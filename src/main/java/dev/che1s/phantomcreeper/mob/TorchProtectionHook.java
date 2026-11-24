package dev.che1s.phantomcreeper.mob;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Predicate;

/** 仅查询火把插件公开的 UUID 快照接口，不接触其他区域的玩家。 */
final class TorchProtectionHook implements Listener, AutoCloseable {
    private static final Predicate<UUID> NONE = id -> false;
    private final JavaPlugin owner;
    private volatile Predicate<UUID> protection = NONE;
    private volatile boolean closed;

    TorchProtectionHook(JavaPlugin owner) {
        this.owner = owner;
        owner.getServer().getPluginManager().registerEvents(this, owner);
        connect(owner.getServer().getPluginManager().getPlugin("StarryHuanYi"));
    }

    boolean protects(UUID id) { return id != null && protection.test(id); }

    private void connect(Plugin provider) {
        if (closed || provider == null || !provider.isEnabled()) return;
        try {
            protection = ApiBridge.bind(provider);
            owner.getLogger().info("已接入 StarryHuanYi 火把保护：不锁定受保护玩家，爆炸波及伤害保持原样。");
        } catch (LinkageError | IllegalArgumentException error) {
            protection = NONE;
            owner.getLogger().warning("StarryHuanYi 缺少火把保护接口，请同时更新两个插件并重启；当前未启用火把联动。");
        }
    }

    // API 仅在提供者存在时链接，缺少可选依赖仍可正常加载主插件。
    private static final class ApiBridge {
        static Predicate<UUID> bind(Plugin provider) {
            if (!(provider instanceof cn.starryhuanyi.api.TorchProtection api))
                throw new IllegalArgumentException("Unsupported StarryHuanYi API");
            return api::isProtectedFromPhantoms;
        }
    }

    @EventHandler public void enabled(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("StarryHuanYi")) connect(event.getPlugin());
    }

    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("StarryHuanYi")) protection = NONE;
    }

    @Override public void close() {
        closed = true;
        protection = NONE;
        HandlerList.unregisterAll(this);
    }
}
