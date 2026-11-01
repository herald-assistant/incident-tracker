package pl.mkn.tdw.integrations.gitlab.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.CharacterCodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/** Bounded retries for idempotent GitLab reads, including response body consumption. */
@Slf4j
public final class GitLabReadRetry {
    private static final int MAX_ATTEMPTS = 3;
    private static final long MAX_RETRY_DELAY_MS = 120_000;
    private static final Set<Integer> TRANSIENT_HTTP = Set.of(408, 429, 500, 502, 503, 504);

    private GitLabReadRetry() {}

    public static <T> T execute(String operation, Supplier<T> read) {
        for (int attempt = 1; ; attempt++) {
            try {
                return read.get();
            } catch (RuntimeException failure) {
                long delay = retryDelay(failure, attempt);
                if (delay < 0 || Thread.currentThread().isInterrupted()) {
                    throw failure;
                }
                log.warn("Retrying GitLab read operation={} nextAttempt={} delayMs={} failureType={}",
                        operation, attempt + 1, delay, failure.getClass().getSimpleName());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    static long retryDelay(RuntimeException failure, int attempt) {
        if (attempt >= MAX_ATTEMPTS) return -1;
        long backoff = 500L * attempt;
        boolean ioFailure = false;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                if (!TRANSIENT_HTTP.contains(response.getStatusCode().value())) return -1;
                var headers = response.getResponseHeaders();
                long delay = Math.max(backoff, retryAfter(headers == null ? null : headers.getFirst("Retry-After")));
                return delay <= MAX_RETRY_DELAY_MS ? delay : -1;
            }
            if (cause instanceof SSLException || cause instanceof CharacterCodingException
                    || cause instanceof JsonProcessingException || cause instanceof InterruptedException
                    || cause instanceof InterruptedIOException && !(cause instanceof SocketTimeoutException)) {
                return -1;
            }
            ioFailure |= cause instanceof IOException;
        }
        return ioFailure ? backoff : -1;
    }

    private static long retryAfter(String value) {
        if (value == null || value.isBlank()) return 0;
        var trimmed = value.trim();
        if (trimmed.matches("[0-9]+")) {
            try {return Math.multiplyExact(Long.parseLong(trimmed), 1000L);}
            catch (NumberFormatException | ArithmeticException tooLong) {return Long.MAX_VALUE;}
        }
        try {
            return Math.max(0, Duration.between(Instant.now(),
                    ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());
        } catch (DateTimeParseException | ArithmeticException invalid) {
            return 0;
        }
    }
}
