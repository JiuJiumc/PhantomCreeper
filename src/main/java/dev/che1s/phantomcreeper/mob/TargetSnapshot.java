package dev.che1s.phantomcreeper.mob;

import java.util.UUID;

/** 在玩家所属线程发布的不可变数据，不携带可供跨区域访问的玩家对象。 */
public record TargetSnapshot(UUID id, UUID worldId, double x, double y, double z,
                             double eyeHeight, boolean dead, boolean invulnerable,
                             boolean creative, boolean spectator, long sampledAt) {
    boolean fresh(long now) {
        return now - sampledAt >= 0 && now - sampledAt <= 5_000_000_000L;
    }
}
