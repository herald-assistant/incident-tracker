package pl.mkn.tdw.localworkspace.analysisruns.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import pl.mkn.tdw.common.JsonTextView;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only callbacks bound to one saved run, independent of an AI provider or feature. */
@RequiredArgsConstructor
public final class RunHttpHistoryTools {
    public static final String LIST = "run_http_history_list_entries";
    public static final String PATHS = "run_http_history_list_paths";
    public static final String READ = "run_http_history_read_value";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BYTES = 8_000;
    private static final int MAX_CONTENT = 4_000;
    private final LocalAnalysisRunStore runs;
    private final String runId;

    @Tool(name = LIST, description = "Czyta indeks zamrozonej historii HTTP biezacego runu: ID, method, pelny URL, czasy, state i status; bez body/naglowkow. Wynik jest fragmentem JSON. Kontynuuj przez nextOffset; wszystkie fragmenty skladaja sie w caly indeks.")
    public Map<String, Object> listEntries(
            @ToolParam(required = false, description = "Offset UTF-16 z nextOffset poprzedniej odpowiedzi; domyslnie 0.") Integer offset) {
        var snapshot = snapshot();
        if (snapshot == null) return error("unavailable", "HTTP history is unavailable in this run.");
        var summary = JsonNodeFactory.instance.arrayNode();
        int index = 0;
        for (var entry : snapshot) {
            var row = summary.addObject();
            row.put("index", index++); row.set("entryId", entry.get("id"));
            row.set("method", entry.path("request").get("method"));
            row.set("url", entry.path("request").get("url"));
            for (var name : new String[]{"startedAt", "durationMs", "state"}) row.set(name, entry.get(name));
            row.set("httpStatus", entry.path("response").path("status").isMissingNode()
                    ? JsonNodeFactory.instance.nullNode() : entry.path("response").path("status"));
        }
        return page(JsonTextView.pretty(summary), "json", "json", null, null, "", null, offset, null);
    }

