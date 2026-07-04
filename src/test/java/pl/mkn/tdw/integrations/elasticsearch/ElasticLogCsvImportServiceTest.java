package pl.mkn.tdw.integrations.elasticsearch;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticLogCsvImportServiceTest {

    private final ElasticLogCsvImportService service = new ElasticLogCsvImportService();

    @Test
    void shouldImportKibanaDiscoverCsvWithMultilineStacktrace() {
        var result = service.importCsv(new StringReader("""
                "@timestamp","_id","_ignored","_index","fields.class","fields.correlationId","fields.exception","fields.message","fields.microservice","fields.spanId","fields.thread","fields.type","kubernetes.namespace","kubernetes.pod.name","kubernetes.container.name","container.image.name"
                "Jul 4, 2026 @ 10:57:36.860","doc-1","-","logs-2026","pl.example.RequestLoggingFilter","corr-1","(blank)","After method: GET /clp/process/limit/0661035358 | Ended with status: 404","corpo-lending-platform","span-1","https-jsse-nio-8443-exec-5","INFO","clp-main-dev4","backend-56f97976b6-g794x","backend","registry.example/clp-main-dev4/backend:20260630-094920-327-dev-sigma-0b9faa0cea1c88ffdb819cad739750c9729a8b0a"
                "2026-07-04T08:57:37.001Z","doc-2","fields.exception","logs-2026","pl.example.LimitProcessApiExceptionHandler","corr-1","java.lang.IllegalStateException: boom
                \tat pl.example.App.run(App.java:10)","Failed, with comma","corpo-lending-platform","span-2","https-jsse-nio-8443-exec-6","ERROR","clp-main-dev4","backend-56f97976b6-g794x","backend","registry.example/clp-main-dev4/backend:20260630-094920-327-dev-sigma-0b9faa0cea1c88ffdb819cad739750c9729a8b0a"
                """));

        assertEquals("corr-1", result.correlationId());
        assertEquals(2, result.importedRecords());
        assertEquals(2, result.entries().size());

        var first = result.entries().get(0);
        assertEquals(expectedKibanaInstant("2026-07-04T10:57:36.860"), first.timestamp());
        assertEquals("INFO", first.level());
        assertEquals("corpo-lending-platform", first.serviceName());
        assertNull(first.exception());
        assertEquals("logs-2026", first.indexName());
        assertEquals("doc-1", first.documentId());
        assertFalse(first.exceptionTruncated());

        var second = result.entries().get(1);
        assertEquals("2026-07-04T08:57:37.001Z", second.timestamp());
        assertEquals("ERROR", second.level());
        assertTrue(second.message().contains("Failed, with comma"));
        assertTrue(second.exception().contains("java.lang.IllegalStateException: boom"));
        assertTrue(second.exception().contains("\tat pl.example.App.run"));
        assertTrue(second.exceptionTruncated());
        assertFalse(second.messageTruncated());
    }

    @Test
    void shouldTreatBlankAndDashValuesAsNull() {
        var result = service.importCsv(new StringReader(header() + "\n"
                + row("2026-07-04T08:57:36.860Z", "corr-1", "-", "(blank)", "-", "-", "-")));

        var entry = result.entries().get(0);

        assertNull(entry.level());
        assertNull(entry.message());
        assertNull(entry.exception());
        assertNull(entry.thread());
        assertNull(entry.spanId());
    }

    @Test
    void shouldRejectMissingRequiredColumns() {
        var exception = assertThrows(
                ElasticLogCsvImportException.class,
                () -> service.importCsv(new StringReader("""
                        "@timestamp","fields.correlationId"
                        "2026-07-04T08:57:36.860Z","corr-1"
                        """))
        );

        assertEquals(ElasticLogCsvImportException.Reason.MISSING_COLUMNS, exception.reason());
        assertTrue(exception.details().contains("fields.type"));
        assertTrue(exception.details().contains("container.image.name"));
    }

    @Test
    void shouldRejectEmptyRecordSet() {
        var exception = assertThrows(
                ElasticLogCsvImportException.class,
                () -> service.importCsv(new StringReader(header() + "\n"))
        );

        assertEquals(ElasticLogCsvImportException.Reason.EMPTY, exception.reason());
    }

    @Test
    void shouldRejectMultipleCorrelationIds() {
        var csv = header() + "\n"
                + row("2026-07-04T08:57:36.860Z", "corr-1", "INFO", "first", "-", "thread-1", "span-1")
                + row("2026-07-04T08:57:37.860Z", "corr-2", "INFO", "second", "-", "thread-2", "span-2");

        var exception = assertThrows(
                ElasticLogCsvImportException.class,
                () -> service.importCsv(new StringReader(csv))
        );

        assertEquals(ElasticLogCsvImportException.Reason.MULTIPLE_CORRELATION_IDS, exception.reason());
        assertTrue(exception.details().contains("corr-1"));
        assertTrue(exception.details().contains("corr-2"));
    }

    @Test
    void shouldRejectInvalidTimestamp() {
        var exception = assertThrows(
                ElasticLogCsvImportException.class,
                () -> service.importCsv(new StringReader(header() + "\n"
                        + row("not-a-timestamp", "corr-1", "INFO", "message", "-", "thread-1", "span-1")))
        );

        assertEquals(ElasticLogCsvImportException.Reason.INVALID_TIMESTAMP, exception.reason());
        assertTrue(exception.getMessage().contains("row 2"));
    }

    @Test
    void shouldRejectInvalidCsvSyntax() {
        var exception = assertThrows(
                ElasticLogCsvImportException.class,
                () -> service.importCsv(new StringReader(header() + "\n\"unterminated"))
        );

        assertEquals(ElasticLogCsvImportException.Reason.INVALID_CSV, exception.reason());
    }

    private String expectedKibanaInstant(String localDateTime) {
        return LocalDateTime.parse(localDateTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toString();
    }

    private String header() {
        return "\"@timestamp\",\"fields.correlationId\",\"fields.type\",\"fields.microservice\",\"fields.class\","
                + "\"fields.message\",\"fields.exception\",\"fields.thread\",\"fields.spanId\","
                + "\"kubernetes.namespace\",\"kubernetes.pod.name\",\"kubernetes.container.name\","
                + "\"container.image.name\"";
    }

    private String row(
            String timestamp,
            String correlationId,
            String level,
            String message,
            String exception,
            String thread,
            String spanId
    ) {
        return "\"%s\",\"%s\",\"%s\",\"test-service\",\"pl.example.TestClass\",\"%s\",\"%s\",\"%s\",\"%s\","
                .formatted(timestamp, correlationId, level, message, exception, thread, spanId)
                + "\"test-dev1\",\"test-pod\",\"test-container\",\"registry.example/test-dev1/test:tag\"\n";
    }
}
