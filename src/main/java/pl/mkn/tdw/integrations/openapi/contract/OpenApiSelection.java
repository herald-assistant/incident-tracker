package pl.mkn.tdw.integrations.openapi.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class OpenApiSelection {
    public static final int MAX_SOURCE_CHARACTERS = 2_000_000;
    public static final int MAX_DOCUMENT_CHARACTERS = 1_000_000;
    public static final int MAX_TOOL_SCHEMA_CHARACTERS = 250_000;
    public static final int MAX_CATALOG_SCHEMA_CHARACTERS = 1_000_000;
    public static final int MAX_CANDIDATES = 5_000;
    public static final int MAX_SELECTED = 60;
    private OpenApiSelection() {}

    public record OperationKey(String method, String path) {}
    public record Candidate(String method, String path, String operationId, String summary,
                            List<String> tags, String unavailableReason, List<String> warnings) {
        public Candidate { tags = List.copyOf(tags); warnings = List.copyOf(warnings); }
        public OperationKey key() { return new OperationKey(method, path); }
    }
    public record Preview(String title, List<Candidate> operations, int maxSelected, JsonNode specification) {
        public Preview { operations = List.copyOf(operations); specification = specification.deepCopy(); }
        @Override public JsonNode specification() { return specification.deepCopy(); }
    }
    public record Prepared(String yaml, int operationCount) {}
    public record Document(JsonNode specification) {
        public Document { specification = specification.deepCopy(); }
        @Override public JsonNode specification() { return specification.deepCopy(); }
    }
}
