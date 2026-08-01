package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedRuntimeConfigurationComponentExecutorTest {

    @Test
    void shouldUseConfiguredParallelismAndBoundedQueue() {
        var properties = new RuntimeConfigurationRepositoryProperties();
        properties.setMaxParallelComponents(3);
        var executor = new BoundedRuntimeConfigurationComponentExecutor(properties);
        try {
            assertEquals(3, executor.maxParallelComponents());
            assertEquals(150, executor.queueCapacity());
            assertEquals("runtime-config-component-", executor.threadNamePrefix());
        } finally {
            executor.shutdown();
        }
    }
}
