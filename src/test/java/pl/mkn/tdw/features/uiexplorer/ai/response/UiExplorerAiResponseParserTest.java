package pl.mkn.tdw.features.uiexplorer.ai.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.EMBEDDED_COMPONENT_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.FETCHED_VALIDATOR_PATH;
import static pl.mkn.tdw.features.uiexplorer.ai.UiExplorerAiRuntimeTestFixture.completeResponse;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.context;
import static pl.mkn.tdw.features.uiexplorer.ai.preparation.UiExplorerAiPreparationTestFixture.request;

class UiExplorerAiResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UiExplorerAiResponseParser parser = new UiExplorerAiResponseParser(objectMapper);

    @Test
    void shouldKeepValidContractPartialWithoutCapturedFallbackEvidence() {
        var parsed = parser.parse(completeResponse(EMBEDDED_COMPONENT_PATH), request(), context(), Set.of());

        assertThat(parsed.status()).isEqualTo(UiExplorerAiParseStatus.PARTIAL);
        assertThat(parsed.result().sections())
                .extracting(section -> section.sectionId())
                .containsExactly(UiExplorerSectionId.OVERVIEW, UiExplorerSectionId.FORMS_AND_RULES);
        assertThat(parsed.result().sections())
                .filteredOn(section -> section.sectionId() == UiExplorerSectionId.FORMS_AND_RULES)
                .singleElement()
                .satisfies(section -> assertThat(section.coverage()).isEqualTo(UiExplorerCoverageStatus.PARTIAL));
        assertThat(parsed.result().usage()).isNull();
        assertThat(parsed.result().screen()).isEqualTo(context().screen());
    }

    @Test
    void shouldAcceptToolFetchedPathOnlyWhenCapturedAsEvidence() {
        var rejected = parser.parse(completeResponse(FETCHED_VALIDATOR_PATH), request(), context(), Set.of());
        var accepted = parser.parse(
                completeResponse(FETCHED_VALIDATOR_PATH),
                request(),
                context(),
                Set.of(FETCHED_VALIDATOR_PATH)
        );

        assertThat(rejected.status()).isEqualTo(UiExplorerAiParseStatus.MALFORMED);
        assertThat(accepted.status()).isEqualTo(UiExplorerAiParseStatus.COMPLETED);
    }

    @Test
    void shouldCreateBlockedSectionForPartialJsonResponse() throws Exception {
        var root = objectMapper.readTree(completeResponse(EMBEDDED_COMPONENT_PATH));
        ((ArrayNode) root.get("sections")).remove(1);

        var parsed = parser.parse(objectMapper.writeValueAsString(root), request(), context(), Set.of());

        assertThat(parsed.status()).isEqualTo(UiExplorerAiParseStatus.PARTIAL);
        assertThat(parsed.result().sections())
                .filteredOn(section -> section.sectionId() == UiExplorerSectionId.FORMS_AND_RULES)
                .singleElement()
                .satisfies(section -> {
                    assertThat(section.coverage()).isEqualTo(UiExplorerCoverageStatus.BLOCKED);
                    assertThat(section.visibilityLimits()).singleElement().asString().contains("omitted");
                });
    }

    @Test
    void shouldRejectMarkdownFenceProtectedFieldAndUngroundedConfirmedClaim() throws Exception {
        var fenced = parser.parse(
                "```json\n" + completeResponse(EMBEDDED_COMPONENT_PATH) + "\n```",
                request(),
                context(),
                Set.of()
        );
        var protectedField = objectMapper.readTree(completeResponse(EMBEDDED_COMPONENT_PATH));
        ((com.fasterxml.jackson.databind.node.ObjectNode) protectedField).put("hiddenRepository", "synthetic-crm/private");
        var confirmedWithoutReference = objectMapper.readTree(completeResponse(EMBEDDED_COMPONENT_PATH));
        ((com.fasterxml.jackson.databind.node.ObjectNode) confirmedWithoutReference
                .get("sections").get(0)).set("sourceReferences", objectMapper.createArrayNode());

        assertThat(fenced.status()).isEqualTo(UiExplorerAiParseStatus.MALFORMED);
        assertThat(parser.parse(
                objectMapper.writeValueAsString(protectedField), request(), context(), Set.of()
        ).status()).isEqualTo(UiExplorerAiParseStatus.MALFORMED);
        assertThat(parser.parse(
                objectMapper.writeValueAsString(confirmedWithoutReference), request(), context(), Set.of()
        ).status()).isEqualTo(UiExplorerAiParseStatus.MALFORMED);
    }

    @Test
    void shouldRejectRemovedFindingsContractWithoutCompatibilityFallback() throws Exception {
        var previousContract = objectMapper.readTree(completeResponse(EMBEDDED_COMPONENT_PATH));
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) previousContract.get("sections").get(0);
        section.put("summary", "Previous synthetic CRM summary.");
        section.set("findings", objectMapper.createArrayNode());

        var parsed = parser.parse(
                objectMapper.writeValueAsString(previousContract), request(), context(), Set.of()
        );

        assertThat(parsed.status()).isEqualTo(UiExplorerAiParseStatus.MALFORMED);
        assertThat(parsed.limitations()).singleElement().asString().contains("section shape");
    }
}
