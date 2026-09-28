package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/*
    Портируемо из: Trouser-Streak (AutoWither), оригинал Germanminer/MeteorServerUtils
*/
public class AutoWither extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgVisuals = this.settings.createGroup("Визуал");
    private final SettingGroup sgColors = this.settings.createGroup("Цвета");

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Разрешает размещение в воздухе.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> placeCenterSkull = sgGeneral.add(new BoolSetting.Builder()
        .name("center-skull")
        .description("Размещает череп в центре визера.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> lockRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-rotation")
        .description("Фиксирует ориентацию визера.")
        .defaultValue(false)
        .build()
    );
    private final Setting<CardinalDirection> chosenDirection = sgGeneral.add(new EnumSetting.Builder<CardinalDirection>()
        .name("direction")
        .description("Направление, к которому привязывается строительство (North означает, что руки визера будут направлены запад-восток).")
        .defaultValue(CardinalDirection.North)
        .visible(() -> lockRotation.get())
        .build()
    );
    private final Setting<Boolean> renderPreview = sgVisuals.add(new BoolSetting.Builder()
        .name("preview")
        .description("Отрисовывать ли превью блоков.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> swingHand = sgVisuals.add(new BoolSetting.Builder()
        .name("swing")
        .description("Взмах рукой при установке.")
        .defaultValue(true)
        .build()
    );
    private final Setting<SettingColor> previewColor = sgColors.add(new ColorSetting.Builder()
        .name("preview-color")
        .description("Цвет заливки превью.")
        .defaultValue(new SettingColor(51, 207, 255, 50, false))
        .build()
    );
    private final Setting<SettingColor> previewOutlineColor = sgColors.add(new ColorSetting.Builder()
        .name("preview-outline-color")
        .description("Цвет контура превью.")
        .defaultValue(new SettingColor(112, 136, 255, 255, false))
        .build()
    );

    private BlockPos previewPos;
    private Boolean isBuilding = false;
    private Boolean hasMaterials = false;

    public AutoWither() {
        super(B2XY.CATEGORY, "auto-wither", "Автоматически строит визера.");
    }

    @Override
    public void onActivate() {
        info("Нажмите ПКМ, чтобы построить визера");
        int soulSandCount = 0;
        int skullCount = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.SOUL_SAND) {
                soulSandCount += stack.getCount();
            } else if (stack.getItem() == Items.WITHER_SKELETON_SKULL) {
                skullCount += stack.getCount();
            }
        }

        int witherCount = Math.min(soulSandCount / 4, skullCount / 3);
        if (witherCount > 0) {
            info("Вы можете построить " + witherCount + " визера(ов)");
            hasMaterials = true;
        } else {
            info("У вас недостаточно материалов для постройки визера в хотбаре");
            hasMaterials = false;
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (mc.currentScreen != null) return;
        if (event.button != 1) return;
        if (isBuilding) return;
        if (event.action == KeyAction.Press) {
            event.cancel();
            if (!hasMaterials) {
                info("У вас недостаточно материалов для постройки визера в хотбаре");
                return;
            }
        } else {
            return;
        }
        if (previewPos != null) {
            isBuilding = true;
            placeWither(previewPos, () -> isBuilding = false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        hasMaterials = hasWitherMaterials();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (renderPreview.get() == false || !hasMaterials) return;
        if (previewPos != null) {
            Direction direction;
            if (lockRotation.get()) {
                direction = chosenDirection.get().toMcDirection();
            } else {
                direction = mc.player.getHorizontalFacing();
            }
            event.renderer.box(previewPos, previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            event.renderer.box(previewPos.up(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
            if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                event.renderer.box(previewPos.up().west(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
                event.renderer.box(previewPos.up().east(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
                renderSkull(event, previewPos.up(2).west());
                renderSkull(event, previewPos.up(2).east());
            } else {
                event.renderer.box(previewPos.up().south(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
                event.renderer.box(previewPos.up().north(), previewColor.get(), previewOutlineColor.get(), ShapeMode.Both, 0);
                renderSkull(event, previewPos.up(2).south());
                renderSkull(event, previewPos.up(2).north());
            }
            if (placeCenterSkull.get()) {
                renderSkull(event, previewPos.up(2));
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!renderPreview.get()) return;
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (airPlace.get()) {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = pos;
                }
            } else {
                if (state.isSolidBlock(mc.world, pos)) {
                    previewPos = pos.up();
                } else {
                    previewPos = null;
                }
            }
        } else {
            previewPos = null;
        }
    }

    private void placeWither(BlockPos basePos, Runnable onComplete) {
        if (!checkWitherBlocks(basePos)) {
            info("Препятствие помешало постройке визера");
            onComplete.run();
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        Direction direction;
        if (lockRotation.get()) {
            direction = chosenDirection.get().toMcDirection();
        } else {
            direction = mc.player.getHorizontalFacing();
        }
        List<BlockPos> blockPositions = new ArrayList<>();
        List<BlockPos> skullPositions = new ArrayList<>();
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            blockPositions.add(basePos.up().west());
            blockPositions.add(basePos.up().east());
            skullPositions.add(basePos.up().west());
            skullPositions.add(basePos.up().east());
        } else {
            blockPositions.add(basePos.up().south());
            blockPositions.add(basePos.up().north());
            skullPositions.add(basePos.up().south());
            skullPositions.add(basePos.up().north());
        }
        blockPositions.add(basePos);
        blockPositions.add(basePos.up());
        if (placeCenterSkull.get()) {
            skullPositions.add(basePos.up());
        }

        mc.execute(() -> {
            for (BlockPos pos : blockPositions) {
                placeSoulBlock(pos);
            }
            for (BlockPos pos : skullPositions) {
                placeBlock(pos, Items.WITHER_SKELETON_SKULL);
            }
            mc.player.getInventory().setSelectedSlot(originalSlot);
            onComplete.run();
        });
    }

    private boolean checkWitherBlocks(BlockPos basePos) {
        List<BlockPos> blockPositions = new ArrayList<>();
        Direction direction;
        if (lockRotation.get()) {
            direction = chosenDirection.get().toMcDirection();
        } else {
            direction = mc.player.getHorizontalFacing();
        }
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            blockPositions.add(basePos.up().west());
            blockPositions.add(basePos.up().east());
            blockPositions.add(basePos.up(2).west());
            blockPositions.add(basePos.up(2).east());
            blockPositions.add(basePos.west());
            blockPositions.add(basePos.east());
        } else {
            blockPositions.add(basePos.up().south());
            blockPositions.add(basePos.up().north());
            blockPositions.add(basePos.up(2).south());
            blockPositions.add(basePos.up(2).north());
            blockPositions.add(basePos.south());
            blockPositions.add(basePos.north());
        }
        blockPositions.add(basePos);
        blockPositions.add(basePos.up());
        if (placeCenterSkull.get()) {
            blockPositions.add(basePos.up(2));
        }
        for (BlockPos pos : blockPositions) {
            if (!checkBlockPlaceable(pos)) {
                return false;
            }
        }
        blockPositions.remove(4);
        blockPositions.remove(5);
        for (BlockPos pos : blockPositions) {
            if (!checkBlockForEntity(pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkBlockPlaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    private boolean checkBlockForEntity(BlockPos pos) {
        List<Entity> entities = mc.world.getOtherEntities(
            mc.player,
            new Box(pos),
            e -> !(e instanceof ItemEntity || e instanceof ExperienceOrbEntity)
        );
        return entities.isEmpty();
    }

    private void placeSoulBlock(BlockPos pos) {
        Item soulBlock = hasSoulSandHotbar() ? Items.SOUL_SAND : Items.SOUL_SOIL;
        placeBlock(pos, soulBlock);
    }

    private void placeBlock(BlockPos pos, Item item) {
        int slot = findHotbarSlot(item);
        if (slot == -1) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        if (!swingHand.get()) return;
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private boolean hasWitherMaterials() {
        int soulBlockCount = 0;
        int skullCount = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            if (item == Items.SOUL_SAND || item == Items.SOUL_SOIL) {
                soulBlockCount += stack.getCount();
            } else if (item == Items.WITHER_SKELETON_SKULL) {
                skullCount += stack.getCount();
            }

            if (mc.player.isCreative()) {
                if (soulBlockCount >= 1 && skullCount >= 1) return true;
            } else {
                if (soulBlockCount >= 4 && skullCount >= 3) return true;
            }
        }

        return false;
    }

    private boolean hasSoulSandHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.SOUL_SAND) return true;
        }
        return false;
    }

    private void renderSkull(Render3DEvent event, BlockPos pos) {
        double shrink = 0.23;
        event.renderer.box(
            pos.getX() + shrink,
            pos.getY(),
            pos.getZ() + shrink,
            pos.getX() + 1 - shrink,
            pos.getY() + 0.5,
            pos.getZ() + 1 - shrink,
            previewColor.get(),
            previewOutlineColor.get(),
            ShapeMode.Both,
            0
        );
    }

    private enum CardinalDirection {
        North(Direction.NORTH),
        South(Direction.SOUTH),
        East(Direction.EAST),
        West(Direction.WEST);

        private final Direction mcDirection;

        CardinalDirection(Direction mcDirection) {
            this.mcDirection = mcDirection;
        }

        public Direction toMcDirection() {
            return mcDirection;
        }
    }
}