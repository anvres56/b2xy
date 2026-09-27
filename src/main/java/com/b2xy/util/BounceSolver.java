package com.b2xy.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public final class BounceSolver {
    private static final double GRAVITY = 0.08;
    private static final float JUMP_POWER = 0.42f;
    public static final double GLIDE_HEIGHT = 0.6;
    private static final int GLIDE_PERSIST_TICKS = 3;
    private static final int SIM_TICKS = 600;
    private static final float MIN_PITCH = 25.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final double MAX_USEFUL_HEADROOM = 2.0;

    private BounceSolver() {
    }

    public static double measureHeadroom(PlayerEntity player, World world) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(player.getX() - 0.3, player.getY(), player.getZ() - 0.3, player.getX() + 0.3, player.getY() + 0.6, player.getZ() + 0.3);
        double free = 0.0;
        for (double rise = 0.05; rise <= 2.0 && world.isBlockSpaceEmpty(null, box.offset(0.0, rise, 0.0)); rise += 0.05) {
            free = rise;
        }
        return free;
    }

    public static double simulateSpeed(float pitch, double headroom) {
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        double y = 0.0;
        boolean onGround = true;
        boolean gliding = false;
        boolean prevJump = false;
        int noJumpDelay = 0;
        int sinceGround = Integer.MAX_VALUE;
        for (int t = 0; t < 600; ++t) {
            boolean jump;
            if (noJumpDelay > 0) {
                --noJumpDelay;
            }
            boolean bl = jump = onGround || !gliding && !prevJump;
            if (jump && !prevJump && !onGround && !gliding) {
                gliding = true;
            }
            prevJump = jump;
            if (jump) {
                if (onGround && noJumpDelay == 0) {
                    vy = Math.max((double)0.42f, vy);
                    vz += 0.2;
                    noJumpDelay = 10;
                }
            } else {
                noJumpDelay = 0;
            }
            if (gliding) {
                double f = pitch * ((float)Math.PI / 180);
                double lookZ = Math.sin((double)f);
                double d = Math.abs(lookZ);
                double e = Math.sqrt(vx * vx + vz * vz);
                double h = Math.cos((double)f);
                if ((vy += 0.08 * (-1.0 + h * 0.75)) < 0.0 && d > 0.0) {
                    double i = vy * -0.1 * h;
                    vz += lookZ * i / d;
                    vy += i;
                }
                if (d > 0.0) {
                    vz += (lookZ / d * e - vz) * 0.1;
                }
                vx *= (double)0.99f;
                vy *= (double)0.98f;
                vz *= (double)0.99f;
            } else {
                double fric = onGround ? 0.546 : 0.91;
                vx *= fric;
                vz *= fric;
                vy = (vy - 0.08) * 0.98;
            }
            double ceilingH = headroom + 0.6;
            double hitbox = gliding || ceilingH < 1.8 ? 0.6 : 1.8;
            double free = headroom >= 2.0 ? Double.MAX_VALUE : ceilingH - hitbox;
            double ny = y + vy;
            onGround = false;
            if (ny <= 0.0) {
                ny = 0.0;
                vy = 0.0;
                onGround = true;
            } else if (ny >= free) {
                ny = free;
                vy = 0.0;
            }
            y = ny;
            if (onGround) {
                sinceGround = 0;
            } else if (sinceGround != Integer.MAX_VALUE) {
                ++sinceGround;
            }
            if (!gliding || sinceGround < 3 || sinceGround == Integer.MAX_VALUE) continue;
            gliding = false;
            sinceGround = Integer.MAX_VALUE;
        }
        return Math.sqrt(vx * vx + vz * vz) * 20.0;
    }

    public static float solvePitch(double headroom) {
        float best = 89.0f;
        double bestSpeed = -1.0;
        for (float p = 25.0f; p <= 89.0f; p += 1.0f) {
            double s = BounceSolver.simulateSpeed(p, headroom);
            if (!(s > bestSpeed)) continue;
            bestSpeed = s;
            best = p;
        }
        return best;
    }
}
