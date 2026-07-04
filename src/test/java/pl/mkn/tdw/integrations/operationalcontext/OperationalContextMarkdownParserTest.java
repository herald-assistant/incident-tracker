package pl.mkn.tdw.integrations.operationalcontext;

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

                ### `customer-360`

                **Term:** C360

                **Category:** `process-term`

                **Definition:** Customer profile process - handles customer profile lifecycle after validation is complete.

                **Local meaning and boundaries**

                - Handles customer profile lifecycle after validation is complete.

                **Aliases**

                - customer process
                - customer
                - customer profile

                **Match signals**

                Exact:

                - `crm.customer-360.documents.ready`
                - `crm.customer-360.process.reopen`

                Strong:

                - `customer profile lifecycle`
                - `customer profile data ownership`

                Medium:

                - `CRM-CUSTOMER-DataSource`

                Weak:

                - None

                **Canonical references**

                - customer-360-context

                **Not to confuse with**

                - CPE (profile event)
                - CST (support ticket)

                ## Gaps
                - Should not stop the parser.

                ## Customer Domain Terms

                ### Customer Segment

                **Definition:** A segment used to route CRM customer handling.

                **Match signals**

                Strong:

                - `customer segment routing`
                - `segment ownership boundary`

                **Canonical references**

                - VIP Customer
                - Standard Customer
                """;

        var terms = parser.parseGlossary(markdown);

        assertEquals(2, terms.size());
        var customer = terms.get(0);
        assertEquals("customer-360", customer.id());
        assertEquals("C360", customer.term());
        assertEquals("process-term", customer.category());
        assertEquals("Customer profile process - handles customer profile lifecycle after validation is complete.", customer.definition());
        assertTrue(customer.synonyms().contains("customer process"));
        assertTrue(customer.matchSignals().contains("crm.customer-360.documents.ready"));
        assertTrue(customer.matchSignals().contains("customer profile lifecycle"));
        assertTrue(customer.matchSignals().contains("CRM-CUSTOMER-DataSource"));
        assertTrue(customer.doNotConfuseWith().contains("CPE (profile event)"));

        var segment = terms.get(1);
        assertEquals("customer-segment", segment.id());
        assertEquals("Customer Segment", segment.term());
        assertEquals("Customer Domain Terms", segment.category());
        assertTrue(segment.matchSignals().contains("customer segment routing"));
        assertTrue(segment.canonicalReferences().contains("VIP Customer"));
    }

    @Test
    void shouldParseGlossaryMatchSignalsFromCurrentContract() {
        var markdown = """
                # Glossary

                ## Terms

                ### `customer-profile-event`

                **Term:** CPE

                **Category:** `acronym`

                **Definition:** Customer profile event process.

                **Match signals**

                Exact:

                - `crm.customer-profile-event.profile.updated`
                - `crm.customer-profile-event.process.cancelled`

                Strong:

                - `customer profile update`

                Medium:

                - None

                Weak:

                - `CPE`

                **Canonical references**

                - None
                """;

        var terms = parser.parseGlossary(markdown);

        assertEquals(1, terms.size());
        var term = terms.get(0);
        assertTrue(term.matchSignals().contains("crm.customer-profile-event.profile.updated"));
        assertTrue(term.matchSignals().contains("crm.customer-profile-event.process.cancelled"));
        assertTrue(term.matchSignals().contains("customer profile update"));
        assertTrue(term.matchSignals().contains("CPE"));
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

                - **Candidate teams:** CRM Integration Team
                - **Partner teams:** CRM Team

                **Applies when**

                - Evidence points to notification integration failure

                **Required evidence**

                - integration evidence

                **Expected first actions**

                - Verify external call

                **Operational context links**

                - `integration:crm-customer-to-notification-sync`
                - `system:crm-customer-service`
                - `process:customer-support-process` (primary impact)
                """;

        var rules = parser.parseHandoffRules(markdown);

        assertEquals(1, rules.size());
        assertEquals("integration-failure", rules.get(0).id());
        assertEquals("External integration failure", rules.get(0).title());
        assertEquals("CRM Integration Team", rules.get(0).routeTo());
        assertEquals("Evidence points to notification integration failure", rules.get(0).useWhen().get(0));
        assertEquals("integration evidence", rules.get(0).requiredEvidence().get(0));
        assertEquals("Verify external call", rules.get(0).expectedFirstAction().get(0));
        assertEquals("CRM Team", rules.get(0).partnerTeams().get(0));
        assertEquals("crm-customer-service", rules.get(0).references().systems().get(0));
        assertEquals("crm-customer-to-notification-sync", rules.get(0).references().integrations().get(0));
        assertEquals("customer-support-process", rules.get(0).references().processes().get(0));
    }

    @Test
    void shouldParseCurrentHandoffCatalogueFile() throws Exception {
        try (var stream = getClass().getResourceAsStream("/operational-context/handoff-rules.md")) {
            assertNotNull(stream);

            var markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var rules = parser.parseHandoffRules(markdown);

            assertEquals(2, rules.size());
            assertTrue(rules.stream().anyMatch(rule ->
                    rule.id().equals("integration-external-sync-failure")
                            && rule.title().equals("External synchronous integration failure")));
        }
    }

    @Test
    void shouldParseTypicalSignalsFromCurrentGlossaryCatalogueFile() throws Exception {
        try (var stream = getClass().getResourceAsStream("/operational-context/glossary.md")) {
            assertNotNull(stream);

            var markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var terms = parser.parseGlossary(markdown);

            assertTrue(terms.stream().anyMatch(term ->
                    term.id().equals("remote-api-error")
                            && term.matchSignals().contains("RemoteApiError")));
            assertTrue(terms.stream().anyMatch(term -> !term.matchSignals().isEmpty()));
        }
    }
}
