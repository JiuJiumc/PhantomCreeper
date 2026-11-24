package dev.che1s.phantomcreeper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformSchedulerTest {
    @Test
    void cancelReachesPendingAndRunningTasksAndIsSafeToRepeat() {
        for (ScheduledTask.ExecutionState state : ScheduledTask.ExecutionState.values()) {
            AtomicInteger cancellations = new AtomicInteger();
            ScheduledTask task = (ScheduledTask) Proxy.newProxyInstance(
                    ScheduledTask.class.getClassLoader(), new Class<?>[]{ScheduledTask.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getExecutionState" -> state;
                        case "cancel" -> {
                            cancellations.incrementAndGet();
                            yield ScheduledTask.CancelledState.CANCELLED_BY_CALLER;
                        }
                        default -> throw new AssertionError("Unexpected task access: " + method.getName());
                    });
            PlatformScheduler.cancel(task);
            PlatformScheduler.cancel(task);
            assertEquals(2, cancellations.get(), "cancel must reach the scheduler, including IDLE: " + state);
        }
        PlatformScheduler.cancel(null);
    }
}
