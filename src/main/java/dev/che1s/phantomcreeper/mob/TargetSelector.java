package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 负责解析和搜索幻翼苦力怕的当前目标。
 */
final class TargetSelector {
    private final Creeper entity;
    private final PlayerSnapshots snapshots;

    TargetSelector(Creeper entity, PlayerSnapshots snapshots) {
        this.entity = entity;
        this.snapshots = snapshots;
    }

    TargetSnapshot resolve(UUID playerId, PluginSettings current) {
        TargetSnapshot player = snapshots.get(playerId);
        if (!valid(player, current)) return null;
        double loseRange = current.targeting().loseRange();
        if (distanceSquared(entity.getLocation(), player)
                > loseRange * loseRange) {
            return null;
        }
        return player;
    }

    TargetSnapshot find(PluginSettings current) {
        TargetSnapshot best = null;
        Location at = entity.getLocation();
        double bestDistance = current.targeting().acquireRange()
                * current.targeting().acquireRange();
        for (TargetSnapshot player : snapshots.values()) {
            if (!player.worldId().equals(at.getWorld().getUID())) continue;
            double distance = distanceSquared(at, player);
            if (distance >= bestDistance) {
                continue;
            }
            // 先做低成本的距离筛选，再执行可能较慢的视线检测。
            if (!valid(player, current)) {
                continue;
            }
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    private boolean valid(TargetSnapshot player, PluginSettings current) {
        if (player == null || !player.fresh(System.nanoTime())
                || !player.worldId().equals(entity.getWorld().getUID())
                || player.dead() || player.invulnerable()
                || snapshots.protectedFromPhantoms(player.id())) {
            return false;
        }
        if (current.targeting().ignoreCreative() && player.creative()) {
            return false;
        }
        if (current.targeting().ignoreSpectator() && player.spectator()) {
            return false;
        }
        if (!current.targeting().requireLineOfSight()) return true;
        Player live = Bukkit.getPlayer(player.id());
        // LOS 需要访问活实体；目标未归当前线程所有时暂不选择，不越权检测。
        return live != null && PlatformScheduler.owns(live)
                && live.getWorld().equals(entity.getWorld()) && entity.hasLineOfSight(live);
    }

    private static double distanceSquared(Location at, TargetSnapshot target) {
        double x = at.getX() - target.x(), y = at.getY() - target.y(), z = at.getZ() - target.z();
        return x*x + y*y + z*z;
    }
}
