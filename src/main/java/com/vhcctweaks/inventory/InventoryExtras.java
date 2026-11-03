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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            return moveItem(from, move.fromSlot() - 1, to, move.toSlot() == null ? -1 : move.toSlot() - 1, limit);
        }).toLua();
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

    private static int moveItem(IItemHandler from, int fromSlot, IItemHandler to, int toSlot, final int limit) {
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
