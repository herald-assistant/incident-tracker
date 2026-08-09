package pl.mkn.tdw.features.configdriftviewer.workbench;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.job.api
        .ConfigDriftViewerMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDriftViewerWorkbenchPreviewStoreTest {

    @Test
    void shouldExpireEphemeralSnapshotIncludingOperatorProjection() {
        var clock = new MutableClock(Instant.parse("2026-07-30T10:00:00Z"));
        var store = new ConfigDriftViewerWorkbenchPreviewStore(
                clock,
                Duration.ofMinutes(10),
                2
        );

        var stored = store.store(snapshot());
        assertThat(store.require(stored.previewId())).isSameAs(stored.snapshot());

        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> store.require(stored.previewId()))
                .isInstanceOf(ConfigDriftViewerWorkbenchPreviewNotFoundException.class);
    }

    @Test
    void shouldEvictOldestSnapshotWhenCapacityIsReached() {
        var clock = new MutableClock(Instant.parse("2026-07-30T10:00:00Z"));
        var store = new ConfigDriftViewerWorkbenchPreviewStore(
                clock,
                Duration.ofMinutes(10),
                1
        );

        var oldest = store.store(snapshot());
        clock.advance(Duration.ofSeconds(1));
        var newest = store.store(snapshot());

        assertThatThrownBy(() -> store.require(oldest.previewId()))
                .isInstanceOf(ConfigDriftViewerWorkbenchPreviewNotFoundException.class);
        assertThat(store.require(newest.previewId())).isNotNull();
    }

    private ConfigDriftViewerWorkbenchPreviewSnapshot snapshot() {
        return new ConfigDriftViewerWorkbenchPreviewSnapshot(
                ConfigDriftViewerMode.BASIC,
                null,
                new ConfigDriftViewerDiffProjection("dev1", "zt001", List.of()),
                null,
                null,
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
