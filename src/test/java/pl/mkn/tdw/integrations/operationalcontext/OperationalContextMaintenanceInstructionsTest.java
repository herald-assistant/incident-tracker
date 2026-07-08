package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMaintenanceInstructionsTest {

    @Test
    void shouldKeepSystemMaintenancePromptFreeFromRepositoryReferences() throws IOException {
        var prompt = read("operational-context-maintenance/systems-yml-update-prompt.md");

        assertFalse(prompt.contains("references:\n      repositories:"));
        assertFalse(prompt.contains("repositories:\n        - customer-portal-ui"));
        assertTrue(prompt.contains("Systems do not reference repositories directly."));
        assertTrue(prompt.contains("code-search-scopes.yml"));
    }

    @Test
    void shouldDescribeCodeSearchScopeAsTheCanonicalPathFromSystemToCode() throws IOException {
        var fillOrder = read("operational-context-maintenance/operational-context-fill-order.md");
        var scopePrompt = read("operational-context-maintenance/code-search-scopes-yml-update-prompt.md");

        assertTrue(fillOrder.contains("Do not list repositories on a system."));
        assertTrue(fillOrder.contains("Repository navigation for a system goes"));
        assertTrue(scopePrompt.contains("This file is the canonical bridge between semantic context and code"));
        assertTrue(scopePrompt.contains("bounded context -> code-search scope -> repository -> path prefix -> code"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