    @Tool(name = PATHS, description = "Listuje dzieci JSON Pointer bez ich wartosci. Wybierz entryId oraz part (entry/requestBody/responseBody/requestHeaders/responseHeaders), albo pomin oba dla calej tablicy historii. JSON zapisany w body jako tekst jest interpretowany strukturalnie, jezeli odczyt jest bezstratny. Wynik JSON jest fragmentowany; kontynuuj przez nextOffset.")
    public Map<String, Object> listPaths(
            @ToolParam(required = false, description = "ID wpisu z indeksu, nie indeks tablicy.") Long entryId,
            @ToolParam(required = false, description = "entry, requestBody, responseBody, requestHeaders lub responseHeaders; z entryId domyslnie entry.") String part,
            @ToolParam(description = "JSON Pointer wzgledem wybranej czesci; pusty string oznacza korzen.") String pointer,
            @ToolParam(required = false, description = "Offset UTF-16 z nextOffset, domyslnie 0.") Integer offset) {
        var selection = select(entryId, part, pointer);
        if (selection.error != null) return selection.error;
        var node = selection.node;
        if (!node.isContainerNode()) return error("not_container", "Selected value has no children.");
        var paths = JsonNodeFactory.instance.arrayNode();
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                paths.addObject().put("pointer", pointer + "/" + escape(field.getKey()))
                        .put("type", field.getValue().getNodeType().name().toLowerCase())
                        .put("childCount", field.getValue().size());
            }
        } else {
            for (int i = 0; i < node.size(); i++) paths.addObject().put("pointer", pointer + "/" + i)
                    .put("type", node.get(i).getNodeType().name().toLowerCase()).put("childCount", node.get(i).size());
        }
        return page(JsonTextView.pretty(paths), "json", selection.sourceType, entryId, part, pointer, null, offset, null);
    }

    @Tool(name = READ, description = "Czyta konkretna wartosc lub podzbior zamrozonej historii HTTP. Gdy kod wskazuje pole body, wybierz entryId, part=responseBody/requestBody i JSON Pointer tego pola. JSON jest formatowany do linii. Duze wartosci i pojedyncze linie sa dzielone bez utraty danych; kontynuuj przez nextOffset przy niezmienionym wyborze. Nie wykonuje requestow HTTP.")
    public Map<String, Object> readValue(
            @ToolParam(required = false, description = "ID wpisu z indeksu, nie indeks tablicy.") Long entryId,
            @ToolParam(required = false, description = "entry, requestBody, responseBody, requestHeaders lub responseHeaders; z entryId domyslnie entry.") String part,
            @ToolParam(description = "JSON Pointer wzgledem wybranej czesci, np. /customer/permissions; pusty string oznacza korzen.") String pointer,
            @ToolParam(required = false, description = "Pierwsza linia 1-based; nie lacz z offset.") Integer startLine,
            @ToolParam(required = false, description = "Offset UTF-16 z nextOffset; nie lacz ze startLine. Domyslnie 0.") Integer offset,
            @ToolParam(required = false, description = "Maksymalna liczba linii; budzet bajtow moze wczesniej zakonczyc fragment.") Integer maxLines) {
        var selection = select(entryId, part, pointer);
        if (selection.error != null) return selection.error;
        var text = selection.plainText ? selection.node.textValue() : JsonTextView.pretty(selection.node);
        return page(text, selection.plainText ? "text" : "json", selection.sourceType,
                entryId, part, pointer, startLine, offset, maxLines);
    }

    private JsonNode snapshot() {
        try {
            return runs.findById(runId).map(record -> record.httpHistorySnapshot())
                    .filter(value -> value.available()).map(value -> (JsonNode) value.entries()).orElse(null);
        } catch (RuntimeException exception) { return null; }
    }

    private Selection select(Long entryId, String part, String pointer) {
        if (pointer == null || pointer.length() > 2048 || !pointer.matches("(?:/(?:[^~]|~[01])*)*"))
            return failed("invalid_pointer", "Use a valid JSON Pointer up to 2048 characters, or read the root in chunks.");
        var node = snapshot();
        if (node == null) return failed("unavailable", "HTTP history is unavailable in this run.");
        String sourceType = "json";
        boolean plainText = false;
        if (entryId == null && part != null) return failed("invalid_selection", "part requires entryId.");
        if (entryId != null) {
            JsonNode found = null;
            for (var entry : node) if (entry.path("id").longValue() == entryId) { found = entry; break; }
            if (found == null) return failed("not_found", "Entry not found.");
            var selectedPart = part == null ? "entry" : part;
            node = switch (selectedPart) {
                case "entry" -> found;
                case "requestBody" -> found.path("request").path("body");
                case "responseBody" -> found.path("response").path("body");
                case "requestHeaders" -> found.path("request").path("headers");
                case "responseHeaders" -> found.path("response").path("headers");
                default -> null;
            };
            if (node == null) return failed("invalid_selection", "Unsupported part.");
            if (node.isMissingNode()) return failed("unavailable", "The selected response or body was not captured.");
            if (selectedPart.endsWith("Body")) {
                sourceType = node.path("format").asText();
                if ("unavailable".equals(sourceType)) return failed("unavailable", node.path("reason").asText("BODY_SERIALIZATION_FAILED"));
                node = node.path("value");
                if ("text".equals(sourceType)) {
                    var parsed = JsonTextView.parse(node.textValue());
                    if (parsed.isPresent()) node = parsed.get();
                    else plainText = true;
                }
            }
        }
        if (plainText && !pointer.isEmpty()) return failed("not_json", "Body is plain text; read its root by lines or offsets.");
        try { node = node.at(pointer); }
        catch (IllegalArgumentException exception) { return failed("invalid_pointer", "Invalid JSON Pointer."); }
        if (node.isMissingNode()) return failed("not_found", "Path not found in the captured value.");
        return new Selection(node, plainText, sourceType, null);
    }

    private Map<String, Object> page(String text, String format, String sourceType, Long entryId, String part,
                                     String pointer, Integer startLine, Integer offset, Integer maxLines) {
        if (startLine != null && offset != null || startLine != null && startLine < 1 ||
                offset != null && offset < 0 || maxLines != null && maxLines < 1)
            return error("invalid_offset", "Select startLine or offset; line counts must be positive.");
        int totalLines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') totalLines++;
        int start = offset == null ? 0 : offset;
        if (startLine != null) {
            if (startLine > totalLines) return error("invalid_offset", "Line is outside the selected value.");
            int line = 1;
            while (line < startLine) if (text.charAt(start++) == '\n') line++;
        }
        if (start > text.length() || start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start)) && Character.isHighSurrogate(text.charAt(start - 1)))
            return error("invalid_offset", "Offset is outside the selected value or splits a Unicode character.");
        int upper = (int) Math.min(text.length(), (long) start + MAX_CONTENT);
        if (maxLines != null) {
            int lines = 0;
            for (int i = start; i < upper; i++) if (text.charAt(i) == '\n' && ++lines >= maxLines) { upper = i + 1; break; }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "ok"); result.put("entryId", entryId); result.put("part", part);
        result.put("pointer", pointer); result.put("format", format); result.put("sourceType", sourceType);
        result.put("startOffset", start); result.put("totalCharacters", text.length()); result.put("totalLines", totalLines);
        int firstLine = lineAt(text, start);
        result.put("startLine", firstLine);
        result.put("partialStartLine", start > 0 && text.charAt(start - 1) != '\n');
        int low = start, high = upper;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            fill(result, text, start, middle);
            if (size(result) <= MAX_BYTES) low = middle; else high = middle - 1;
        }
        int end = low;
        if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        if (end < text.length()) {
            int newline = text.lastIndexOf('\n', end - 1);
            if (newline >= start) end = newline + 1;
        }
        fill(result, text, start, end);
        if (size(result) > MAX_BYTES || end == start && start < text.length())
            return error("metadata_too_large", "Read using a shorter pointer or the root; metadata exceeds the response budget.");
        return result;
    }

    private void fill(Map<String, Object> result, String text, int start, int end) {
        result.put("content", text.substring(start, end)); result.put("nextOffset", end);
        result.put("hasMore", end < text.length());
        result.put("endLine", lineAt(text, end > start ? end - 1 : end));
        result.put("partialEndLine", end < text.length() && end > start && text.charAt(end - 1) != '\n');
    }
    private int lineAt(String text, int offset) { int line = 1; for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++; return line; }
    private int size(Object value) {
        try { return MAPPER.writeValueAsBytes(value).length; }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize HTTP history page", exception); }
    }
    private String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }
    private Map<String, Object> error(String status, String reason) { return Map.of("status", status, "reason", reason); }
    private Selection failed(String status, String reason) { return new Selection(null, false, null, error(status, reason)); }
    private record Selection(JsonNode node, boolean plainText, String sourceType, Map<String, Object> error) {}
}
