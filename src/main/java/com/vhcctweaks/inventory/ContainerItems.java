package com.vhcctweaks.inventory;

import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.util.Safety;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Objects;

/**
 * Container items in a slot of an inventory (2.2.37): a backpack or any item with a Forge item handler, a shulker
 * box, a Vault Hunters barrel. Looking goes through a copy, so it never changes the real item. Changing goes through
 * the real item and then sets the slot again, so the block saves and inventory_changed fires.
 *
 * Why nothing duplicates: Sophisticated Backpacks keeps a backpack's contents in its own saved data, and each
 * handler holds its own copy of them, writing all of it back on every change. Two handlers on one backpack would
 * overwrite each other. So a change only ever goes through the handler of the real item in the slot, the one that
 * anyone else using that backpack at that moment (a player with it open inside a placed backpack) also uses.
 * Everything happens inside one main-thread call, and a move puts the item into the target before taking it out.
 */
final class ContainerItems {

    private ContainerItems() {}

    /** Why a container can't be used. Carries no stack trace: it is an answer, not a failure. */
    static final class Refused extends Exception {
        Refused(String reason) {
            super(reason, null, false, false);
        }
    }

    enum Kind { HANDLER, BLOCK_ITEMS, NEVER_OPENED }

    /** The result of a move out of a container: the count, or 0 and a reason. */
    record Push(int moved, String reason) {
        static Push of(int moved) { return new Push(moved, null); }
        static Push refused(String reason) { return new Push(0, reason); }
    }

    /** A container item found in a slot. */
    static final class Opened {
        final IItemHandler outer;
        final int slot;
        final ItemStack stack;
        final Kind kind;
        final boolean writable;
        final boolean onRealItem;
        final IItemHandler handler;
        final NonNullList<ItemStack> items;
        final int size;
        private final CompoundTag tagBefore;
        private boolean changed;

        private Opened(IItemHandler outer, int slot, ItemStack stack, Kind kind, boolean writable, boolean onRealItem,
                       IItemHandler handler, NonNullList<ItemStack> items, int size, CompoundTag tagBefore) {
            this.outer = outer;
            this.slot = slot;
            this.stack = stack;
            this.kind = kind;
            this.writable = writable;
            this.onRealItem = onRealItem;
            this.handler = handler;
            this.items = items;
            this.size = size;
            this.tagBefore = tagBefore;
        }

        /** The item in an inner slot, counted from 0. Not to be changed. */
        ItemStack peek(int inner) {
            return switch (kind) {
                case HANDLER -> handler.getStackInSlot(inner);
                case BLOCK_ITEMS -> items.get(inner);
                case NEVER_OPENED -> ItemStack.EMPTY;
            };
        }

        /** Whether replace can set an inner slot. */
        boolean canReplace() {
            return writable && (kind != Kind.HANDLER || (onRealItem && handler instanceof IItemHandlerModifiable));
        }

        /**
         * Moves out of an inner slot (from 0) into to, like pushItems. Only on a container opened for change and
         * writable. toSlot is from 0, or -1 for anywhere.
         */
        int moveOut(int inner, IItemHandler to, int toSlot, int limit) {
            return switch (kind) {
                case NEVER_OPENED -> 0;
                case HANDLER -> {
                    int moved = InventoryExtras.moveItem(handler, inner, to, toSlot, limit);
                    if (moved > 0) changed = true;
                    yield moved;
                }
                case BLOCK_ITEMS -> {
                    ItemStack inside = items.get(inner);
                    if (inside.isEmpty()) yield 0;
                    yield ContainerRules.moveOut(inside.getCount(), limit, inside.getMaxStackSize(), n -> {
                        ItemStack offer = ItemHandlerHelper.copyStackWithSize(inside, n);
                        ItemStack rest = toSlot < 0 ? ItemHandlerHelper.insertItem(to, offer, false) : to.insertItem(toSlot, offer, false);
                        return n - rest.getCount();
                    }, n -> {
                        ItemStack left = inside.copy();
                        left.shrink(n);
                        writeBlockItems(inner, left.isEmpty() ? ItemStack.EMPTY : left);
                    });
                }
            };
        }

        /** Puts a new item in an inner slot (from 0). Only when canReplace. */
        void replace(int inner, ItemStack replacement) {
            switch (kind) {
                case HANDLER -> {
                    ((IItemHandlerModifiable) handler).setStackInSlot(inner, replacement);
                    changed = true;
                }
                case BLOCK_ITEMS -> writeBlockItems(inner, replacement);
                case NEVER_OPENED -> { }
            }
        }

        /**
         * Done with it. A container changed through the real item sets the slot again with that same item, so the
         * block is marked changed and keeps whatever the item wrote on itself (a backpack sets itself up the first
         * time its handler is made).
         */
        void close() {
            if (!onRealItem) return;
            if (!changed && Objects.equals(tagBefore, stack.getTag())) return;
            if (outer.getStackInSlot(slot) != stack) {
                Safety.logOnce("container item moved during a call", new IllegalStateException("slot " + slot));
                return;
            }
            ((IItemHandlerModifiable) outer).setStackInSlot(slot, stack);
        }

