package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.model.ModelAttachment;
import dev.che1s.phantomcreeper.model.PhantomModel;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.util.MovementMath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class PhantomCreeperController {
    private enum State {
        HOVER,
        APPROACH,
        FUSE
    }

    private final JavaPlugin plugin;
    private final PhantomCreeperManager manager;
    private final Creeper entity;
    private final UUID entityId;
    private final Supplier<PluginSettings> settings;
    private final PhantomModel model;
    private final PhantomCreeperManager.SpawnSource spawnSource;
    private final Location home;
    private final World world;
    private final UUID worldId;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final TargetSelector targetSelector;
    private final FlightController flightController;
    private final FuseController fuseController = new FuseController();
    private final CreatureSoundController soundController = new CreatureSoundController();

    private PlatformScheduler.TaskHandle tickTask;
    private ModelAttachment modelAttachment;
    private State state = State.HOVER;
    private volatile UUID targetId;
    private int nextSearchTick;
    private int ageTicks;
    private int modelRetryTicks;
    private volatile double trackedX;
    private volatile double trackedY;
    private volatile double trackedZ;
    private volatile int trackedHitboxes;
    private volatile double trackedHealth;
    private volatile double trackedMaxHealth;
    private volatile double observedMovement;
    private volatile Position cachedPosition;

    record Position(double x, double y, double z) {}

    Position cachedPosition() { return cachedPosition; }

    PhantomCreeperController(
            JavaPlugin plugin,
            PhantomCreeperManager manager,
            Creeper entity,
            Supplier<PluginSettings> settings,
            PhantomModel model,
            UUID preferredTarget,
            PhantomCreeperManager.SpawnSource spawnSource) {
        this.plugin = plugin;
        this.manager = manager;
        this.entity = entity;
        this.entityId = entity.getUniqueId();
        this.settings = settings;
        this.model = model;
        this.spawnSource = spawnSource;
        this.home = entity.getLocation().clone();
        this.world = entity.getWorld();
        this.worldId = world.getUID();
        this.trackedX = entity.getLocation().getX();
        this.trackedY = entity.getLocation().getY();
        this.trackedZ = entity.getLocation().getZ();
        this.cachedPosition = new Position(trackedX, trackedY, trackedZ);
        this.targetId = preferredTarget;
        this.targetSelector = new TargetSelector(entity, manager.playerSnapshots());
        this.flightController = new FlightController(
                this,
                manager,
                entity,
                () -> modelAttachment,
                home
        );
    }

    public void start() {
        tickTask = PlatformScheduler.timerOnEntity(plugin, entity, 1L, 1L, this::tick, this::retired);
    }

    public Creeper entity() {
        return entity;
    }

    UUID entityId() { return entityId; }
    boolean isClosing() { return closing.get(); }

    /** 退役回调只操作本地引用和名额，不在此访问世界或实体。 */
    private void retired() {
        if (closing.compareAndSet(false, true)) {
            manager.forget(this);
        }
    }

    /** 由实体移除事件在拥有实体的上下文中调用，不重复移除载体。 */
    public void onRemoved() {
        if (closing.compareAndSet(false, true)) {
            cancelTask();
            closeModel();
            manager.forget(this);
        }
    }

    public PhantomCreeperManager.SpawnSource spawnSource() {
        return spawnSource;
    }

    public UUID primaryTarget() {
        return targetId;
    }

    public String positionStatus() {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", trackedX, trackedY, trackedZ);
    }

    public String velocityStatus() {
        // 实体连续两次真实坐标的位移（格/tick），不是施加速度的计算值。
        return String.format(Locale.ROOT, "%.2f", observedMovement);
    }

    public String healthStatus() {
        return String.format(
                Locale.ROOT,
                "%.1f/%.1f",
                trackedHealth,
                trackedMaxHealth
        );
    }

    public int hitboxCount() {
        return trackedHitboxes;
    }

    public Location cachedLocation() {
        return new Location(world, trackedX, trackedY, trackedZ);
    }

    public boolean sharesWorld(PhantomCreeperController other) {
        return worldId.equals(other.worldId);
    }

    void attachModel() {
        if (closing.get() || !entity.isValid()) {
            return;
        }
        if (modelAttachment != null && modelAttachment.isValid()) {
            return;
        }
        modelAttachment = model.attach(entity);
        if (modelAttachment != null) {
            modelRetryTicks = 0;
            entity.setInvisible(true);
            modelAttachment.setPatrolHeadLocked(true);
            modelAttachment.setHeadRotation(entity.getYaw(), 0.0F);
        }
    }

    public void damageTint() {
        if (modelAttachment != null && modelAttachment.isValid()) {
            modelAttachment.damageTint();
        }
    }

    public boolean onDeath() {
        if (!closing.compareAndSet(false, true)) {
            return false;
        }
        cancelTask();
        closeModel();
        manager.forget(this);
        // 立即移除隐形的原版载体，避免出现苦力怕倒地动画；
        // 改用独立模型追踪器播放自定义死亡动画。
        model.playDeathEffect(entity.getLocation());
        entity.remove();
        return true;
    }

    public void removeWithModel() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        cancelTask();
        closeModel();
        manager.forget(this);
        if (entity.isValid()) {
            entity.remove();
        }
    }

    public void closeModel() {
        ModelAttachment current = modelAttachment;
        modelAttachment = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "模型关闭失败：" + entityId, exception);
            }
        }
    }

    void stopTask() { cancelTask(); }

    public void reattachModel() {
        if (closing.get() || !entity.isValid()) {
            return;
        }
        closeModel();
        attachModel();
    }

    @SuppressWarnings("deprecation")
    private void tick() {
        if (closing.get()) {
            cancelTask();
            return;
        }
        if (!entity.isValid()) {
            onRemoved();
            return;
        }
        PluginSettings current = settings.get();
        ageTicks++;
        Location observed = entity.getLocation();
        double dx = observed.getX() - trackedX;
        double dy = observed.getY() - trackedY;
        double dz = observed.getZ() - trackedZ;
        observedMovement = Math.sqrt(dx * dx + dy * dy + dz * dz);
        trackedX = observed.getX();
        trackedY = observed.getY();
        trackedZ = observed.getZ();
        cachedPosition = new Position(trackedX, trackedY, trackedZ);
        trackedHealth = entity.getHealth();
        trackedMaxHealth = entity.getMaxHealth();
        trackedHitboxes = modelAttachment == null || !modelAttachment.isValid()
                ? 0
                : modelAttachment.hitboxCount();
        if (entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
        if (entity.getTarget() != null) {
            entity.setTarget(null);
        }
        if (entity.isIgnited()) {
            entity.setIgnited(false);
        }
        // 插件自有FuseController负责计时和爆炸；不让原版引信独立累积。
        entity.setFuseTicks(0);
        if (current.safety().despawnAfterTicks() > 0
                && ageTicks >= current.safety().despawnAfterTicks()) {
            removeWithModel();
            return;
        }
        if (modelAttachment == null || !modelAttachment.isValid()) {
            entity.setInvisible(!current.model().fallbackVisible());
            modelRetryTicks++;
            if (modelRetryTicks >= 20) {
                modelRetryTicks = 0;
                attachModel();
            }
        }
        if (state != State.FUSE) {
            soundController.tickFlight(entity.getWorld(), entity.getLocation());
        }

        TargetSnapshot target = targetSelector.resolve(targetId, current);
        if (target == null) {
            targetId = null;
        }
        if (state != State.FUSE && target == null && ageTicks >= nextSearchTick) {
            nextSearchTick = ageTicks + current.targeting().searchIntervalTicks();
            target = targetSelector.find(current);
            targetId = target == null ? null : target.id();
        }
        if (state == State.FUSE) {
            if (target == null || !withinFuseCancelRange(target, current)) {
                cancelFuse(target);
                return;
            }
            tickFuse(target, current);
            return;
        }
        if (target != null && isTriggered(target, current.combat().triggerRadius())) {
            beginFuse(current, target);
            return;
        }
        if (target == null) {
            state = State.HOVER;
            flightController.patrol(current, ageTicks);
        } else {
            state = State.APPROACH;
            flightController.approach(target, current, ageTicks);
        }
    }

    private boolean isTriggered(TargetSnapshot target, double radius) {
        Location at = entity.getLocation();
        return MovementMath.withinTrigger(
                at.getX() - target.x(),
                at.getY() - target.y(),
                at.getZ() - target.z(),
                radius
        );
    }

    private void beginFuse(PluginSettings current, TargetSnapshot target) {
        state = State.FUSE;
        targetId = target.id();
        fuseController.start(target, current.combat().fuseTicks());
        flightController.faceTarget(target);
        flightController.stop();
        soundController.playPrimed(entity.getWorld(), entity.getLocation());
    }

    private void tickFuse(TargetSnapshot target, PluginSettings current) {
        flightController.stop();
        if (target != null) {
            flightController.faceTarget(target);
        }
        if (fuseController.advance()) {
            detonate(current);
        }
    }

    private boolean withinFuseCancelRange(TargetSnapshot target, PluginSettings current) {
        double cancelRadius = current.combat().fuseCancelRadius();
        Location at = entity.getLocation();
        double x = at.getX() - target.x(), y = at.getY() - target.y(), z = at.getZ() - target.z();
        return x*x + y*y + z*z <= cancelRadius * cancelRadius;
    }

    private void cancelFuse(TargetSnapshot target) {
        fuseController.cancel();
        flightController.stop();
        if (target == null) {
            state = State.HOVER;
            targetId = null;
        } else {
            state = State.APPROACH;
            targetId = target.id();
        }
    }

    private void detonate(PluginSettings current) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        World world = entity.getWorld();
        Location center = entity.getLocation().clone().add(0.0, 1.0, 0.0);
        PluginSettings.Explosion explosion = current.combat().explosion();
        if (!explosion.damageSelf()) {
            entity.setInvulnerable(true);
        }
        manager.withExplosionContext(
                explosion,
                center,
                () -> world.createExplosion(
                        center,
                        (float) (explosion.radius() / 2.0),
                        explosion.setFire(),
                        explosion.breakBlocks(),
                        entity
                )
        );
        cancelTask();
        closeModel();
        manager.forget(this);
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private void cancelTask() {
        PlatformScheduler.TaskHandle current = tickTask;
        tickTask = null;
        if (current != null) {
            current.cancel();
        }
    }
}
