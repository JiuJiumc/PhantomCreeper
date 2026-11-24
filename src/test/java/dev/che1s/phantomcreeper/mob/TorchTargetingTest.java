package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TorchTargetingTest {
    @Test void torchCancelsActiveFuseAndRemovingTorchAllowsFuseAgain() throws Exception {
        UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Creeper mob = mock(Creeper.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        when(mob.getEyeLocation()).thenAnswer(call -> new Location(world, 0, 65, 0));
        when(mob.isValid()).thenReturn(true);
        PlayerSnapshots snapshots = mock(PlayerSnapshots.class);
        TargetSnapshot target = sample(id, worldId, 1);
        when(snapshots.get(id)).thenReturn(target);
        when(snapshots.values()).thenReturn(List.of(target));
        when(snapshots.protectedFromPhantoms(id)).thenReturn(true);
        PhantomCreeperManager manager = mock(PhantomCreeperManager.class);
        when(manager.playerSnapshots()).thenReturn(snapshots);
        PluginSettings settings = mock(PluginSettings.class);
        when(settings.targeting()).thenReturn(new PluginSettings.Targeting(32, 48, true, true, false, 1));
        when(settings.safety()).thenReturn(new PluginSettings.Safety(true, 0));
        when(settings.combat()).thenReturn(new PluginSettings.Combat(15, 3, 30, 5, null, 0, 0, 0));
        var controller = new PhantomCreeperController(mock(org.bukkit.plugin.java.JavaPlugin.class),
                manager, mob, () -> settings, mock(dev.che1s.phantomcreeper.model.PhantomModel.class),
                id, PhantomCreeperManager.SpawnSource.COMMAND);
        var attachment = mock(dev.che1s.phantomcreeper.model.ModelAttachment.class);
        when(attachment.isValid()).thenReturn(true);
        var attachmentField = PhantomCreeperController.class.getDeclaredField("modelAttachment");
        attachmentField.setAccessible(true);
        attachmentField.set(controller, attachment);
        var soundField = PhantomCreeperController.class.getDeclaredField("soundController");
        soundField.setAccessible(true);
        soundField.set(controller, mock(CreatureSoundController.class));
        var state = PhantomCreeperController.class.getDeclaredField("state");
        state.setAccessible(true);
        Object fuse = java.util.Arrays.stream(state.getType().getEnumConstants())
                .filter(value -> value.toString().equals("FUSE")).findFirst().orElseThrow();
        state.set(controller, fuse);
        var tick = PhantomCreeperController.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(controller);
        assertEquals("HOVER", state.get(controller).toString());
        assertNull(controller.primaryTarget());
        when(snapshots.protectedFromPhantoms(id)).thenReturn(false);
        tick.invoke(controller);
        assertEquals("FUSE", state.get(controller).toString());
        assertEquals(id, controller.primaryTarget());
        when(snapshots.protectedFromPhantoms(id)).thenReturn(true);
        tick.invoke(controller);
        assertEquals("HOVER", state.get(controller).toString());
        assertNull(controller.primaryTarget());
        verify(mob, never()).remove();
    }

    @Test void protectedNearestPlayerIsSkippedAndExistingTargetIsInvalidatedThenRestored() {
        UUID worldId = UUID.randomUUID(), torch = UUID.randomUUID(), other = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Creeper mob = mock(Creeper.class);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        PlayerSnapshots snapshots = mock(PlayerSnapshots.class);
        TargetSnapshot near = sample(torch, worldId, 1), far = sample(other, worldId, 4);
        when(snapshots.get(torch)).thenReturn(near);
        when(snapshots.values()).thenReturn(List.of(near, far));
        PluginSettings settings = mock(PluginSettings.class);
        when(settings.targeting()).thenReturn(new PluginSettings.Targeting(32, 48, true, true, false, 5));
        TargetSelector selector = new TargetSelector(mob, snapshots);
        assertSame(near, selector.resolve(torch, settings));
        when(snapshots.protectedFromPhantoms(torch)).thenReturn(true);
        assertNull(selector.resolve(torch, settings), "现有追击/引信的目标也必须失效");
        assertSame(far, selector.find(settings), "仍可主动攻击其他未持火把玩家");
        when(snapshots.protectedFromPhantoms(torch)).thenReturn(false);
        assertSame(near, selector.find(settings));
        assertSame(near, selector.resolve(torch, settings));
    }

    private TargetSnapshot sample(UUID id, UUID world, double x) {
        return new TargetSnapshot(id, world, x, 64, 0, 1.62, false, false, false, false, System.nanoTime());
    }
}
