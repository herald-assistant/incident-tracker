package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMarkdownParserTest {

    private final OperationalContextMarkdownParser parser = new OperationalContextMarkdownParser();

    @Test
    void shouldParseGlossaryTermsFromCurrentCatalogueMarkdownFormat() {
        var markdown = """
                # GLOSSARY

                ## Terms

                ### TTA

                **Term:** TTA

                **Category:** `process-term`

                **Definition:** Agreement process - handles agreement lifecycle after decision is made.

                **Local meaning and boundaries**

                - Handles agreement lifecycle after decision is made.

                **Aliases**

                - agreement process
                - agreement
                - umowa

                **Match signals**

                Exact:

                - `clp.tta.documents.ready`
                - `clp.tta.process.reopen`

                Strong:

                - `/clp/agreement/processhandler/**`
                - `/clp/agreement/data/**`

                Medium:

                - `CLP-AGREEMENT-HikariPool`

                Weak:

                - None

                **Canonical references**

                - tta-context

                **Not to confuse with**

                - TTY (decision)
                - TTL (limit)

                ## Gaps
                - Should not stop the parser.

                ## Collateral Domain Terms

                ### Collateral / Zabezpieczenie

                **Definition:** A security instrument attached to a credit product.

                **Match signals**

                Strong:

                - service `clp-collateral`
                - endpoints `/clp/collaterals/**`

                **Canonical references**

                - Mortgage
                - Guarantee
                """;

        var terms = parser.parseGlossary(markdown);

        assertEquals(2, terms.size());
        var agreement = terms.get(0);
        assertEquals("tta", agreement.id());
        assertEquals("TTA", agreement.term());
        assertEquals("process-term", agreement.category());
        assertEquals("Agreement process - handles agreement lifecycle after decision is made.", agreement.definition());
        assertTrue(agreement.synonyms().contains("agreement process"));
        assertTrue(agreement.matchSignals().contains("clp.tta.documents.ready"));
        assertTrue(agreement.matchSignals().contains("/clp/agreement/processhandler/**"));
        assertTrue(agreement.matchSignals().contains("CLP-AGREEMENT-HikariPool"));
        assertTrue(agreement.doNotConfuseWith().contains("TTY (decision)"));

        var collateral = terms.get(1);
        assertEquals("collateral-zabezpieczenie", collateral.id());
        assertEquals("Collateral / Zabezpieczenie", collateral.term());
        assertEquals("Collateral Domain Terms", collateral.category());
        assertTrue(collateral.matchSignals().contains("service clp-collateral"));
        assertTrue(collateral.canonicalReferences().contains("Mortgage"));
    }

    @Test
    void shouldParseGlossaryMatchSignalsFromCurrentContract() {
        var markdown = """
                # Glossary

                ## Terms

                ### `tty`

                **Term:** TTY

                **Category:** `acronym`

                **Definition:** Credit decision process.

                **Match signals**

                Exact:

                - `clp.tty.decision.made`
                - `clp.tty.process.cancelled`

                Strong:

                - `decision process`

                Medium:

                - None

                Weak:

                - `TTY`

                **Canonical references**

                - None
                """;

        var terms = parser.parseGlossary(markdown);

        assertEquals(1, terms.size());
        var term = terms.get(0);
        assertTrue(term.matchSignals().contains("clp.tty.decision.made"));
        assertTrue(term.matchSignals().contains("clp.tty.process.cancelled"));
        assertTrue(term.matchSignals().contains("decision process"));
        assertTrue(term.matchSignals().contains("TTY"));
        assertFalse(term.matchSignals().contains("Exact:"));
        assertFalse(term.matchSignals().contains("None"));
        assertFalse(term.canonicalReferences().contains("None"));
    }

    @Test
    void shouldParseCurrentHandoffRuleSections() {
        var markdown = """
                # Handoff Rules

                ### `integration-failure`

                **Title:** External integration failure

                **Route decision**

                - **Candidate teams:** Integration Team
                - **Partner teams:** Core Team

                **Applies when**

                - Evidence points to partner endpoint

                **Required evidence**

                - endpoint

                **Expected first actions**

                - Verify external call

                **Operational context links**

                - `integration:app-core-to-partner-sync`
                - `system:app-core`
                - `process:core-process` (primary impact)
                """;

        var rules = parser.parseHandoffRules(markdown);

        assertEquals(1, rules.size());
        assertEquals("integration-failure", rules.get(0).id());
        assertEquals("External integration failure", rules.get(0).title());
        assertEquals("Integration Team", rules.get(0).routeTo());
        assertEquals("Evidence points to partner endpoint", rules.get(0).useWhen().get(0));
        assertEquals("endpoint", rules.get(0).requiredEvidence().get(0));
        assertEquals("Verify external call", rules.get(0).expectedFirstAction().get(0));
        assertEquals("Core Team", rules.get(0).partnerTeams().get(0));
        assertEquals("app-core", rules.get(0).references().systems().get(0));
        assertEquals("app-core-to-partner-sync", rules.get(0).references().integrations().get(0));
        assertEquals("core-process", rules.get(0).references().processes().get(0));
    }

    @Test
    void shouldParseCurrentHandoffCatalogueFile() throws Exception {
        try (var stream = getClass().getResourceAsStream("/operational-context/handoff-rules.md")) {
            assertNotNull(stream);

            var markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var rules = parser.parseHandoffRules(markdown);

            assertTrue(rules.size() > 20);
            assertTrue(rules.stream().anyMatch(rule ->
                    rule.id().equals("tty-decision-not-received")
                            && rule.references().systems().contains("clp-agreement-process")
                            && rule.references().integrations().contains("tty-to-clp-agreement-decision")));
        }
    }

    @Test
    void shouldParseTypicalSignalsFromCurrentGlossaryCatalogueFile() throws Exception {
        try (var stream = getClass().getResourceAsStream("/operational-context/glossary.md")) {
            assertNotNull(stream);

            var markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var terms = parser.parseGlossary(markdown);

            assertTrue(terms.stream().anyMatch(term ->
                    term.id().equals("tty")
                && term.matchSignals().contains("exchange:clp.local.tty.decision.made")));
        assertTrue(terms.stream().anyMatch(term -> !term.matchSignals().isEmpty()));
        }
    }
}
