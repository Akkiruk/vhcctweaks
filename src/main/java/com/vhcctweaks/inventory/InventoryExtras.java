package com.vhcctweaks.inventory;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.util.Safety;
import dan200.computercraft.api.detail.DetailRegistries;
import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.vhcctweaks.ccvault.SessionAuthManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Batch inventory methods and change events, on every inventory peripheral through a CC generic source:
 * listDetailed, moveMany, watch and unwatch. Remote inventories get them too, because a linked peripheral is
 * the same peripheral object.
 *
 * Moving items copies CC 1.101.3's InventoryMethods.pushItems exactly, so each move is a pushItems.
 */
public final class InventoryExtras implements GenericSource {

    @Nonnull
    @Override
    public ResourceLocation id() {
        return new ResourceLocation(VHCCTweaks.MOD_ID, "inventory_extras");
    }

    /** Every non-empty slot's getItemDetail, in one main-thread call. */
    @LuaFunction(mainThread = true)
    public static Object[] listDetailed(IItemHandler inventory) throws LuaException {
        return Safety.lua("inventory.listDetailed", () -> {
            Map<Integer, Map<String, ?>> result = new HashMap<>();
            int size = inventory.getSlots();
            for (int i = 0; i < size; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) result.put(i + 1, DetailRegistries.ITEM_STACK.getDetails(stack));
            }
            return new Object[]{result};
        });
    }

    /**
     * Many pushItems in one main-thread call: { { fromSlot, to, toSlot?, limit? }, ... }, run in order.
     * A move that raises (a bad target or slot) ends the batch with pushItems' own error; moves before it stay done.
     * Stops early once the batch has used batchTimeBudgetMicros; done says how many moves were attempted.
     */
    @LuaFunction(mainThread = true)
    public static Object[] moveMany(IItemHandler from, IComputerAccess computer, Map<?, ?> moves) throws LuaException {
        return Safety.lua("inventory.moveMany", () -> new Object[]{runMoves(from, computer, moves)});
    }

    private static Map<String, Object> runMoves(IItemHandler from, IComputerAccess computer, Map<?, ?> moves) throws LuaException {
        long start = System.nanoTime();
        List<MoveBatch.Move> batch = MoveBatch.parse(moves, ModConfig.INVENTORY_MAX_BATCH_MOVES.get());

        // Each name is looked up once per batch, the first time a move uses it
        Map<String, IItemHandler> byName = new HashMap<>();
        long budget = ModConfig.INVENTORY_BATCH_TIME_BUDGET_MICROS.get() * 1000L;
        return MoveBatch.run(batch.size(), start, budget, System::nanoTime, i -> {
            MoveBatch.Move move = batch.get(i);
            IItemHandler to = byName.get(move.to());
            if (to == null) {
                to = resolveTarget(computer, move.to());
                byName.put(move.to(), to);
            }
            int limit = move.limit() == null ? Integer.MAX_VALUE : move.limit();
            assertBetween(move.fromSlot(), 1, from.getSlots(), "From slot out of range (%s)");
            if (move.toSlot() != null) assertBetween(move.toSlot(), 1, to.getSlots(), "To slot out of range (%s)");
            if (limit <= 0) return 0;
            if (move.inner() != null) {
                // Whatever would refuse a pushContents moves 0 here and the batch goes on
                return ContainerItems.push(from, move.fromSlot() - 1, move.inner(), move.expectName(), to,
                        move.toSlot() == null ? -1 : move.toSlot() - 1, limit).moved();
            }
            if (move.expectName() != null && !move.accepts(nameOf(from.getStackInSlot(move.fromSlot() - 1)))) return 0;
            return moveItem(from, move.fromSlot() - 1, to, move.toSlot() == null ? -1 : move.toSlot() - 1, limit);
        }).toLua();
    }

    // ===== Container items and identify (2.2.37) =====

    /**
     * What is inside the container item in slot: { [innerSlot] = list()-style item }, or getItemDetail-style with
     * detailed, and the container's slot count. Looks through a copy, so it changes nothing.
     */
    @LuaFunction(mainThread = true)
    public static Object[] listContents(IItemHandler inventory, int slot, Optional<Boolean> detailed) throws LuaException {
        return Safety.lua("inventory.listContents", () -> {
            assertBetween(slot, 1, inventory.getSlots(), "Slot out of range (%s)");
            ContainerItems.Opened container;
            try {
                container = ContainerItems.open(inventory, slot - 1, false);
            } catch (ContainerItems.Refused refused) {
                return new Object[]{null, refused.getMessage()};
            }
            boolean full = detailed.orElse(false);
            Map<Integer, Map<String, ?>> contents = new HashMap<>();
            for (int i = 0; i < container.size; i++) {
                ItemStack stack = container.peek(i);
                if (stack.isEmpty()) continue;
                contents.put(i + 1, full ? DetailRegistries.ITEM_STACK.getDetails(stack) : DetailRegistries.ITEM_STACK.getBasicDetails(stack));
            }
            return new Object[]{contents, container.size};
        });
    }

    /** pushItems for the item in innerSlot inside the container item in fromSlot. Returns the count, or 0 and why. */
    @LuaFunction(mainThread = true)
    public static Object[] pushContents(IItemHandler from, IComputerAccess computer, String toName, int fromSlot, int innerSlot,
                                        Optional<Integer> limit, Optional<Integer> toSlot) throws LuaException {
        return Safety.lua("inventory.pushContents", () -> {
            // The same checks, in the same order, as pushItems
            IItemHandler to = resolveTarget(computer, toName);
            int actualLimit = limit.orElse(Integer.MAX_VALUE);
            assertBetween(fromSlot, 1, from.getSlots(), "From slot out of range (%s)");
            if (toSlot.isPresent()) assertBetween(toSlot.get(), 1, to.getSlots(), "To slot out of range (%s)");
            if (actualLimit <= 0) return new Object[]{0};
            ContainerItems.Push push = ContainerItems.push(from, fromSlot - 1, innerSlot, null, to,
                    toSlot.map(s -> s - 1).orElse(-1), actualLimit);
            return push.reason() == null ? new Object[]{push.moved()} : new Object[]{0, push.reason()};
        });
    }

    /**
     * Identifies the Vault Hunters item in slot, or in innerSlot of the container item there, as the Identification
     * Stand would for player, who must be on this server and have approved this computer. Returns its getItemDetail.
     */
    @LuaFunction(mainThread = true)
    public static Object[] identify(IItemHandler inventory, IComputerAccess computer, int slot, String player,
                                    Optional<Integer> innerSlot) throws LuaException {
        return Safety.lua("inventory.identify", () -> {
            if (!ModConfig.INVENTORY_IDENTIFY.get()) return new Object[]{null, ContainerRules.IDENTIFY_OFF};
            ServerPlayer who = Safety.here(ServerLifecycleHooks.getCurrentServer(), player);
            if (who == null) return new Object[]{null, ContainerRules.notOnline(player)};
            if (!SessionAuthManager.isAuthenticated(who.getUUID(), computer.getID())) {
                return new Object[]{null, ContainerRules.notApproved(player)};
            }
            SessionAuthManager.touchSession(who.getUUID(), computer.getID());

            assertBetween(slot, 1, inventory.getSlots(), "Slot out of range (%s)");
            if (inventory.getStackInSlot(slot - 1).isEmpty()) return new Object[]{null, ContainerRules.EMPTY_SLOT};
            try {
                return innerSlot.isPresent()
                        ? identifyInner(inventory, slot - 1, innerSlot.get(), who)
                        : identifyAt(inventory, slot - 1, who);
            } catch (LinkageError e) {
                // A Vault Hunters without identification
                Safety.logOnce("identify classes", e);
                return new Object[]{null, ContainerRules.IDENTIFY_NOT_AVAILABLE};
            }
        });
    }

    private static Object[] identifyAt(IItemHandler inventory, int slot, ServerPlayer player) {
        ItemStack stack = inventory.getStackInSlot(slot);
        ItemStack piece = stack.copy();
        String why = Identify.check(piece, player);
        if (why != null) return new Object[]{null, why};
        boolean writable = ContainerRules.writable(inventory instanceof IItemHandlerModifiable, inventory.getStackInSlot(slot) == stack);
        if (!writable) return new Object[]{null, ContainerRules.CANT_CHANGE};
        if (!Identify.run(piece, player)) return new Object[]{null, ContainerRules.IDENTIFY_NOT_AVAILABLE};
        ((IItemHandlerModifiable) inventory).setStackInSlot(slot, piece);
        return new Object[]{DetailRegistries.ITEM_STACK.getDetails(piece)};
    }

    private static Object[] identifyInner(IItemHandler inventory, int slot, int inner, ServerPlayer player) {
        ContainerItems.Opened container;
        try {
            container = ContainerItems.open(inventory, slot, true);
        } catch (ContainerItems.Refused refused) {
            return new Object[]{null, refused.getMessage()};
        }
        try {
            String range = ContainerRules.innerOutOfRange(inner, container.size);
            if (range != null) return new Object[]{null, range};
            ItemStack inside = container.peek(inner - 1);
            if (inside.isEmpty()) return new Object[]{null, ContainerRules.EMPTY_SLOT};
            ItemStack piece = inside.copy();
            String why = Identify.check(piece, player);
            if (why != null) return new Object[]{null, why};
            if (!container.canReplace()) return new Object[]{null, ContainerRules.CANT_CHANGE};
            if (!Identify.run(piece, player)) return new Object[]{null, ContainerRules.IDENTIFY_NOT_AVAILABLE};
            container.replace(inner - 1, piece);
            return new Object[]{DetailRegistries.ITEM_STACK.getDetails(piece)};
        } finally {
            container.close();
        }
    }

    /** The registry name getItemDetail reports as name, or null for an empty slot. */
    private static String nameOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = stack.getItem().getRegistryName();
        return id == null ? null : id.toString();
    }

    /** Send this computer inventory_changed for this inventory, under the name it uses for it. */
    @LuaFunction(mainThread = true)
    public static Object[] watch(IItemHandler inventory, IComputerAccess computer) throws LuaException {
        return Safety.lua("inventory.watch", () -> {
            InventoryWatch.watch(blockOf(computer), computer, computer.getID(),
                    ModConfig.INVENTORY_MAX_WATCHES_PER_COMPUTER.get(), InventoryWatch::isGoneBlock);
            return new Object[0];
        });
    }

    @LuaFunction(mainThread = true)
    public static Object[] unwatch(IItemHandler inventory, IComputerAccess computer) throws LuaException {
        return Safety.lua("inventory.unwatch", () -> {
            InventoryWatch.unwatch(blockOf(computer), computer);
            return new Object[0];
        });
    }

    /** The block behind the peripheral being called: each computer access can find its own peripheral by its own name. */
    private static Object blockOf(IComputerAccess computer) throws LuaException {
        IPeripheral self = computer.getAvailablePeripheral(computer.getAttachmentName());
        Object target = self == null ? null : self.getTarget();
        if (!(target instanceof BlockEntity)) throw new LuaException("this inventory cannot be watched");
        return target;
    }

    // ===== Copied from CC 1.101.3 InventoryMethods, which keeps them private =====

    private static IItemHandler resolveTarget(IComputerAccess computer, String toName) throws LuaException {
        IPeripheral location = computer.getAvailablePeripheral(toName);
        if (location == null) throw new LuaException("Target '" + toName + "' does not exist");
        IItemHandler to = extractHandler(location.getTarget());
        if (to == null) throw new LuaException("Target '" + toName + "' is not an inventory");
        return to;
    }

    private static void assertBetween(int value, int min, int max, String message) throws LuaException {
        if (value < min || value > max) {
            throw new LuaException(String.format(message, "between " + min + " and " + max));
        }
    }

    @Nullable
    private static IItemHandler extractHandler(@Nullable Object object) {
        if (object instanceof BlockEntity blockEntity && blockEntity.isRemoved()) return null;

        if (object instanceof ICapabilityProvider provider) {
            LazyOptional<IItemHandler> cap = provider.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (cap.isPresent()) return cap.orElseThrow(NullPointerException::new);
        }

        if (object instanceof IItemHandler handler) return handler;
        if (object instanceof Container container) return new InvWrapper(container);
        return null;
    }

    static int moveItem(IItemHandler from, int fromSlot, IItemHandler to, int toSlot, final int limit) {
        ItemStack extracted = from.extractItem(fromSlot, limit, true);
        if (extracted.isEmpty()) return 0;

        int extractCount = Math.min(extracted.getCount(), limit);
        extracted.setCount(extractCount);

        ItemStack remainder = toSlot < 0 ? ItemHandlerHelper.insertItem(to, extracted, false) : to.insertItem(toSlot, extracted, false);
        int inserted = remainder.isEmpty() ? extractCount : extractCount - remainder.getCount();
        if (inserted <= 0) return 0;

        from.extractItem(fromSlot, inserted, false);
        return inserted;
    }
}
