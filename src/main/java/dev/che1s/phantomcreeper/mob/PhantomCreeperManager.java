package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.model.PhantomModel;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.util.ExplosionMath;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PhantomCreeperManager {
    public enum SpawnSource {
        NATURAL,
        COMMAND
    }

    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final Language language;
    private final PhantomModel model;
    private final NamespacedKey markerKey;
    private final Map<UUID, PhantomCreeperController> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentExplosionVictims = new ConcurrentHashMap<>();
    private final SpawnQuota spawnQuota = new SpawnQuota();
    private final PlayerSnapshots playerSnapshots;
    private final ThreadLocal<ExplosionContext> explosionContext = new ThreadLocal<>();
    private final PlatformScheduler.TaskHandle explosionCleanupTask;
    private volatile boolean stopped;

    public PhantomCreeperManager(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Language language,
            PhantomModel model) {
        this.plugin = plugin;
        this.settings = settings;
        this.language = language;
        this.model = model;
        this.markerKey = new NamespacedKey(plugin, "phantom_creeper");
        this.playerSnapshots = new PlayerSnapshots(plugin, () -> !active.isEmpty());
        this.explosionCleanupTask = PlatformScheduler.globalTimer(
                plugin,
                20L,
                20L,
                this::purgeExpiredExplosionVictims
        );
    }

    @SuppressWarnings("deprecation")
    public PhantomCreeperController spawnNow(Location location, UUID preferredTarget) {
        return spawnNow(location, preferredTarget, SpawnSource.COMMAND);
    }

    @SuppressWarnings("deprecation")
    public PhantomCreeperController spawnNow(
            Location location,
            UUID preferredTarget,
            SpawnSource source) {
        World world = location.getWorld();
        if (stopped || world == null
                || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        PluginSettings current = settings.get();
        if (!spawnQuota.reserve(
                source,
                Math.max(1, current.naturalSpawn().maxTracked()),
                current.naturalSpawn().maxActive()
        )) {
            return null;
        }
        Creeper carrier = null;
        try {
            carrier = world.spawn(location, Creeper.class, entity -> {
                // NoAI会使26.2跳过LivingEntity的速度位移积分。
                // 保留物理移动，仅关闭自主感知/目标AI，飞行由实体调度器控制。
                entity.setAI(true);
                entity.setAware(false);
                entity.setGravity(false);
                entity.setCollidable(false);
                entity.setPersistent(false);
                entity.setRemoveWhenFarAway(false);
                entity.setCanPickupItems(false);
                entity.setSilent(false);
                entity.setFireTicks(0);
                entity.setMaxHealth(current.combat().maxHealth());
                entity.setHealth(current.combat().maxHealth());
                entity.customName(language.component("creature.name", Map.of()));
                entity.setCustomNameVisible(false);
                entity.getPersistentDataContainer().set(
                        markerKey, PersistentDataType.BYTE, (byte) 1);
                entity.addScoreboardTag("phantomcreeper");
                entity.setTarget(null);
                entity.setIgnited(false);
                entity.setFuseTicks(0);
                if (!current.model().fallbackVisible() && !model.isLoaded()) {
                    entity.setInvisible(true);
                }
            });
        } catch (RuntimeException exception) {
            spawnQuota.release(source);
            plugin.getLogger().warning(language.plain(
                    "log.spawn-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return null;
        }
        try {
            PhantomCreeperController controller = new PhantomCreeperController(
                    plugin, this, carrier, settings, model, preferredTarget, source);
            active.put(carrier.getUniqueId(), controller);
            if (stopped) {
                controller.removeWithModel();
                return null;
            }
            controller.start();
            if (controller.isClosing()) return null;
            if (model.isLoaded()) {
                controller.attachModel();
            }
            return controller;
        } catch (RuntimeException exception) {
            PhantomCreeperController controller = active.get(carrier.getUniqueId());
            if (controller != null) {
                controller.removeWithModel();
            } else {
                carrier.remove();
                spawnQuota.release(source);
            }
            plugin.getLogger().warning(language.plain(
                    "log.spawn-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return null;
        }
    }

    public PhantomCreeperController controller(Entity entity) {
        if (entity == null) {
            return null;
        }
        return active.get(entity.getUniqueId());
    }

    public boolean isManaged(Entity entity) {
        return entity != null
                && (active.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE));
    }

    public int size() {
        return active.size();
    }

    PlayerSnapshots playerSnapshots() { return playerSnapshots; }

    public int naturalCount() {
        return Math.min(spawnQuota.naturalReservations(), active.size());
    }

    public int naturalLimit() {
        return settings.get().naturalSpawn().maxActive();
    }

    public int countNear(UUID playerId) {
        int count = 0;
        for (PhantomCreeperController controller : active.values()) {
            if (playerId.equals(controller.primaryTarget())) {
                count++;
            }
        }
        return count;
    }

    public Collection<PhantomCreeperController> activeControllers() {
        return active.values();
    }

    public PhantomCreeperController firstController() {
        return active.values().stream().findFirst().orElse(null);
    }

    public Vector separationVector(PhantomCreeperController source, double radius) {
        Vector result = new Vector();
        PhantomCreeperController.Position sourceLocation = source.cachedPosition();
        double radiusSquared = radius * radius;
        for (PhantomCreeperController other : active.values()) {
            if (other == source || !other.sharesWorld(source)) {
                continue;
            }
            PhantomCreeperController.Position otherLocation = other.cachedPosition();
            double x = sourceLocation.x() - otherLocation.x();
            double y = sourceLocation.y() - otherLocation.y();
            double z = sourceLocation.z() - otherLocation.z();
            double distanceSquared = x*x + y*y + z*z;
            if (distanceSquared >= radiusSquared) {
                continue;
            }
            if (distanceSquared < 1.0E-6) {
                double angle = source.entityId().hashCode() * 0.017453292519943295;
                x = Math.cos(angle); y = 0; z = Math.sin(angle);
            } else {
                double inverse = 1.0 / Math.sqrt(distanceSquared);
                x *= inverse; y *= inverse; z *= inverse;
            }
            double distance = Math.sqrt(Math.max(distanceSquared, 0.0));
            double weight = (radius - distance) / radius;
            result.setX(result.getX() + x * weight);
            result.setY(result.getY() + y * weight);
            result.setZ(result.getZ() + z * weight);
        }
        return result;
    }

    public void withExplosionContext(
            PluginSettings.Explosion settings,
            Location center,
            Runnable explosionAction) {
        ExplosionContext previous = explosionContext.get();
        explosionContext.set(new ExplosionContext(settings, center.clone()));
        try {
            explosionAction.run();
        } finally {
            if (previous == null) {
                explosionContext.remove();
            } else {
                explosionContext.set(previous);
            }
        }
    }

    public void adjustExplosionDamage(EntityDamageEvent event) {
        ExplosionContext context = explosionContext.get();
        if (context == null
                || (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            return;
        }
        Location entityLocation = event.getEntity().getLocation();
        if (!entityLocation.getWorld().equals(context.center().getWorld())) {
            return;
        }
        double distance = entityLocation.distance(context.center());
        event.setDamage(ExplosionMath.damageAtDistance(
                context.settings().damage(),
                context.settings().radius(),
                context.settings().damageFalloff(),
                distance
        ));
        if (event.getEntity() instanceof Player player) {
            long now = System.currentTimeMillis();
            purgeExpiredExplosionVictims(now);
            recentExplosionVictims.put(player.getUniqueId(), now + 500L);
        }
    }

    public boolean consumeExplosionDeath(UUID playerId) {
        Long expiry = recentExplosionVictims.remove(playerId);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    public void forget(PhantomCreeperController controller) {
        if (active.remove(controller.entityId(), controller)) {
            spawnQuota.release(controller.spawnSource());
        }
    }

    public void onModelImported() {
        plugin.getLogger().info(language.plain("model.loaded", Map.of(
                "engine", model.backendName(),
                "model", settings.get().model().name()
        )));
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(
                    plugin,
                    controller.entity(),
                    controller::attachModel
            );
        }
    }

    public void removeAll() {
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(plugin, controller.entity(), controller::removeWithModel);
        }
    }

    public void reattachAll() {
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(plugin, controller.entity(), controller::reattachModel);
        }
    }

    public void shutdown() {
        stopped = true;
        playerSnapshots.close();
        explosionCleanupTask.cancel();
        for (PhantomCreeperController controller : active.values()) {
            controller.stopTask();
            if (PlatformScheduler.owns(controller.entity())) {
                controller.removeWithModel();
            } else {
                // 停用时不能再向已禁用插件注册实体任务，更不能跨 Region 销毁载体。
                // Folia 安装/卸载需完整重启；正常 /phantomcreeper reload 不走此分支。
                plugin.getLogger().warning("Folia 停用：跨区域载体请通过完整重启清理，不支持热卸载。");
            }
        }
        active.clear();
        spawnQuota.reset();
    }

    private void purgeExpiredExplosionVictims() {
        purgeExpiredExplosionVictims(System.currentTimeMillis());
    }

    private void purgeExpiredExplosionVictims(long now) {
        recentExplosionVictims.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private record ExplosionContext(PluginSettings.Explosion settings, Location center) {
    }
}
