package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceCopilotAccessTokenResolverTest {

    @Test
    void requiresFineGrainedPatAndUsesCurrentWorkspaceValue() {
        var value = new AtomicReference<String>();
        var resolver = new WorkspaceCopilotAccessTokenResolver(value::get);

        assertThrows(CopilotLocalTokenMissingException.class,
                () -> resolver.resolve(CopilotRunAuth.localToken()));

        value.set("ghp_crm_classic_token");
        assertThrows(CopilotPatInvalidException.class,
                () -> resolver.resolve(CopilotRunAuth.localToken()));

        value.set("  github_pat_crm_first_token  ");
        var first = resolver.resolve(CopilotRunAuth.localToken());
        assertEquals("github_pat_crm_first_token", first.value());
        assertTrue(first.userBound());

        value.set("github_pat_crm_rotated_token");
        var rotated = resolver.resolve(CopilotRunAuth.localToken());
        assertEquals("github_pat_crm_rotated_token", rotated.value());
        assertFalse(rotated.value().equals(first.value()));
    }
}
