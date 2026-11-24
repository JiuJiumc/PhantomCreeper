package dev.che1s.phantomcreeper.mob;

import cn.starryhuanyi.api.TorchProtection;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TorchProtectionHookTest {
    @Test void optionalProviderEnableDisableAndConsumerCloseDoNotRetainProtection() {
        JavaPlugin owner = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(owner.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
        TorchProtectionHook hook = new TorchProtectionHook(owner);
        UUID id = UUID.randomUUID();
        assertFalse(hook.protects(id));
        Plugin provider = mock(Plugin.class, withSettings().extraInterfaces(TorchProtection.class));
        when(provider.getName()).thenReturn("StarryHuanYi");
        when(provider.isEnabled()).thenReturn(true);
        when(((TorchProtection) provider).isProtectedFromPhantoms(id)).thenReturn(true);
        PluginEnableEvent enable = mock(PluginEnableEvent.class);
        when(enable.getPlugin()).thenReturn(provider);
        PluginDisableEvent disable = mock(PluginDisableEvent.class);
        when(disable.getPlugin()).thenReturn(provider);
        hook.enabled(enable);
        assertTrue(hook.protects(id));
        assertFalse(hook.protects(null));
        hook.disabled(disable);
        assertFalse(hook.protects(id));
        hook.enabled(enable);
        assertTrue(hook.protects(id));
        hook.close();
        assertFalse(hook.protects(id));
        hook.enabled(enable);
        assertFalse(hook.protects(id));
    }
}
