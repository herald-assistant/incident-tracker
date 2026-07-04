package pl.mkn.tdw.integrations.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ElasticLogCsvImportService {

    public static final List<String> REQUIRED_COLUMNS = List.of(
            "@timestamp",
            "fields.correlationId",
            "fields.type",
            "fields.microservice",
            "fields.class",
            "fields.message",
            "fields.exception",
            "fields.thread",
            "fields.spanId",
            "kubernetes.namespace",
            "kubernetes.pod.name",
            "kubernetes.container.name",
            "container.image.name"
    );

    private static final String INDEX_COLUMN = "_index";
    private static final String ID_COLUMN = "_id";
    private static final String IGNORED_COLUMN = "_ignored";
    private static final CsvSchema HEADER_SCHEMA = CsvSchema.emptySchema();
    private static final CsvSchema DATA_SCHEMA = CsvSchema.emptySchema().withHeader();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final DateTimeFormatter KIBANA_DISPLAY_TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d, uuuu")
            .appendLiteral(" @ ")
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ENGLISH);

    private final CsvMapper csvMapper;
    private final ZoneId defaultZoneId;

    public ElasticLogCsvImportService() {
        this(CsvMapper.builder().enable(CsvParser.Feature.WRAP_AS_ARRAY).build(), ZoneId.systemDefault());
    }

    ElasticLogCsvImportService(CsvMapper csvMapper, ZoneId defaultZoneId) {
        this.csvMapper = csvMapper;
        this.defaultZoneId = defaultZoneId;
    }

    public ElasticLogCsvImportResult importCsv(InputStream inputStream) {
        return importCsv(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    public ElasticLogCsvImportResult importCsv(Reader reader) {
        var csvText = readAll(reader);
        if (!StringUtils.hasText(csvText)) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.MISSING_HEADER,
                    "CSV file must contain a header row."
            );
        }

        var headers = parseHeaders(stripBom(csvText));
        validateRequiredColumns(headers);
        return parseRows(stripBom(csvText));
    }

    private String readAll(Reader reader) {
        try (reader) {
            var builder = new StringBuilder();
            var buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException exception) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.INVALID_CSV,
                    "CSV file could not be read.",
                    exception
            );
        }
    }

    private List<String> parseHeaders(String csvText) {
        try (MappingIterator<List<String>> iterator = csvMapper
                .readerFor(STRING_LIST)
                .with(HEADER_SCHEMA)
                .readValues(csvText)) {
            if (!iterator.hasNextValue()) {
                throw new ElasticLogCsvImportException(
                        ElasticLogCsvImportException.Reason.MISSING_HEADER,
                        "CSV file must contain a header row."
                );
            }

            var headers = iterator.nextValue()
                    .stream()
                    .map(this::normalizeHeader)
                    .toList();

            if (headers.stream().noneMatch(StringUtils::hasText)) {
                throw new ElasticLogCsvImportException(
                        ElasticLogCsvImportException.Reason.MISSING_HEADER,
                        "CSV file must contain a non-empty header row."
                );
            }

            return headers;
        } catch (JsonProcessingException exception) {
            throw invalidCsv(exception);
        } catch (IOException exception) {
            throw invalidCsv(exception);
        }
    }

    private ElasticLogCsvImportResult parseRows(String csvText) {
        var entries = new ArrayList<ElasticLogEntry>();
        var correlationIds = new LinkedHashSet<String>();
        var rowNumber = 1;

        try (MappingIterator<LinkedHashMap<String, String>> iterator = csvMapper
                .readerFor(STRING_MAP)
                .with(DATA_SCHEMA)
                .readValues(csvText)) {
            while (iterator.hasNextValue()) {
                rowNumber++;
                var row = iterator.nextValue();
                validateRowShape(row, rowNumber);

                var correlationId = value(row, "fields.correlationId");
                if (StringUtils.hasText(correlationId)) {
                    correlationIds.add(correlationId);
                }

                entries.add(mapEntry(row, rowNumber));
            }
        } catch (JsonProcessingException exception) {
            throw invalidCsv(exception);
        } catch (IOException exception) {
            throw invalidCsv(exception);
        }

        if (entries.isEmpty()) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.EMPTY,
                    "CSV file must contain at least one log record."
            );
        }
        if (correlationIds.isEmpty()) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.MISSING_CORRELATION_ID,
                    "CSV file must contain at least one non-empty fields.correlationId value."
            );
        }
        if (correlationIds.size() > 1) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.MULTIPLE_CORRELATION_IDS,
                    "CSV file contains logs for multiple correlation ids.",
                    List.copyOf(correlationIds)
            );
        }

        return new ElasticLogCsvImportResult(correlationIds.iterator().next(), entries, entries.size());
    }

    private void validateRequiredColumns(List<String> headers) {
        var present = new LinkedHashSet<>(headers);
        var missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !present.contains(column))
                .toList();

        if (!missing.isEmpty()) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.MISSING_COLUMNS,
                    "CSV file is missing required Kibana Discover columns: " + String.join(", ", missing) + ".",
                    missing
            );
        }
    }

    private void validateRowShape(Map<String, String> row, int rowNumber) {
        if (row.containsKey(null)) {
            throw new ElasticLogCsvImportException(
                    ElasticLogCsvImportException.Reason.INVALID_CSV,
                    "CSV row " + rowNumber + " has more values than the header row."
            );
        }
    }

    private ElasticLogEntry mapEntry(Map<String, String> row, int rowNumber) {
        var ignored = value(row, IGNORED_COLUMN);
        return new ElasticLogEntry(
                normalizeTimestamp(value(row, "@timestamp"), rowNumber),
                value(row, "fields.type"),
                value(row, "fields.microservice"),
                value(row, "fields.class"),
                value(row, "fields.message"),
                value(row, "fields.exception"),
                value(row, "fields.thread"),
                value(row, "fields.spanId"),
                value(row, "kubernetes.namespace"),
                value(row, "kubernetes.pod.name"),
                value(row, "kubernetes.container.name"),
                value(row, "container.image.name"),
                value(row, INDEX_COLUMN),
                value(row, ID_COLUMN),
                ignoredContains(ignored, "fields.message"),
                ignoredContains(ignored, "fields.exception")
        );
    }

    private String normalizeTimestamp(String value, int rowNumber) {
        if (!StringUtils.hasText(value)) {
            throw invalidTimestamp(value, rowNumber);
        }

        try {
            return Instant.parse(value).toString();
        } catch (RuntimeException ignored) {
            // Try the next supported timestamp shape.
        }

        try {
            return OffsetDateTime.parse(value).toInstant().toString();
        } catch (RuntimeException ignored) {
            // Try the next supported timestamp shape.
        }

        try {
            return ZonedDateTime.parse(value).toInstant().toString();
        } catch (RuntimeException ignored) {
            // Try the next supported timestamp shape.
        }

        try {
            return LocalDateTime.parse(value, KIBANA_DISPLAY_TIMESTAMP_FORMAT)
                    .atZone(defaultZoneId)
                    .toInstant()
                    .toString();
        } catch (RuntimeException ignored) {
            throw invalidTimestamp(value, rowNumber);
        }
    }

    private ElasticLogCsvImportException invalidTimestamp(String value, int rowNumber) {
        return new ElasticLogCsvImportException(
                ElasticLogCsvImportException.Reason.INVALID_TIMESTAMP,
                "CSV row " + rowNumber + " contains an invalid @timestamp value: " + value + "."
        );
    }

    private ElasticLogCsvImportException invalidCsv(Exception exception) {
        return new ElasticLogCsvImportException(
                ElasticLogCsvImportException.Reason.INVALID_CSV,
                "CSV file could not be parsed as valid CSV.",
                exception
        );
    }

    private String normalizeHeader(String value) {
        return stripBom(value).trim();
    }

    private String value(Map<String, String> row, String column) {
        var rawValue = row.get(column);
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        var trimmed = rawValue.trim();
        if ("(blank)".equalsIgnoreCase(trimmed) || "-".equals(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private boolean ignoredContains(String ignored, String fieldName) {
        return StringUtils.hasText(ignored) && ignored.contains(fieldName);
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }
}
