package pl.mkn.tdw.integrations.gitlab.internal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class GitLabReadRetryDelayTest {
    @Test void honorsRateLimitWindowsUpToTwoMinutesWithoutAnEarlyRetry() {
        assertEquals(60_000, GitLabReadRetry.retryDelay(rateLimited("60"), 1));
        assertEquals(120_000, GitLabReadRetry.retryDelay(rateLimited("120"), 2));
        assertEquals(-1, GitLabReadRetry.retryDelay(rateLimited("121"), 1));
        assertEquals(-1, GitLabReadRetry.retryDelay(rateLimited("60"), 3));
    }

    @Test void honorsHttpDateAndRejectsOverflowingDelay() {
        var reset = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        var delay = GitLabReadRetry.retryDelay(rateLimited(reset), 1);
        assertTrue(delay > 50_000 && delay <= 60_000);
        assertEquals(-1, GitLabReadRetry.retryDelay(rateLimited("99999999999999999999999999"), 1));
    }

    private RuntimeException rateLimited(String retryAfter) {
        var headers = new HttpHeaders();
        headers.set("Retry-After", retryAfter);
        return new RestClientResponseException("CRM rate limit", 429, "Too Many Requests", headers, null, null);
    }
}
