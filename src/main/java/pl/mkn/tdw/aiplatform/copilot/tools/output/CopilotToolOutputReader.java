package pl.mkn.tdw.aiplatform.copilot.tools.output;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolResultObject;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class CopilotToolOutputReader {

    public static final String TOOL_NAME = "tool_output_read_chunk";
    static final int MAX_RESPONSE_BYTES = 8_192;
    static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_POINTER_CHARACTERS = 1_024;
    private static final String TEXT_FALLBACK = "Read the same file as text without jsonPointer and follow nextCursor.";
    private static final String GUIDANCE = "When a tool result says it was saved to a file, use tool_output_read_chunk "
            + "with that exact path. Follow nextCursor for more text or supply jsonPointer for a known JSON value.";

    private final CopilotSdkProperties properties;
    private final ObjectMapper mapper;
    private final ObjectReader jsonReader;

    public CopilotToolOutputReader(CopilotSdkProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        var parser = mapper.copy();
        parser.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(128).build());
        jsonReader = parser.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public Path prepareDirectory() {
        var directory = properties.resolvedToolOutputDirectory();
        try {
            Files.createDirectories(directory.getParent());
            if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectories(directory);
            }
            if (!safeAttributes(directory).isDirectory() || !directory.equals(directory.toRealPath())) {
                throw new IOException("Tool output directory resolves outside its configured path");
            }
            return directory;
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException("Cannot prepare the Copilot tool output directory.", exception);
        }
    }

    public static String instructions(String featureInstructions) {
        return featureInstructions == null || featureInstructions.isBlank()
                ? GUIDANCE : featureInstructions + "\n\n" + GUIDANCE;
    }

    public List<ToolDefinition> withDefinition(List<ToolDefinition> tools) {
        var definitions = new ArrayList<>(tools);
        definitions.add(ToolDefinition.createSkipPermission(TOOL_NAME,
                "Read a saved Copilot tool result from the configured output directory.",
                Map.of("type", "object", "required", List.of("path"), "additionalProperties", false,
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Absolute path provided in the tool result."),
                                "cursor", Map.of("type", "string", "description", "nextCursor from the previous text chunk."),
                                "jsonPointer", Map.of("type", "string", "description", "RFC 6901 pointer. Omit cursor."))),
                invocation -> {
                    var arguments = invocation.getArguments();
                    return CompletableFuture.completedFuture(ToolResultObject.success(read(arguments == null ? Map.of() : arguments)));
                }));
        return List.copyOf(definitions);
    }

    String read(Map<String, Object> arguments) {
        var path = arguments.get("path");
        var cursor = arguments.get("cursor");
        var pointer = arguments.get("jsonPointer");
        if (!(path instanceof String filePath) || cursor != null && !(cursor instanceof String)
                || pointer != null && !(pointer instanceof String) || cursor != null && pointer != null) {
            return error("INVALID_ARGUMENTS", "Provide path and at most one of cursor and jsonPointer.");
        }
        try (var file = open(filePath)) {
            return pointer instanceof String jsonPointer ? selectJson(file, jsonPointer) : readText(file, (String) cursor);
        } catch (IOException | InvalidPathException | SecurityException exception) {
            return error("OUTPUT_UNAVAILABLE", "File unavailable. If needed, repeat the original allowed read and use its new result.");
        }
    }

    private FileChannel open(String input) throws IOException {
        var root = properties.resolvedToolOutputDirectory();
        var path = Path.of(input);
        if (!path.isAbsolute() || !path.equals(path.normalize()) || !path.startsWith(root) || path.equals(root)
                || !safeAttributes(root).isDirectory() || !root.equals(root.toRealPath())) {
            throw new IOException("Path outside the tool output directory");
        }
        var current = root;
        for (var segment : root.relativize(path)) {
            if (segment.toString().contains(":")) {
                throw new IOException("Unsupported file path");
            }
            current = current.resolve(segment);
            var attributes = safeAttributes(current);
            if (current.equals(path) ? !attributes.isRegularFile() : !attributes.isDirectory()) {
                throw new IOException("Unsupported file type");
            }
        }
        if (!path.equals(path.toRealPath())) {
            throw new IOException("Path resolves to a different file");
        }
        return FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    private static BasicFileAttributes safeAttributes(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Unsupported file type");
        }
        return attributes;
    }

    private String readText(FileChannel file, String cursor) throws IOException {
        final long offset;
        try {
            offset = cursor == null ? 0 : Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            return error("INVALID_CURSOR", "Use nextCursor from the previous text chunk.");
        }
        if (offset < 0 || offset > file.size()) {
            return error("INVALID_CURSOR", "Use nextCursor from the previous text chunk.");
        }
        file.position(offset);
        var input = ByteBuffer.wrap(Channels.newInputStream(file).readNBytes(MAX_RESPONSE_BYTES));
        var end = file.position() >= file.size();
        var decoded = CharBuffer.allocate(input.remaining());
        var result = StandardCharsets.UTF_8.newDecoder().decode(input, decoded, end);
        if (result.isError()) {
            return error("INVALID_UTF8", "The file or requested offset does not contain readable UTF-8 text.");
        }
        var text = decoded.flip().toString();
        var complete = end && !input.hasRemaining();
        var response = textResponse(text, complete ? null : offset + input.position());
        if (response.length <= MAX_RESPONSE_BYTES) {
            return asString(response);
        }

        // JSON escaping can make a page larger than its source bytes. Find the longest fitting Unicode prefix.
        int low = 1;
        int high = text.codePointCount(0, text.length()) - 1;
        byte[] page = null;
        while (low <= high) {
            int middle = (low + high) / 2;
            var prefix = text.substring(0, text.offsetByCodePoints(0, middle));
            var candidate = textResponse(prefix, offset + prefix.getBytes(StandardCharsets.UTF_8).length);
            if (candidate.length <= MAX_RESPONSE_BYTES) {
                page = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return page != null ? asString(page) : error("OUTPUT_LIMIT_EXCEEDED", "The text page could not fit the response limit.");
    }

    private String selectJson(FileChannel file, String pointer) throws IOException {
        if (pointer.length() > MAX_POINTER_CHARACTERS || !pointer.isEmpty() && !pointer.startsWith("/")
                || pointer.matches("(?s).*~(?![01]).*")) {
            return error("INVALID_JSON_POINTER", "Provide an RFC 6901 pointer or read as text without jsonPointer.");
        }
        var bytes = Channels.newInputStream(file).readNBytes(MAX_JSON_BYTES + 1);
        if (bytes.length > MAX_JSON_BYTES) {
            return error("JSON_LIMIT_EXCEEDED", TEXT_FALLBACK);
        }
        final JsonNode document;
        try {
            document = jsonReader.readTree(bytes);
        } catch (StreamConstraintsException exception) {
            return error("JSON_LIMIT_EXCEEDED", TEXT_FALLBACK);
        } catch (IOException exception) {
            return error("INVALID_JSON", TEXT_FALLBACK);
        }
        if (document == null || document.isMissingNode()) {
            return error("INVALID_JSON", TEXT_FALLBACK);
        }
        var value = document.at(JsonPointer.compile(pointer));
        if (value.isMissingNode()) {
            return error("JSON_VALUE_NOT_FOUND", "Check the pointer. " + TEXT_FALLBACK);
        }
        var response = mapper.createObjectNode().put("status", "OK");
        response.set("value", value);
        var serialized = mapper.writeValueAsBytes(response);
        return serialized.length <= MAX_RESPONSE_BYTES ? asString(serialized)
                : error("JSON_VALUE_TOO_LARGE", "Choose a more specific jsonPointer. " + TEXT_FALLBACK);
    }

    private byte[] textResponse(String text, Long next) throws IOException {
        ObjectNode response = mapper.createObjectNode().put("status", "OK").put("content", text);
        if (next != null) {
            response.put("nextCursor", next.toString());
        }
        return mapper.writeValueAsBytes(response);
    }

    private String error(String status, String message) {
        return mapper.createObjectNode().put("status", status).put("message", message).toString();
    }

    private String asString(byte[] serialized) {
        return new String(serialized, StandardCharsets.UTF_8);
    }
}
