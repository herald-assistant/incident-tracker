package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

class UxInspectorCaptureContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UxInspectorCaptureNormalizer normalizer = new UxInspectorCaptureNormalizer(objectMapper);

    @Test
    void shouldRejectUnknownNestedFieldsLegacyVersionAndPayloadBombsBeforeNormalization() throws Exception {
        var node = objectMapper.valueToTree(capture());
        node.withObject("target").put("value", "FORM_VALUE_MUST_NOT_ENTER_CAPTURE");
        assertThrows(Exception.class, () -> objectMapper.treeToValue(node, UxInspectorCapture.class));

        ObjectNode legacy = objectMapper.valueToTree(capture());
        legacy.put("capturedAt", "2026-09-15T10:00:00Z");
        legacy.put("version", 1);
        var legacyCapture = objectMapper.treeToValue(legacy, UxInspectorCapture.class);
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(legacyCapture));

        ObjectNode oversized = objectMapper.valueToTree(capture());
        oversized.put("capturedAt", "2026-09-15T10:00:00Z");
        oversized.withObject("page").put("title", "x".repeat(UxInspectorCapture.MAX_BYTES));
        assertThrows(Exception.class, () -> objectMapper.treeToValue(oversized, UxInspectorCapture.class));
    }

    @Test
    void shouldApplyAllowlistRedactionAndLimitsForElementContext() throws Exception {
        var ancestors = new ArrayList<UxInspectorCapture.Ancestor>();
        for (var index = 1; index <= 30; index++) {
            ancestors.add(new UxInspectorCapture.Ancestor(index, "section", null, null,
                    "Kontakt contact" + index + "@example.invalid numer 1234567", Map.of()));
        }
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("data-testid", "contact-save");
        attributes.put("formcontrolname", "contactName");
        attributes.put("onclick", "stealSecret()");
        attributes.put("data-customer-value", "FORM_VALUE_MUST_NOT_SURVIVE");
        var raw = new UxInspectorCapture(UxInspectorCapture.SCHEMA, 3, "cap_crm_redaction",
                Instant.parse("2026-09-15T10:00:00Z"),
                UxInspectorCapture.CaptureProfile.ELEMENT_CONTEXT,
                new UxInspectorCapture.Page("https://crm.example.com", "/contacts/:value", "CRM 1234567", "pl",
                        List.of("view", "token", "view")),
                new UxInspectorCapture.Target("input", "textbox", "Password token",
                        "contact@example.invalid 1234567",
                        new UxInspectorCapture.DomFingerprint(attributes,
                                List.of("input[data-testid=\"contact-save\"]"), List.of("crm-contact-form"), null),
                        new UxInspectorCapture.TargetState(false, false, false, true, true, null, null, false),
                        new UxInspectorCapture.Bounds(10, 20, 200, 40)),
                ancestors, null, new UxInspectorCapture.Traversal(31, 25, 6, true),
                new UxInspectorCapture.Signals(0, "TOP_LEVEL", List.of("FORM_VALUES_NOT_REQUESTED")),
                List.of(), new UxInspectorCapture.Client("TDW UX Inspector", "3.0.0", "ux-inspector"));

        var normalized = normalizer.normalize(raw);
        var json = objectMapper.writeValueAsString(normalized);

        assertNull(normalized.target().accessibleName());
        assertEquals("[EMAIL] [NUMBER]", normalized.target().text());
        assertEquals(Map.of("data-testid", "contact-save", "formcontrolname", "contactName"),
                normalized.target().domFingerprint().stableAttributes());
        assertEquals(List.of("view"), normalized.page().queryParameterNames());
        assertEquals(24, normalized.ancestors().size());
        assertTrue(normalized.limits().contains("ANCESTORS_TRUNCATED"));
        assertTrue(normalized.signals().redactions().contains("BACKEND_TEXT_REDACTED"));
        assertTrue(normalized.signals().redactions().contains("BACKEND_SENSITIVE_TEXT_REMOVED"));
        assertFalse(json.contains("FORM_VALUE_MUST_NOT_SURVIVE"));
        assertFalse(json.contains("stealSecret"));
        assertTrue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= UxInspectorCapture.MAX_BYTES);
    }

    @Test
    void shouldKeepAllowedFormValuesAndRejectSensitiveControlsFromTheValuesPayload() {
        var valid = capture();
        var validity = new UxInspectorCapture.Validity(false, true, false, false, false, false,
                false, false, false, false, false, "Uzupełnij adres e-mail");
        var email = new UxInspectorCapture.FormControl(true, "input", "email", "email", "email",
                "E-mail", Map.of("name", "email", "formcontrolname", "email"),
                "customer@example.test", false, null, List.of(), List.of(), false, false, true, validity);
        var excluded = new UxInspectorCapture.ExcludedFormControl("input", "password", null, null,
                "SENSITIVE_TYPE");
        var form = new UxInspectorCapture.FormSnapshot("NEAREST_FORM", Map.of("id", "login-form"),
                List.of("#login-form"), false, 2, 1, 0, List.of(email), List.of(), List.of(excluded),
                "customer@example.test".length(), false);
        var raw = new UxInspectorCapture(valid.schema(), valid.version(), valid.captureId(), valid.capturedAt(),
                UxInspectorCapture.CaptureProfile.FORM_DIAGNOSTICS, valid.page(), valid.target(), valid.ancestors(),
                form, valid.traversal(), valid.signals(), valid.limits(), valid.client());

        var normalized = normalizer.normalize(raw);

        assertEquals("customer@example.test", normalized.formSnapshot().controls().get(0).value());
        assertFalse(normalized.formSnapshot().valid());
        assertEquals("SENSITIVE_TYPE", normalized.formSnapshot().excludedControls().get(0).reason());
        assertTrue(normalized.signals().redactions().contains("BACKEND_SENSITIVE_FORM_CONTROLS_EXCLUDED"));

        var passwordInPayload = new UxInspectorCapture.FormControl(false, "input", "password", "password", null,
                "Hasło", Map.of(), "crm-secret", false, null, List.of(), List.of(), false, false, true, validity);
        var invalidForm = new UxInspectorCapture.FormSnapshot("NEAREST_FORM", Map.of(), List.of(), false,
                1, 1, 0, List.of(passwordInPayload), List.of(), List.of(), 10, false);
        var invalid = new UxInspectorCapture(raw.schema(), raw.version(), raw.captureId(), raw.capturedAt(),
                raw.captureProfile(), raw.page(), raw.target(), raw.ancestors(), invalidForm, raw.traversal(),
                raw.signals(), raw.limits(), raw.client());
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(invalid));
    }

    @Test
    void shouldRejectAnOversizedOriginInsteadOfNormalizingItToNull() {
        var valid = capture();
        var raw = new UxInspectorCapture(valid.schema(), valid.version(), valid.captureId(), valid.capturedAt(),
                valid.captureProfile(),
                new UxInspectorCapture.Page("https://" + "a".repeat(2048) + ".example.com", valid.page().path(),
                        valid.page().title(), valid.page().language(), valid.page().queryParameterNames()),
                valid.target(), valid.ancestors(), valid.formSnapshot(), valid.traversal(), valid.signals(), valid.limits(), valid.client());

        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(raw));
    }
}
