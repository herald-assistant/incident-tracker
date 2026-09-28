package pl.mkn.tdw.localworkspace.analysisruns.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.util.ArrayList;
import java.util.List;

/** Read-only, feature-neutral access to the store snapshot embedded in a local run. */
public final class RunStoreTools {
    public static final String LIST = "run_store_list_paths";
    public static final String READ = "run_store_read_value";
    private static final int PAGE = 100;
    private static final int VALUE_CHARS = 12_000;
    private static final int PATH_CHARS = 4_000;
    private final LocalAnalysisRunStore runs;

    public RunStoreTools(LocalAnalysisRunStore runs) {
        this.runs = runs;
    }

    @Tool(name = LIST, description = "Listuje klucze lub indeksy pod JSON Pointer w store zapisanym w run.json wskazanego runu.")
    public PathPage listPaths(
            @ToolParam(description = "ID runu zawierajacego store snapshot.") String runId,
            @ToolParam(description = "JSON Pointer; pusty string oznacza korzen store.") String pointer,
            @ToolParam(required = false, description = "Indeks pierwszego dziecka, domyslnie 0.") Integer offset
    ) {
        var node = resolve(runId, pointer);
        if (node == null) return new PathPage("unavailable", pointer, List.of(), 0, false);
        if (node.isMissingNode()) return new PathPage("not_found", pointer, List.of(), 0, false);
        if (!node.isContainerNode()) return new PathPage("not_container", pointer, List.of(), 0, false);
        var start = Math.max(0, offset != null ? offset : 0);
        var children = new ArrayList<PathEntry>();
        var pathChars = 0;
        if (node.isObject()) {
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (var i = start; i < Math.min(names.size(), start + PAGE); i++) {
                var name = names.get(i);
                var child = node.get(name);
                var childPointer = append(pointer, name);
                if (childPointer.length() > PATH_CHARS) {
                    if (children.isEmpty()) return new PathPage("path_too_long", pointer, List.of(), node.size(), false);
                    break;
                }
                if (pathChars + childPointer.length() + 64 > PATH_CHARS) break;
                children.add(new PathEntry(childPointer, kind(child), child.size()));
                pathChars += childPointer.length() + 64;
            }
        } else {
            for (var i = start; i < Math.min(node.size(), start + PAGE); i++) {
                var child = node.get(i);
                var childPointer = append(pointer, Integer.toString(i));
                if (pathChars + childPointer.length() + 64 > PATH_CHARS) break;
                children.add(new PathEntry(childPointer, kind(child), child.size()));
                pathChars += childPointer.length() + 64;
            }
        }
        return new PathPage("ok", pointer, children, node.size(), start + children.size() < node.size());
    }

    @Tool(name = READ, description = "Czyta wartosc JSON Pointer ze store w run.json. Dlugie JSON zwraca we fragmentach po 12000 znakow.")
    public ValuePage readValue(
            @ToolParam(description = "ID runu zawierajacego store snapshot.") String runId,
            @ToolParam(description = "JSON Pointer; pusty string oznacza korzen store.") String pointer,
            @ToolParam(required = false, description = "Offset znakowy kolejnego fragmentu JSON, domyslnie 0.") Integer offset
    ) {
        var node = resolve(runId, pointer);
        if (node == null) return new ValuePage("unavailable", pointer, null, 0, 0, false);
        if (node.isMissingNode()) return new ValuePage("not_found", pointer, null, 0, 0, false);
        var serialized = node.toString();
        var start = Math.max(0, offset != null ? offset : 0);
        if (start > serialized.length()) return new ValuePage("invalid_offset", pointer, null, start, serialized.length(), false);
        var end = Math.min(serialized.length(), start + VALUE_CHARS);
        return new ValuePage("ok", pointer, serialized.substring(start, end), end, serialized.length(), end < serialized.length());
    }

    private JsonNode resolve(String runId, String pointer) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]{1,128}")
                || pointer == null || pointer.length() > 2048 || !pointer.isEmpty() && !pointer.startsWith("/")) return null;
        try {
            return runs.findById(runId)
                    .map(record -> record.storeSnapshot())
                    .map(snapshot -> snapshot.state().at(pointer))
                    .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String append(String pointer, String child) {
        return pointer + "/" + child.replace("~", "~0").replace("/", "~1");
    }

    private String kind(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "null";
    }

    public record PathEntry(String pointer, String type, int childCount) {}
    public record PathPage(String status, String pointer, List<PathEntry> children, int totalChildren, boolean hasMore) {}
    public record ValuePage(String status, String pointer, String jsonFragment, int nextOffset, int totalCharacters, boolean hasMore) {}
}
