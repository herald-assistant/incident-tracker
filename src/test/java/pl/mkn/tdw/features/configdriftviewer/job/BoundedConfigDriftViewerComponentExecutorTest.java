package pl.mkn.tdw.features.configdriftviewer.job;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedConfigDriftViewerComponentExecutorTest {

    @Test
    void shouldUseConfiguredParallelismAndBoundedQueue() {
        var properties = new ConfigDriftViewerRepositoryProperties();
        properties.setMaxParallelComponents(3);
        var executor = new BoundedConfigDriftViewerComponentExecutor(properties);
        try {
            assertEquals(3, executor.maxParallelComponents());
            assertEquals(150, executor.queueCapacity());
            assertEquals("runtime-config-component-", executor.threadNamePrefix());
        } finally {
            executor.shutdown();
        }
    }
}
