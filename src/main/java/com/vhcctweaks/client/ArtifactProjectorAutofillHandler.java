package com.vhcctweaks.client;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import iskallia.vault.block.ArtifactProjectorBlock;
import iskallia.vault.block.VaultArtifactBlock;
import iskallia.vault.block.entity.ArtifactProjectorTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VHCCTweaks.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArtifactProjectorAutofillHandler {
    private static PendingAutofill pending;

    private ArtifactProjectorAutofillHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ModConfig.ARTIFACT_PROJECTOR_AUTOFILL_ENABLED.get()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getEntity() != player || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        Level level = event.getWorld();
        if (!level.isClientSide()) {
            return;
        }

        BlockPos projectorPos = event.getPos();
        BlockState projectorState = level.getBlockState(projectorPos);
        if (!(projectorState.getBlock() instanceof ArtifactProjectorBlock)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(projectorPos);
        if (!(blockEntity instanceof ArtifactProjectorTileEntity projector)) {
            return;
        }

        UUID owner = projector.getOwner();
        if (owner == null || !owner.equals(player.getUUID()) || projector.consuming || projector.completed) {
            return;
        }

        PendingAutofill plan = PendingAutofill.create(player, projectorPos, projectorState, event.getFace());
        if (plan == null) {
            return;
        }

        pending = plan;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            pending = null;
            return;
        }

        if (!pending.isStillValid(minecraft.level, player)) {
            pending.abort(minecraft, player);
            pending = null;
            return;
        }

        pending.tick(minecraft, player);
        if (pending.isFinished()) {
            pending = null;
        }
    }

    private static boolean isArtifactStack(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof VaultArtifactBlock;
    }

    private static boolean isCorrectArtifact(BlockState state, int order) {
        if (!(state.getBlock() instanceof VaultArtifactBlock) || !state.hasProperty(VaultArtifactBlock.ORDER_PROPERTY)) {
            return false;
        }
        return state.getValue(VaultArtifactBlock.ORDER_PROPERTY) == order;
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.getMaterial().isReplaceable();
    }

    private static BlockPos getTargetPos(BlockPos projectorPos, BlockState projectorState, int order) {
        int index = Math.floorMod(49 - order, 25);
        int row = index / 5;
        int column = index % 5;
        Direction facing = projectorState.getValue(HorizontalDirectionalBlock.FACING);
        Direction rowDirection = facing.getClockWise();
        BlockPos origin = projectorPos.below(5).relative(rowDirection.getOpposite(), 2);
        return origin.above(row).relative(rowDirection, column);
    }

    private static int toContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static List<Integer> getScanOrder(int selectedHotbarSlot) {
        List<Integer> order = new ArrayList<>(36);
        order.add(selectedHotbarSlot);
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (hotbarSlot != selectedHotbarSlot) {
                order.add(hotbarSlot);
            }
        }
        for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
            order.add(inventorySlot);
        }
        return order;
    }

    private static final class PendingAutofill {
        private final BlockPos projectorPos;
        private final Direction hitFace;
        private final int selectedHotbarSlot;
        private final List<Integer> sourceInventorySlots;

        private int queueIndex;
        private int activeInventorySlot;
        private boolean directUse;
        private boolean swapApplied;
        private Stage stage;
        private boolean finished;

        private PendingAutofill(BlockPos projectorPos, Direction hitFace, int selectedHotbarSlot, List<Integer> sourceInventorySlots) {
            this.projectorPos = projectorPos.immutable();
            this.hitFace = hitFace == null ? Direction.UP : hitFace;
            this.selectedHotbarSlot = selectedHotbarSlot;
            this.sourceInventorySlots = sourceInventorySlots;
            this.queueIndex = 0;
            this.activeInventorySlot = -1;
            this.directUse = false;
            this.swapApplied = false;
            this.stage = Stage.PREPARE;
            this.finished = false;
        }

        private static PendingAutofill create(LocalPlayer player, BlockPos projectorPos, BlockState projectorState, Direction hitFace) {
            List<Integer> queuedSlots = new ArrayList<>();
            Set<Integer> plannedOrders = new HashSet<>();

            for (int inventorySlot : getScanOrder(player.getInventory().selected)) {
                ItemStack stack = player.getInventory().getItem(inventorySlot);
                if (!isArtifactStack(stack)) {
                    continue;
                }

                int order = VaultArtifactBlock.getArtifactOrder(stack);
                if (!plannedOrders.add(order)) {
                    continue;
                }

                BlockPos targetPos = getTargetPos(projectorPos, projectorState, order);
                BlockState targetState = player.level.getBlockState(targetPos);
                if (isCorrectArtifact(targetState, order) || !canReplace(targetState)) {
                    continue;
                }

                queuedSlots.add(inventorySlot);
            }

            if (queuedSlots.isEmpty()) {
                return null;
            }

            return new PendingAutofill(projectorPos, hitFace, player.getInventory().selected, queuedSlots);
        }

        private boolean isStillValid(Level level, LocalPlayer player) {
            if (player.getInventory().selected != this.selectedHotbarSlot) {
                return false;
            }
            BlockState projectorState = level.getBlockState(this.projectorPos);
            if (!(projectorState.getBlock() instanceof ArtifactProjectorBlock)) {
                return false;
            }
            BlockEntity blockEntity = level.getBlockEntity(this.projectorPos);
            if (!(blockEntity instanceof ArtifactProjectorTileEntity projector)) {
                return false;
            }
            return !projector.consuming && !projector.completed && player.getUUID().equals(projector.getOwner());
        }

        private void abort(Minecraft minecraft, LocalPlayer player) {
            if (this.swapApplied && minecraft.gameMode != null && this.activeInventorySlot >= 0) {
                minecraft.gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId,
                        toContainerSlot(this.activeInventorySlot),
                        this.selectedHotbarSlot,
                        ClickType.SWAP,
                        player
                );
            }
            this.finished = true;
        }

        private void tick(Minecraft minecraft, LocalPlayer player) {
            if (this.queueIndex >= this.sourceInventorySlots.size()) {
                this.finished = true;
                return;
            }

            if (this.stage == Stage.PREPARE) {
                this.activeInventorySlot = this.sourceInventorySlots.get(this.queueIndex);
                this.directUse = this.activeInventorySlot == this.selectedHotbarSlot;
                this.stage = this.directUse ? Stage.USE : Stage.SWAP_IN;
            }

            switch (this.stage) {
                case SWAP_IN -> {
                    minecraft.gameMode.handleInventoryMouseClick(
                            player.inventoryMenu.containerId,
                            toContainerSlot(this.activeInventorySlot),
                            this.selectedHotbarSlot,
                            ClickType.SWAP,
                            player
                    );
                    this.swapApplied = true;
                    this.stage = Stage.USE;
                }
                case USE -> {
                    ItemStack held = player.getMainHandItem();
                    if (!isArtifactStack(held)) {
                        abort(minecraft, player);
                        return;
                    }
                    minecraft.gameMode.useItemOn(
                            player,
                            (ClientLevel) player.level,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(this.projectorPos), this.hitFace, this.projectorPos, false)
                    );
                    if (this.directUse) {
                        advanceQueue();
                    } else {
                        this.stage = Stage.SWAP_BACK;
                    }
                }
                case SWAP_BACK -> {
                    minecraft.gameMode.handleInventoryMouseClick(
                            player.inventoryMenu.containerId,
                            toContainerSlot(this.activeInventorySlot),
                            this.selectedHotbarSlot,
                            ClickType.SWAP,
                            player
                    );
                    this.swapApplied = false;
                    advanceQueue();
                }
                default -> {
                }
            }
        }

        private void advanceQueue() {
            this.queueIndex++;
            this.activeInventorySlot = -1;
            this.directUse = false;
            this.swapApplied = false;
            this.stage = Stage.PREPARE;
            if (this.queueIndex >= this.sourceInventorySlots.size()) {
                this.finished = true;
            }
        }

        private boolean isFinished() {
            return this.finished;
        }
    }

    private enum Stage {
        PREPARE,
        SWAP_IN,
        USE,
        SWAP_BACK
    }
}