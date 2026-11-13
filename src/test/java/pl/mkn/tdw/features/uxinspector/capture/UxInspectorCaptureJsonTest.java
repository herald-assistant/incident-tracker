package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.capture;

class UxInspectorCaptureJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final UxInspectorCaptureJson json = UxInspectorCaptureTestSupport.json(mapper);

    @ParameterizedTest(name = "rejects {0} = {1}")
    @MethodSource("malformedValues")
    void rejectsCoercionOverflowAndInvalidNulls(String pointer, String value) throws Exception {
        ObjectNode node = mapper.valueToTree(capture());
        int separator = pointer.lastIndexOf('/');
        ((ObjectNode) node.at(pointer.substring(0, separator))).set(pointer.substring(separator + 1), mapper.readTree(value));
        assertThatThrownBy(() -> json.read(node, UxInspectorCapture.class)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> malformedValues() {
        return Stream.of(
                Arguments.of("/version", "\"1\""), Arguments.of("/version", "1.5"),
                Arguments.of("/version", "4294967297"), Arguments.of("/version", "null"),
                Arguments.of("/captureProfile", "0"), Arguments.of("/captureProfile", "\"0\""),
                Arguments.of("/captureProfile", "\"element_context\""),
                Arguments.of("/capturedAt", "1794480000"), Arguments.of("/capturedAt", "1794480000.5"),
                Arguments.of("/capturedAt", "\"1794480000\""),
                Arguments.of("/capturedAt", "\"\""), Arguments.of("/capturedAt", "null"),
                Arguments.of("/page/title", "false"), Arguments.of("/page/origin", "null"),
                Arguments.of("/page/queryParameterNames", "\"view\""),
                Arguments.of("/page/queryParameterNames", "[42]"), Arguments.of("/page/queryParameterNames", "null"),
                Arguments.of("/target/state/disabled", "\"false\""), Arguments.of("/target/state/disabled", "0"),
                Arguments.of("/target/state/disabled", "null"), Arguments.of("/target/state/checked", "\"true\""),
                Arguments.of("/target/domFingerprint/stableAttributes", "{\"data-testid\":42}"),
                Arguments.of("/target/domFingerprint/stableAttributes", "null"),
                Arguments.of("/target/domFingerprint/selectorCandidates", "[null]"),
                Arguments.of("/target/bounds/x", "\"42\""), Arguments.of("/client/version", "17"),
                Arguments.of("/ancestors", "null"), Arguments.of("/limits", "[true]"));
    }

    @Test
    void distinguishesMissingNullableValuesFromExplicitNullAndKeepsFalseZeroEmptyCollections() {
        ObjectNode node = mapper.valueToTree(capture());
        node.withObject("/target/state").put("disabled", false).putNull("checked");
        node.withObject("/target/bounds").put("x", 0);
        node.withObject("/page").putNull("title").putArray("queryParameterNames");
        var result = json.read(node, UxInspectorCapture.class);
        assertThat(result.target().state().disabled()).isFalse();
        assertThat(result.target().state().checked()).isNull();
        assertThat(result.target().bounds().x()).isZero();
        assertThat(result.page().queryParameterNames()).isEmpty();
        node.withObject("/target/state").remove("checked");
        assertThatThrownBy(() -> json.read(node, UxInspectorCapture.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownNestedFieldsWithoutChangingGlobalMapperAndReportsNoRejectedValue() throws Exception {
        ObjectNode node = mapper.valueToTree(capture());
        node.withObject("/target").put("unknown", "CRM_SECRET_VALUE");
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.treeToValue(node, UxInspectorCapture.class)).isNotNull();
        assertThatThrownBy(() -> json.read(node, UxInspectorCapture.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("CRM_SECRET_VALUE");
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }
}
