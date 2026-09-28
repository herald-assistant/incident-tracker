package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

class UxInspectorFormFieldsSnapshotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UxInspectorFormFieldsSnapshotService service = new UxInspectorFormFieldsSnapshotService(mapper);
    private final AnalysisAiAuthRef operator = AnalysisAiAuthRef.localToken("crm-operator");

    @Test
    void acceptsMoreThanSixtyFourFieldsAndBindsOneTimeReferenceToCapture() throws Exception {
        var fields = mapper.createArrayNode();
        for (var index = 0; index < 70; index++) fields.add(field("contactNote" + index, "CRM note " + index));
        fields.add(field("password", "crm-private"));
        var receipt = service.stage(input(envelope(fields)), operator);

        var claimed = service.claim(receipt.formFieldsSnapshotRef(), formCapture(), operator);

        assertNotNull(claimed);
        assertEquals(70, claimed.fields().size());
        assertEquals(1, receipt.omittedSensitiveFields());
        assertNull(service.claim(receipt.formFieldsSnapshotRef(), formCapture(), operator));
    }

    @Test
    void keepsEmptyArrayAsSuccessAndRejectsWrongScopeOrMalformedFields() throws Exception {
        var receipt = service.stage(input(envelope(mapper.createArrayNode())), operator);
        var wrongCapture = formCapture();
        wrongCapture = new UxInspectorCapture(wrongCapture.schema(), wrongCapture.version(), "cap_crm_other",
                wrongCapture.capturedAt(), wrongCapture.captureProfile(), wrongCapture.page(), wrongCapture.target(),
                wrongCapture.ancestors(), wrongCapture.traversal(), wrongCapture.signals(), wrongCapture.limits(), wrongCapture.client());
        assertNull(service.claim(receipt.formFieldsSnapshotRef(), wrongCapture, operator));
        var emptyReceipt = service.stage(input(envelope(mapper.createArrayNode())), operator);
        assertEquals(0, service.claim(emptyReceipt.formFieldsSnapshotRef(), formCapture(), operator).fields().size());

        var otherOperatorReceipt = service.stage(input(envelope(mapper.createArrayNode())), operator);
        assertNull(service.claim(otherOperatorReceipt.formFieldsSnapshotRef(), formCapture(),
                new AnalysisAiAuthRef(AnalysisAiAuthRef.PROVIDER_GITHUB, AnalysisAiAuthRef.MODE_LOCAL_TOKEN,
                        "crm-other-principal", "CRM other operator", true)));

        var invalid = mapper.createArrayNode().add(field("contactName", "CRM"));
        ((ObjectNode) invalid.get(0)).put("unexpected", "bad");
        assertThrows(IllegalArgumentException.class, () -> service.stage(input(envelope(invalid)), operator));
    }

    private UxInspectorCapture formCapture() {
        var base = capture();
        return new UxInspectorCapture(base.schema(), base.version(), base.captureId(), base.capturedAt(),
                UxInspectorCapture.CaptureProfile.FORM_DIAGNOSTICS, base.page(), base.target(), base.ancestors(),
                base.traversal(), base.signals(), base.limits(), base.client());
    }

    private ObjectNode field(String name, String value) {
        var field = mapper.createObjectNode();
        field.put("tag", "input"); field.put("type", "text"); field.put("name", name);
        field.put("id", name); field.put("testId", ""); field.put("label", "CRM contact note");
        field.put("disabled", false); field.put("value", value); field.put("display", value);
        field.put("source", "dom"); field.putNull("checked"); field.putNull("indeterminate");
        field.putNull("invalid"); field.putArray("errors"); field.putArray("descriptions");
        field.putNull("nativeInvalid"); field.put("nativeValidationMessage", "");
        return field;
    }

    private ObjectNode envelope(com.fasterxml.jackson.databind.node.ArrayNode fields) {
        var envelope = mapper.createObjectNode();
        envelope.put("captureId", capture().captureId());
        envelope.put("origin", capture().page().origin());
        envelope.set("fields", fields);
        return envelope;
    }

    private ByteArrayInputStream input(ObjectNode value) throws Exception {
        return new ByteArrayInputStream(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
    }
}
