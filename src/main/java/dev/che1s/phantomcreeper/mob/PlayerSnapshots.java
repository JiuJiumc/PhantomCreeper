package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** 仅有受管理生物时采样；每个玩家最多一个待处理采样任务。 */
public final class PlayerSnapshots implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BooleanSupplier needed;
    private final TorchProtectionHook torchProtection;
    private final Map<UUID, TargetSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final PlatformScheduler.TaskHandle task;
    private volatile boolean closed;

    public PlayerSnapshots(JavaPlugin plugin, BooleanSupplier needed) {
        this.plugin = plugin;
        this.needed = needed;
        this.torchProtection = new TorchProtectionHook(plugin);
        task = PlatformScheduler.globalTimer(plugin, 1, 5, this::sample);
    }

    private void sample() {
        if (closed) return;
        if (!needed.getAsBoolean()) {
            snapshots.clear();
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            online.add(id);
            if (!pending.add(id)) continue;
            Runnable retired = () -> {
                pending.remove(id);
                snapshots.remove(id);
            };
            if (PlatformScheduler.isFolia()) {
                PlatformScheduler.runOnEntity(plugin, player, () -> capture(player, id), retired);
            } else {
                capture(player, id);
            }
        }
        snapshots.keySet().retainAll(online);
    }

    private void capture(Player player, UUID id) {
        try {
            if (closed || !player.isOnline()) {
                snapshots.remove(id);
                return;
            }
            Location at = player.getLocation();
            GameMode mode = player.getGameMode();
            TargetSnapshot snapshot = new TargetSnapshot(id, at.getWorld().getUID(),
                    at.getX(), at.getY(), at.getZ(), player.getEyeHeight(),
                    player.isDead(), player.isInvulnerable(),
                    mode == GameMode.CREATIVE, mode == GameMode.SPECTATOR, System.nanoTime());
            snapshots.put(id, snapshot);
            if (closed) snapshots.remove(id, snapshot);
        } finally {
            pending.remove(id);
        }
    }

    TargetSnapshot get(UUID id) { return id == null ? null : snapshots.get(id); }
    Collection<TargetSnapshot> values() { return snapshots.values(); }

    boolean protectedFromPhantoms(UUID id) { return torchProtection.protects(id); }

    @Override
    public void close() {
        closed = true;
        torchProtection.close();
        task.cancel();
        snapshots.clear();
        pending.clear();
    }
}
