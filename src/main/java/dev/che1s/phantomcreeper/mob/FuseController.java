package dev.che1s.phantomcreeper.mob;


import java.util.UUID;

/**
 * 管理引信计时、锁定目标和取消状态。
 */
final class FuseController {
    private UUID targetId;
    private int remainingTicks;

    boolean isActive() {
        return remainingTicks > 0;
    }

    UUID targetId() {
        return targetId;
    }

    void start(TargetSnapshot target, int configuredTicks) {
        targetId = target.id();
        remainingTicks = Math.max(1, configuredTicks);
    }

    boolean advance() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        return remainingTicks <= 0;
    }

    void cancel() {
        targetId = null;
        remainingTicks = 0;
    }
}
