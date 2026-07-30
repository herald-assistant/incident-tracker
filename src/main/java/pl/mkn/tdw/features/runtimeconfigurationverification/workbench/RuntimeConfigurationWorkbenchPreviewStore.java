package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuntimeConfigurationWorkbenchPreviewStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ENTRIES = 32;

    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public RuntimeConfigurationWorkbenchPreviewStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_ENTRIES);
    }

    RuntimeConfigurationWorkbenchPreviewStore(Clock clock, Duration ttl, int maxEntries) {
        this.clock = clock;
        this.ttl = ttl != null && !ttl.isNegative() && !ttl.isZero() ? ttl : DEFAULT_TTL;
        this.maxEntries = Math.max(1, maxEntries);
    }

    synchronized StoredPreview store(RuntimeConfigurationWorkbenchPreviewSnapshot snapshot) {
        var now = clock.instant();
        prune(now);
        var previewId = UUID.randomUUID().toString();
        var expiresAt = now.plus(ttl);
        entries.put(previewId, new Entry(snapshot, now, expiresAt));
        return new StoredPreview(previewId, expiresAt, snapshot);
    }

    RuntimeConfigurationWorkbenchPreviewSnapshot require(String previewId) {
        var now = clock.instant();
        var entry = previewId != null ? entries.get(previewId) : null;
        if (entry == null || !entry.expiresAt().isAfter(now)) {
            if (previewId != null) {
                entries.remove(previewId);
            }
            throw new RuntimeConfigurationWorkbenchPreviewNotFoundException();
        }
        return entry.snapshot();
    }

    private void prune(Instant now) {
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (entries.size() < maxEntries) {
            return;
        }
        entries.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
                .map(java.util.Map.Entry::getKey)
                .ifPresent(entries::remove);
    }

    record StoredPreview(
            String previewId,
            Instant expiresAt,
            RuntimeConfigurationWorkbenchPreviewSnapshot snapshot
    ) {
    }

    private record Entry(
            RuntimeConfigurationWorkbenchPreviewSnapshot snapshot,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
