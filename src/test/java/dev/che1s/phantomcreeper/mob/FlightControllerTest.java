package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlightControllerTest {
    @Test
    void patrolUsesBoundedVelocityAndThrottlesAvoidanceWithoutTeleporting() {
        World world = mock(World.class);
        Creeper entity = mock(Creeper.class);
        when(entity.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        PhantomCreeperManager manager = mock(PhantomCreeperManager.class);
        PhantomCreeperController owner = mock(PhantomCreeperController.class);
        when(manager.separationVector(eq(owner), anyDouble())).thenReturn(new Vector());
        PluginSettings settings = mock(PluginSettings.class);
        PluginSettings.Movement movement = new PluginSettings.Movement(
                .32, .48, .68, .2, 6, 1.2, 8, 3.5, 72, 6, 5);
        when(settings.movement()).thenReturn(movement);
        FlightController flight = new FlightController(owner, manager, entity, () -> null,
                new Location(world, 0, 64, 0));
        for (int tick = 1; tick <= 10; tick++) flight.patrol(settings, tick);
        verify(manager, times(2)).separationVector(owner, 6);
        verify(entity, never()).teleport(any(Location.class));
        verify(entity, never()).teleportAsync(any(Location.class));
        ArgumentCaptor<Vector> velocities = ArgumentCaptor.forClass(Vector.class);
        verify(entity, times(10)).setVelocity(velocities.capture());
        for (Vector velocity : velocities.getAllValues()) {
            assertTrue(velocity.length() > 0);
            assertTrue(velocity.length() <= movement.maxSpeed() + 1E-9);
        }
    }
}
