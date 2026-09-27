package com.b2xy.util;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Grim-утилиты (порт из BepHax NEW-SRC).
 *
 * ВАЖНО (1.21.11): BlockStatePredictionHandler удалён из клиента, поэтому
 * nextSequence() возвращает собственный монотонно растущий счётчик — семантика
 * "следующий номер последовательности" сохранена, сервер игнорирует его для ACK.
 */
public final class GrimUtils {
    private static int sequence = 0;

    private GrimUtils() {
    }

    public static double[] getPossibleEyeHeights(PlayerEntity player) {
        float scale = (float) player.getAttributeValue(EntityAttributes.SCALE);
        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        double swimming = 0.4 * scale;

        return switch (player.getPose()) {
            case GLIDING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, sneaking};
            case CROUCHING -> new double[]{sneaking, standing, swimming};
            default -> new double[]{standing, sneaking, swimming};
        };
    }

    public static double closestEyeDistanceSqTo(PlayerEntity player, Vec3d target) {
        Vec3d base = new Vec3d(player.getX(), player.getY(), player.getZ());
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            double d = base.add(0.0, h, 0.0).squaredDistanceTo(target);
            if (d < best) {
                best = d;
            }
        }

        return best;
    }

    public static double closestEyeDistanceSqTo(PlayerEntity player, Box box) {
        Vec3d base = new Vec3d(player.getX(), player.getY(), player.getZ());
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            Vec3d eye = base.add(0.0, h, 0.0);
            double cx = clamp(eye.getX(), box.minX, box.maxX);
            double cy = clamp(eye.getY(), box.minY, box.maxY);
            double cz = clamp(eye.getZ(), box.minZ, box.maxZ);
            double d = eye.squaredDistanceTo(cx, cy, cz);
            if (d < best) {
                best = d;
            }
        }

        return best;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int nextSequence() {
        int seq = sequence;
        sequence++;
        return seq;
    }
}