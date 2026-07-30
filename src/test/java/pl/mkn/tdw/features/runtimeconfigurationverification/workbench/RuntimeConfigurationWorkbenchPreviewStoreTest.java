package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
        .RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigurationWorkbenchPreviewStoreTest {

    @Test
    void shouldExpireSanitizedSnapshot() {
        var clock = new MutableClock(Instant.parse("2026-07-30T10:00:00Z"));
        var store = new RuntimeConfigurationWorkbenchPreviewStore(
                clock,
                Duration.ofMinutes(10),
                2
        );

        var stored = store.store(snapshot());
        assertThat(store.require(stored.previewId())).isSameAs(stored.snapshot());

        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> store.require(stored.previewId()))
                .isInstanceOf(RuntimeConfigurationWorkbenchPreviewNotFoundException.class);
    }

    @Test
    void shouldEvictOldestSnapshotWhenCapacityIsReached() {
        var clock = new MutableClock(Instant.parse("2026-07-30T10:00:00Z"));
        var store = new RuntimeConfigurationWorkbenchPreviewStore(
                clock,
                Duration.ofMinutes(10),
                1
        );

        var oldest = store.store(snapshot());
        clock.advance(Duration.ofSeconds(1));
        var newest = store.store(snapshot());

        assertThatThrownBy(() -> store.require(oldest.previewId()))
                .isInstanceOf(RuntimeConfigurationWorkbenchPreviewNotFoundException.class);
        assertThat(store.require(newest.previewId())).isNotNull();
    }

    private RuntimeConfigurationWorkbenchPreviewSnapshot snapshot() {
        return new RuntimeConfigurationWorkbenchPreviewSnapshot(
                RuntimeConfigurationVerificationMode.BASIC,
                null,
                null,
                new RuntimeConfigurationPromptPreparation("", Map.of(), List.of()),
                List.of(),
                List.of()
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
