package pl.mkn.incidenttracker.integrations.gitlab;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class GitLabRepositoryAnalysisCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;
    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public GitLabRepositoryAnalysisCache() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_ENTRIES);
    }

    GitLabRepositoryAnalysisCache(Clock clock, Duration ttl, int maxEntries) {
        this.clock = clock;
        this.ttl = ttl != null && !ttl.isNegative() && !ttl.isZero() ? ttl : DEFAULT_TTL;
        this.maxEntries = Math.max(1, maxEntries);
    }

    public <T> T getOrCompute(String namespace, List<?> keyParts, Supplier<T> loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        var key = cacheKey(namespace, keyParts);
        var now = clock.instant();
        var cached = entries.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.typedValue();
        }

        var value = loader.get();
        if (value == null) {
            entries.remove(key);
            return null;
        }

        pruneIfNeeded(now);
        entries.put(key, new CacheEntry(value, now, now.plus(ttl)));
        return value;
    }

    public void clear() {
        entries.clear();
    }

    private void pruneIfNeeded(Instant now) {
        if (entries.size() < maxEntries) {
            return;
        }

        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (entries.size() < maxEntries) {
            return;
        }

        var toRemove = entries.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
                .map(java.util.Map.Entry::getKey);
        toRemove.ifPresent(entries::remove);
    }

    private String cacheKey(String namespace, List<?> keyParts) {
        var safeNamespace = namespace != null ? namespace.trim() : "";
        var key = new StringBuilder(safeNamespace);
        for (var part : keyParts != null ? keyParts : List.of()) {
            key.append('|').append(part != null ? part.toString().trim() : "");
        }
        return key.toString();
    }

    private record CacheEntry(
            Object value,
            Instant createdAt,
            Instant expiresAt
    ) {
        @SuppressWarnings("unchecked")
        private <T> T typedValue() {
            return (T) value;
        }
    }
}
