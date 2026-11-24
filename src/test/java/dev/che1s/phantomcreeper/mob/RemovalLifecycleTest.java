package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.listener.CreatureListener;
import dev.che1s.phantomcreeper.model.ModelAttachment;
import dev.che1s.phantomcreeper.model.PhantomModel;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemovalLifecycleTest {
    @Test
    void externalRemovalReleasesSlotAndModelOnceEvenWhenModelCloseThrows() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenAnswer(call -> mock(BukkitTask.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            JavaPlugin plugin = mock(JavaPlugin.class);
            org.bukkit.Server server = mock(org.bukkit.Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            when(plugin.getName()).thenReturn("PhantomCreeper");
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            World world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            Creeper entity = mock(Creeper.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenAnswer(call -> new Location(world, 1, 64, 1));
            when(entity.isValid()).thenReturn(true);
            when(entity.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(world.spawn(any(Location.class), eq(Creeper.class), any(java.util.function.Consumer.class)))
                    .thenAnswer(call -> {
                        java.util.function.Consumer<Creeper> initializer = call.getArgument(2);
                        initializer.accept(entity);
                        return entity;
                    });
            PluginSettings settings = mock(PluginSettings.class);
            PluginSettings.Combat combat = mock(PluginSettings.Combat.class);
            when(combat.maxHealth()).thenReturn(15.0);
            when(settings.combat()).thenReturn(combat);
            when(settings.model()).thenReturn(mock(PluginSettings.Model.class));
            PluginSettings.NaturalSpawn spawn = mock(PluginSettings.NaturalSpawn.class);
            when(settings.naturalSpawn()).thenReturn(spawn);
            when(spawn.maxTracked()).thenReturn(1);
            when(spawn.maxActive()).thenReturn(1);
            PhantomModel model = mock(PhantomModel.class);
            when(model.isLoaded()).thenReturn(true);
            ModelAttachment attachment = mock(ModelAttachment.class);
            when(attachment.isValid()).thenReturn(true);
            when(model.attach(entity)).thenReturn(attachment);
            doThrow(new IllegalStateException("intentional close failure")).when(attachment).close();
            PhantomCreeperManager manager = new PhantomCreeperManager(plugin, () -> settings,
                    mock(Language.class), model);
            try {
                PhantomCreeperController controller = manager.spawnNow(entity.getLocation(), null,
                        PhantomCreeperManager.SpawnSource.NATURAL);
                assertNotNull(controller);
                verify(entity).setAI(true);
                verify(entity, never()).setAI(false);
                verify(entity).setAware(false);
                verify(entity).setGravity(false);
                verify(entity).setFuseTicks(0);
                verify(entity).setMaxHealth(15.0);
                assertEquals("0.00", controller.velocityStatus());
                assertEquals(1, manager.size());
                CreatureListener listener = new CreatureListener(manager, () -> settings, null);
                listener.onRemoved(new EntityRemoveFromWorldEvent(entity, world));
                listener.onRemoved(new EntityRemoveFromWorldEvent(entity, world));
                assertEquals(0, manager.size());
                assertEquals(0, manager.naturalCount());
                verify(attachment, times(1)).close();
                verify(entity, never()).remove();
                // 相同的硬上限下能再次生成，证明名额确实归还，而非仅隐藏计数。
                assertNotNull(manager.spawnNow(entity.getLocation(), null,
                        PhantomCreeperManager.SpawnSource.NATURAL));
            } finally {
                manager.shutdown();
            }
        }
    }
}
