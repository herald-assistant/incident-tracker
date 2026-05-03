package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMarkdownParserTest {

    private final OperationalContextMarkdownParser parser = new OperationalContextMarkdownParser();

    @Test
    void shouldParseGlossaryTermsFromCurrentCatalogueMarkdownFormat() {
        var markdown = """
                # GLOSSARY

                ### TTA
                - **Definition**: Agreement process - handles agreement lifecycle after decision is made.
                - **Synonyms**: agreement process, agreement, umowa
                - **Signals**: `clp.tta.documents.ready`, `clp.tta.process.reopen`
                - **REST endpoints**: `/clp/agreement/processhandler/**`, `/clp/agreement/data/**`
                - **HikariPool**: `CLP-AGREEMENT-HikariPool`
                - **Not to confuse with**: TTY (decision), TTL (limit)

                ## Open Questions
                - Should not stop the parser.

                ## Collateral Domain Terms

                ### Collateral / Zabezpieczenie
                - **Definition**: A security instrument attached to a credit product.
                - **Signals**: service `clp-collateral`, endpoints `/clp/collaterals/**`
                - **Types**: Mortgage, Guarantee
                """;

        var terms = parser.parseGlossary(markdown);

        assertEquals(2, terms.size());
        var agreement = terms.get(0);
        assertEquals("tta", agreement.id());
        assertEquals("TTA", agreement.term());
        assertEquals("General", agreement.category());
        assertEquals("Agreement process - handles agreement lifecycle after decision is made.", agreement.definition());
        assertTrue(agreement.synonyms().contains("agreement process"));
        assertTrue(agreement.typicalEvidenceSignals().contains("clp.tta.documents.ready"));
        assertTrue(agreement.typicalEvidenceSignals().contains("/clp/agreement/processhandler/**"));
        assertTrue(agreement.typicalEvidenceSignals().contains("CLP-AGREEMENT-HikariPool"));
        assertTrue(agreement.doNotConfuseWith().contains("TTY (decision)"));

        var collateral = terms.get(1);
        assertEquals("collateral-zabezpieczenie", collateral.id());
        assertEquals("Collateral / Zabezpieczenie", collateral.term());
        assertEquals("Collateral Domain Terms", collateral.category());
        assertTrue(collateral.typicalEvidenceSignals().contains("service clp-collateral"));
        assertTrue(collateral.canonicalReferences().contains("Mortgage"));
    }

    @Test
    void shouldKeepBacktickedTemplateStyleSupportedForHandoffRules() {
        var markdown = """
                # Handoff Rules

                ### `integration-failure`

                **Title:** External integration failure

                **Route to:** Integration Team

                **Use when**

                - Evidence points to partner endpoint

                **Required evidence**

                - endpoint
                """;

        var rules = parser.parseHandoffRules(markdown);

        assertEquals(1, rules.size());
        assertEquals("integration-failure", rules.get(0).id());
        assertEquals("External integration failure", rules.get(0).title());
        assertEquals("Integration Team", rules.get(0).routeTo());
        assertEquals("Evidence points to partner endpoint", rules.get(0).useWhen().get(0));
        assertEquals("endpoint", rules.get(0).requiredEvidence().get(0));
    }

    @Test
    void shouldParseHandoffRulesFromMarkdownTables() {
        var markdown = """
                # Handoff Rules

                ## Internal System Failures

                | Symptom | Route to | Required evidence |
                | --- | --- | --- |
                | Backend service error \\(decision/limit flow\\) | Owner of backend | traceId, endpoint, exceptionClass |
                | RabbitMQ connection failure | Platform team / messaging infra | host, virtualHost, queue/exchange, serviceName |

                ## Open Questions

                - This should not become a handoff rule.
                """;

        var rules = parser.parseHandoffRules(markdown);

        assertEquals(2, rules.size());
        var backendRule = rules.get(0);
        assertEquals("internal-system-failures-backend-service-error-decision-limit-flow", backendRule.id());
        assertEquals("Backend service error (decision/limit flow)", backendRule.title());
        assertEquals("Owner of backend", backendRule.routeTo());
        assertEquals("Backend service error (decision/limit flow)", backendRule.useWhen().get(0));
        assertTrue(backendRule.requiredEvidence().contains("traceId"));
        assertTrue(backendRule.requiredEvidence().contains("endpoint"));
        assertEquals("Route to Owner of backend.", backendRule.expectedFirstAction().get(0));

        var platformRule = rules.get(1);
        assertEquals("Platform team / messaging infra", platformRule.routeTo());
        assertTrue(platformRule.partnerTeams().contains("Platform team / messaging infra"));
    }

    @Test
    void shouldParseCurrentHandoffCatalogueFile() throws Exception {
        try (var stream = getClass().getResourceAsStream("/operational-context/handoff-rules.md")) {
            assertNotNull(stream);

            var markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var rules = parser.parseHandoffRules(markdown);

            assertTrue(rules.size() > 20);
            assertTrue(rules.stream().anyMatch(rule ->
                    rule.title().equals("Backend service error (decision/limit flow)")
                            && rule.routeTo().equals("Owner of backend")));
        }
    }
}
