package com.b2xy.accessor;

public interface InputAccessor {
    default float getMovementForward() { return 0.0f; }
    default void setMovementForward(float value) {}
    default float getMovementSideways() { return 0.0f; }
    default void setMovementSideways(float value) {}
}