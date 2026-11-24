package dev.che1s.phantomcreeper.mob;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSnapshotTest {
    @Test
    void oldAndFutureSamplesCannotRemainAttackTargets() {
        long sampledAt = 9_000_000_000L;
        TargetSnapshot sample = new TargetSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                1, 2, 3, 1.62, false, false, false, false, sampledAt);
        assertTrue(sample.fresh(sampledAt));
        assertTrue(sample.fresh(sampledAt + 5_000_000_000L));
        assertFalse(sample.fresh(sampledAt + 5_000_000_001L));
        assertFalse(sample.fresh(sampledAt - 1));
    }
}
