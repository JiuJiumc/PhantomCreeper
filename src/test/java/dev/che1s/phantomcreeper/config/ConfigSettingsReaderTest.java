package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSettingsReaderTest {
    @Test
    void currentCustomValuesSurviveMigrationAndReading() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat.max-health", 15.0);
        config.set("movement.cruise-speed", .4);
        config.set("movement.chase-speed", .5);
        config.set("movement.max-speed", .68);
        config.set("natural-spawn.check-interval-ticks", 200);
        config.set("natural-spawn.worlds", java.util.List.of("world"));
        config.set("natural-spawn.phantom-threshold-spawn.enabled", true);
        config.set("natural-spawn.phantom-threshold-spawn.scan-radius", 32.0);
        config.set("natural-spawn.phantom-threshold-spawn.triggers", java.util.List.of(
                java.util.Map.of("phantom-count", 1, "spawn-count", 1),
                java.util.Map.of("phantom-count", 6, "spawn-count", 4)));
        ConfigMigrationService.migrate(config);
        PluginSettings settings = ConfigSettingsReader.read(config);
        assertEquals(15.0, settings.combat().maxHealth());
        assertEquals(.4, settings.movement().cruiseSpeed());
        assertEquals(.5, settings.movement().chaseSpeed());
        assertEquals(.68, settings.movement().maxSpeed());
        assertEquals(java.util.List.of("world"), settings.naturalSpawn().worlds());
        assertEquals(32.0, settings.naturalSpawn().phantomThresholdSpawn().scanRadius());
        assertEquals(java.util.List.of(new PluginSettings.PhantomThreshold(1, 1),
                        new PluginSettings.PhantomThreshold(6, 4)),
                settings.naturalSpawn().phantomThresholdSpawn().triggers());
    }

    @Test
    void relatedRangesAreNormalized() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("targeting.acquire-range", 64.0);
        config.set("targeting.lose-range", 32.0);
        config.set("movement.cruise-speed", 0.5);
        config.set("movement.chase-speed", 0.8);
        config.set("movement.max-speed", 0.1);
        config.set("combat.trigger-radius", 5.0);
        config.set("combat.fuse-cancel-radius", 2.0);
        config.set("natural-spawn.spawn-area.min-distance", 40.0);
        config.set("natural-spawn.spawn-area.max-distance", 20.0);
        config.set("natural-spawn.spawn-area.min-height-offset", 12.0);
        config.set("natural-spawn.spawn-area.max-height-offset", 6.0);

        PluginSettings settings = ConfigSettingsReader.read(config);

        assertEquals(64.0, settings.targeting().loseRange());
        assertEquals(0.8, settings.movement().maxSpeed());
        assertEquals(5.0, settings.combat().fuseCancelRadius());
        assertEquals(20.0, settings.naturalSpawn().spawnArea().minDistance());
        assertEquals(40.0, settings.naturalSpawn().spawnArea().maxDistance());
        assertEquals(6.0, settings.naturalSpawn().spawnArea().minHeightOffset());
        assertEquals(12.0, settings.naturalSpawn().spawnArea().maxHeightOffset());
    }

    @Test
    void performanceIntervalsHaveDefaultsAndRejectUnboundedDelays() {
        YamlConfiguration config = new YamlConfiguration();
        PluginSettings defaults = ConfigSettingsReader.read(config);
        assertEquals(10, defaults.targeting().searchIntervalTicks());
        assertEquals(5, defaults.movement().separationIntervalTicks());
        config.set("targeting.search-interval-ticks", -20);
        config.set("movement.separation-interval-ticks", Integer.MAX_VALUE);
        PluginSettings clamped = ConfigSettingsReader.read(config);
        assertEquals(1, clamped.targeting().searchIntervalTicks());
        assertEquals(100, clamped.movement().separationIntervalTicks());
    }
}
