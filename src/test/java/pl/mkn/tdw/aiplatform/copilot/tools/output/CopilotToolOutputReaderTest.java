package pl.mkn.tdw.aiplatform.copilot.tools.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CopilotToolOutputReaderTest {

    @TempDir
    Path workspace;

    private final ObjectMapper mapper = new ObjectMapper();
    private CopilotToolOutputReader reader;
    private Path directory;

    @BeforeEach
    void prepare() {
        var properties = new CopilotSdkProperties();
        properties.setCopilotHome(workspace.resolve("crm-copilot").toString());
        reader = new CopilotToolOutputReader(properties, mapper);
        directory = reader.prepareDirectory();
    }

    @Test
    void reconstructsUtf8AndEscapedTextWithinTheSerializedByteLimit() throws Exception {
        var content = "CRM: Żółć 🧾 \u0000\n\r\"\\".repeat(5_000) + "CRM_END";
        var path = Files.writeString(directory.resolve("crm-output.txt"), content);
        var arguments = new HashMap<String, Object>();
        arguments.put("path", path.toString());
        var combined = new StringBuilder();
        for (int page = 0; page < 100; page++) {
            var response = read(arguments);
            assertThat(response.path("status").asText()).isEqualTo("OK");
            combined.append(response.path("content").asText());
            if (!response.has("nextCursor")) {
                assertThat(combined.toString()).isEqualTo(content);
                return;
            }
            arguments.put("cursor", response.path("nextCursor").asText());
        }
        throw new AssertionError("CRM output did not reach EOF");
    }

    @Test
    void selectsJsonValuesAndAllowsTextFallback() throws Exception {
        var path = Files.writeString(directory.resolve("crm.json"), """
                {"contacts":[{"status":"active","followUp":null,"region/team":"CRM"}],"note":"%s"}
                """.formatted("CRM ".repeat(4_000)));
        assertThat(select(path, "/contacts/0/status").path("value").asText()).isEqualTo("active");
        assertThat(select(path, "/contacts/0/followUp").path("value").isNull()).isTrue();
        assertThat(select(path, "/contacts/0/region~1team").path("value").asText()).isEqualTo("CRM");
        assertThat(select(path, "/contacts/0/unknown").path("status").asText()).isEqualTo("JSON_VALUE_NOT_FOUND");
        assertThat(select(path, "/contacts/0/~2").path("status").asText()).isEqualTo("INVALID_JSON_POINTER");
        assertThat(select(path, "/note").path("status").asText()).isEqualTo("JSON_VALUE_TOO_LARGE");
        assertThat(read(Map.of("path", path.toString())).path("nextCursor").isTextual()).isTrue();

        Files.writeString(path, "{\"contacts\":");
        assertThat(select(path, "/contacts").path("status").asText()).isEqualTo("INVALID_JSON");
        assertThat(read(Map.of("path", path.toString())).path("content").asText()).isEqualTo("{\"contacts\":");

        Files.writeString(path, "[".repeat(150) + "null" + "]".repeat(150));
        assertThat(select(path, "").path("status").asText()).isEqualTo("JSON_LIMIT_EXCEEDED");
        Files.writeString(path, " ".repeat(CopilotToolOutputReader.MAX_JSON_BYTES + 1));
        assertThat(select(path, "").path("status").asText()).isEqualTo("JSON_LIMIT_EXCEEDED");
    }

    @Test
    void confinesReadToRegularFilesUnderTheSharedDirectory() throws Exception {
        var inside = Files.writeString(directory.resolve("another-crm-session.txt"), "CRM output");
        var outside = Files.writeString(workspace.resolve("crm-outside.txt"), "Outside the CRM output directory");
        assertThat(read(Map.of("path", inside.toString())).path("content").asText()).isEqualTo("CRM output");
        assertThat(read(Map.of("path", inside.toString(), "cursor", "-1")).path("status").asText())
                .isEqualTo("INVALID_CURSOR");
        for (var path : new Path[] {outside, directory, directory.resolve("../tool-outputs/another-crm-session.txt"),
                directory.resolve("missing-crm.txt")}) {
            assertUnavailable(path);
        }
    }

    @Test
    void rejectsSymbolicLinks() throws Exception {
        var outside = Files.writeString(workspace.resolve("crm-outside.txt"), "Outside the CRM output directory");
        var link = directory.resolve("crm-link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getClass().getSimpleName());
        }
        assertUnavailable(link);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsWindowsJunctions() throws Exception {
        var outside = Files.createDirectory(workspace.resolve("crm-outside"));
        Files.writeString(outside.resolve("crm.txt"), "Outside the CRM output directory");
        var junction = directory.resolve("crm-junction");
        var mklink = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", junction.toString(), outside.toString())
                .redirectErrorStream(true).start();
        assertThat(mklink.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(mklink.exitValue()).isZero();
        try {
            assertUnavailable(junction.resolve("crm.txt"));
        } finally {
            Files.delete(junction);
        }
    }

    private void assertUnavailable(Path path) throws Exception {
        assertThat(read(Map.of("path", path.toString())).path("status").asText()).isEqualTo("OUTPUT_UNAVAILABLE");
    }

    private JsonNode select(Path path, String pointer) throws Exception {
        return read(Map.of("path", path.toString(), "jsonPointer", pointer));
    }

    private JsonNode read(Map<String, Object> arguments) throws Exception {
        var serialized = reader.read(arguments);
        assertThat(serialized.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8_192);
        return mapper.readTree(serialized);
    }
}
