package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.systems.modules.Module;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super(B2XY.CATEGORY, "no-jump-delay", "Убирает задержку между прыжками.");
    }
}