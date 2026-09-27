package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.fluid.FluidState;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/*
    Портируемо из: BleachHack (NewChunks), далее meteor-rejects
*/
public class NewChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Рендер");

    private final Setting<Boolean> remove = sgGeneral.add(new BoolSetting.Builder()
        .name("remove")
        .description("Удаляет закешированные чанки при выключении модуля.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
        .name("render-height")
        .description("Высота, на которой будут отрисовываться новые чанки.")
        .defaultValue(0)
        .min(-64)
        .sliderRange(-64, 319)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Как отрисовываются фигуры.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> newChunksSideColor = sgRender.add(new ColorSetting.Builder()
        .name("new-chunks-side-color")
        .description("Цвет чанков, которые (скорее всего) полностью новые.")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> oldChunksSideColor = sgRender.add(new ColorSetting.Builder()
        .name("old-chunks-side-color")
        .description("Цвет чанков, которые (скорее всего) уже загружались раньше.")
        .defaultValue(new SettingColor(0, 255, 0, 75))
        .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> newChunksLineColor = sgRender.add(new ColorSetting.Builder()
        .name("new-chunks-line-color")
        .description("Цвет линий чанков, которые (скорее всего) полностью новые.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> oldChunksLineColor = sgRender.add(new ColorSetting.Builder()
        .name("old-chunks-line-color")
        .description("Цвет линий чанков, которые (скорее всего) уже загружались раньше.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
        .build()
    );

    private final Set<ChunkPos> newChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<ChunkPos> oldChunks = Collections.synchronizedSet(new HashSet<>());
    private static final Direction[] searchDirs = new Direction[] { Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP };
    private final Executor taskExecutor = Executors.newSingleThreadExecutor();

    public NewChunks() {
        super(B2XY.CATEGORY, "new-chunks", "Определяет полностью новые чанки по их характерным признакам");
    }

    @Override
    public void onDeactivate() {
        if (remove.get()) {
            newChunks.clear();
            oldChunks.clear();
        }
        super.onDeactivate();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (newChunksLineColor.get().a > 5 || newChunksSideColor.get().a > 5) {
            synchronized (newChunks) {
                for (ChunkPos c : newChunks) {
                    if (mc.getCameraEntity() != null && mc.getCameraEntity().getBlockPos().isWithinDistance(c.getStartPos(), 1024)) {
                        render(new Box(Vec3d.of(c.getStartPos()), Vec3d.of(c.getStartPos().add(16, renderHeight.get(), 16))), newChunksSideColor.get(), newChunksLineColor.get(), shapeMode.get(), event);
                    }
                }
            }
        }

        if (oldChunksLineColor.get().a > 5 || oldChunksSideColor.get().a > 5) {
            synchronized (oldChunks) {
                for (ChunkPos c : oldChunks) {
                    if (mc.getCameraEntity() != null && mc.getCameraEntity().getBlockPos().isWithinDistance(c.getStartPos(), 1024)) {
                        render(new Box(Vec3d.of(c.getStartPos()), Vec3d.of(c.getStartPos().add(16, renderHeight.get(), 16))), oldChunksSideColor.get(), oldChunksLineColor.get(), shapeMode.get(), event);
                    }
                }
            }
        }
    }

    private void render(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sides, lines, shapeMode, 0);
    }

    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) -> {
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty() && !fluid.isStill()) {
                    ChunkPos chunkPos = new ChunkPos(pos);
                    for (Direction dir : searchDirs) {
                        if (hasSourceFluidNextTo(pos.offset(dir)) && !oldChunks.contains(chunkPos)) {
                            newChunks.add(chunkPos);
                            return;
                        }
                    }
                }
            });
        } else if (event.packet instanceof BlockUpdateS2CPacket packet) {
            BlockState state = packet.getState();
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty() && !fluid.isStill()) {
                ChunkPos chunkPos = new ChunkPos(packet.getPos());
                for (Direction dir : searchDirs) {
                    if (hasSourceFluidNextTo(packet.getPos().offset(dir)) && !oldChunks.contains(chunkPos)) {
                        newChunks.add(chunkPos);
                        return;
                    }
                }
            }
        } else if (event.packet instanceof ChunkDataS2CPacket packet && mc.world != null) {
            ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

            if (!newChunks.contains(pos) && !mc.world.isChunkLoaded(pos.x, pos.z)) {
                WorldChunk chunk = new WorldChunk(mc.world, pos);
                try {
                    ChunkData data = packet.getChunkData();
                    taskExecutor.execute(() -> chunk.loadFromPacket(data.getSectionsDataBuf(), data.getHeightmap(), data.getBlockEntities(packet.getChunkX(), packet.getChunkZ())));
                } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                    return;
                }

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = mc.world.getBottomY(); y < mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z); y++) {
                            FluidState fluid = chunk.getFluidState(x, y, z);
                            if (!fluid.isEmpty() && !fluid.isStill()) {
                                oldChunks.add(pos);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasSourceFluidNextTo(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).getFluidState().isStill();
    }
}