        /** A shulker box or barrel: its new contents go into a copy, which replaces it in the slot. */
        private void writeBlockItems(int inner, ItemStack replacement) {
            ItemStack now = outer.getStackInSlot(slot);
            if (now.getItem() != stack.getItem() || now.getCount() != 1) {
                Safety.logOnce("container item changed during a call", new IllegalStateException("slot " + slot));
                return;
            }
            ItemStack updated = now.copy();
            NonNullList<ItemStack> contents = readBlockItems(updated, size);
            contents.set(inner, replacement);
            CompoundTag tag = updated.getOrCreateTagElement("BlockEntityTag");
            tag.remove("Items");
            ContainerHelper.saveAllItems(tag, contents, false);
            if (tag.isEmpty()) updated.removeTagKey("BlockEntityTag");
            ((IItemHandlerModifiable) outer).setStackInSlot(slot, updated);
            items.set(inner, replacement);
        }
    }

    /**
     * The container item in slot (from 0). forChange opens the real item's handler when the inventory may be changed;
     * otherwise, and always for looking, a copy's. The caller closes it.
     */
    static Opened open(IItemHandler outer, int slot, boolean forChange) throws Refused {
        ItemStack stack = outer.getStackInSlot(slot);
        if (stack.isEmpty()) throw new Refused(ContainerRules.EMPTY_SLOT);
        if (!ModConfig.INVENTORY_CONTAINER_ITEMS.get()) throw new Refused(ContainerRules.CONTAINERS_OFF);
        if (stack.getCount() != 1) throw new Refused(ContainerRules.NOT_A_CONTAINER);

        boolean writable = ContainerRules.writable(outer instanceof IItemHandlerModifiable, outer.getStackInSlot(slot) == stack);

        if (Backpacks.neverOpened(stack)) {
            return new Opened(outer, slot, stack, Kind.NEVER_OPENED, writable, false, null, null, Backpacks.slots(stack), null);
        }

        boolean onRealItem = forChange && writable;
        ItemStack source = onRealItem ? stack : stack.copy();
        // Taken before the handler is made: making it may set the backpack up
        CompoundTag tagBefore = onRealItem && stack.getTag() != null ? stack.getTag().copy() : null;
        IItemHandler handler = source.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).resolve().orElse(null);
        if (handler != null) {
            return new Opened(outer, slot, stack, Kind.HANDLER, writable, onRealItem, handler, null, handler.getSlots(), tagBefore);
        }

        int size = blockItemSlots(stack);
        if (size > 0) {
            return new Opened(outer, slot, stack, Kind.BLOCK_ITEMS, writable, false, null, readBlockItems(stack, size), size, null);
        }
        throw new Refused(ContainerRules.NOT_A_CONTAINER);
    }

    /**
     * pushContents and an inner moveMany move, after the target and slot checks. inner is counted from 1, toSlot from
     * 0 (-1 for anywhere). Reasons in the spec's order.
     */
    static Push push(IItemHandler from, int slot, int inner, String expectName, IItemHandler to, int toSlot, int limit) {
        Opened container;
        try {
            container = open(from, slot, true);
        } catch (Refused refused) {
            return Push.refused(refused.getMessage());
        }
        try {
            String range = ContainerRules.innerOutOfRange(inner, container.size);
            if (range != null) return Push.refused(range);
            if (!container.writable) return Push.refused(ContainerRules.CANT_CHANGE);
            ItemStack inside = container.peek(inner - 1);
            if (inside.isEmpty()) return Push.of(0);
            if (expectName != null && !expectName.equals(nameOf(inside))) return Push.of(0);
            return Push.of(container.moveOut(inner - 1, to, toSlot, limit));
        } finally {
            container.close();
        }
    }

    static String nameOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = stack.getItem().getRegistryName();
        return id == null ? null : id.toString();
    }

    /** How many slots a shulker-style item keeps in BlockEntityTag.Items, or 0 when it is not one. */
    private static int blockItemSlots(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return 0;
        Block block = blockItem.getBlock();
        if (block instanceof ShulkerBoxBlock) return ContainerRules.SHULKER_SLOTS;
        try {
            if (block instanceof iskallia.vault.block.VaultBarrelBlock) {
                return block == iskallia.vault.init.ModBlocks.WOODEN_BARREL ? ContainerRules.WOODEN_BARREL_SLOTS : ContainerRules.VAULT_BARREL_SLOTS;
            }
        } catch (LinkageError e) {
            // A Vault Hunters without barrels
        }
        return 0;
    }

    /** The contents of a shulker-style item, as new stacks. */
    private static NonNullList<ItemStack> readBlockItems(ItemStack stack, int size) {
        NonNullList<ItemStack> contents = NonNullList.withSize(size, ItemStack.EMPTY);
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        if (tag != null) ContainerHelper.loadAllItems(tag, contents);
        return contents;
    }

    /** Sophisticated Backpacks, by reflection: the mod is not needed to build or run. */
    static final class Backpacks {
        private static final Class<?> ITEM = classOrNull("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem");

        private Backpacks() {}

        /**
         * A backpack nobody has opened has no contents yet. Making its handler would give it storage, which a copy
         * would give to nothing; so it is left alone and counts as empty.
         */
        static boolean neverOpened(ItemStack stack) {
            if (ITEM == null || !ITEM.isInstance(stack.getItem())) return false;
            CompoundTag tag = stack.getTag();
            return tag == null || !tag.contains("contentsUuid");
        }

        /** Its slot count: what it was set up with, else what its tier gives. */
        static int slots(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("inventorySlots")) return tag.getInt("inventorySlots");
            try {
                Object slots = stack.getItem().getClass().getMethod("getNumberOfSlots").invoke(stack.getItem());
                return slots instanceof Integer n ? n : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Safety.logOnce("backpack slot count", e);
                return 0;
            }
        }

        private static Class<?> classOrNull(String name) {
            try {
                return Class.forName(name, false, Backpacks.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }
    }
}
