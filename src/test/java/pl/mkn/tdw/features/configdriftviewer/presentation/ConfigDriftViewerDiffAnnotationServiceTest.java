package pl.mkn.tdw.features.configdriftviewer.presentation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConfidence;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservation;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservationType;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiSecondOpinion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerFunctionalImpact;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerFinding;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerFindingSeverity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerFileRole;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDriftViewerDiffAnnotationServiceTest {

    private final ConfigDriftViewerDiffAnnotationService service =
            new ConfigDriftViewerDiffAnnotationService();

    @Test
    void shouldJoinDirectAndFindingTransitiveDifferenceIdsWithoutReplacingCommentValues() {
        var opinion = opinion(
                List.of(new ConfigDriftViewerAiObservation(
                        "observation-1",
                        ConfigDriftViewerAiObservationType.GROUNDED_OBSERVATION,
                        "Wartość VALUE_A zmienia wybór datasource.",
                        "Fallback explanation.",
                        List.of("difference-1", "unknown-difference"),
                        List.of("finding-1"),
                        List.of(),
                        List.of()
                )),
                List.of()
        );

        var annotations = service.create(deterministic(), opinion);

        assertEquals(1, annotations.size());
        assertEquals(
                List.of("difference-1", "difference-2"),
                annotations.get(0).differenceIds()
        );
        assertEquals("Wartość VALUE_A zmienia wybór datasource.", annotations.get(0).comment());
        assertEquals(ConfigDriftViewerDiffAnnotationKind.OBSERVATION, annotations.get(0).kind());
        assertFalse(annotations.get(0).hypothesis());
    }

    @Test
    void shouldCreateFunctionalImpactAnnotationWithConfidenceAndHypothesis() {
        var opinion = opinion(
                List.of(),
                List.of(new ConfigDriftViewerFunctionalImpact(
                        "impact-1",
                        "Obsługa klientów",
                        "Może kierować ruch do innej bazy.",
                        ConfigDriftViewerAiConfidence.MEDIUM,
                        true,
                        List.of("system-1"),
                        List.of(),
                        List.of("finding-1"),
                        List.of(),
                        List.of()
                ))
        );

        var annotation = service.create(deterministic(), opinion).get(0);

        assertEquals(ConfigDriftViewerDiffAnnotationKind.FUNCTIONAL_IMPACT, annotation.kind());
        assertEquals("Obsługa klientów: Może kierować ruch do innej bazy.", annotation.comment());
        assertEquals(ConfigDriftViewerAiConfidence.MEDIUM, annotation.confidence());
        assertTrue(annotation.hypothesis());
        assertEquals(List.of("difference-2"), annotation.differenceIds());
    }

    @Test
    void shouldSkipUngroundedAnnotationsAndNormalizeLongComment() {
        var longSummary = "  " + "ryzyko ".repeat(80) + "  ";
        var opinion = opinion(
                List.of(
                        new ConfigDriftViewerAiObservation(
                                "observation-long",
                                ConfigDriftViewerAiObservationType.HYPOTHESIS,
                                longSummary,
                                null,
                                List.of("difference-1"),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        new ConfigDriftViewerAiObservation(
                                "observation-unknown",
                                ConfigDriftViewerAiObservationType.GROUNDED_OBSERVATION,
                                "Nie ma potwierdzonego powiązania.",
                                null,
                                List.of("unknown"),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                ),
                List.of()
        );

        var annotations = service.create(deterministic(), opinion);

        assertEquals(1, annotations.size());
        assertEquals(280, annotations.get(0).comment().length());
        assertTrue(annotations.get(0).comment().endsWith("…"));
        assertTrue(annotations.get(0).hypothesis());
    }

    @Test
    void shouldReturnEmptyAnnotationsWhenAiOpinionIsMissing() {
        assertTrue(service.create(deterministic(), null).isEmpty());
        assertTrue(service.create(null, opinion(List.of(), List.of())).isEmpty());
    }

    private static ConfigDriftViewerAiSecondOpinion opinion(
            List<ConfigDriftViewerAiObservation> observations,
            List<ConfigDriftViewerFunctionalImpact> impacts
    ) {
        return new ConfigDriftViewerAiSecondOpinion(
                ConfigDriftViewerAiExecutionStatus.COMPLETED,
                ConfigDriftViewerAiConclusion.REVIEW_REQUIRED,
                ConfigDriftViewerAiConfidence.MEDIUM,
                "Review.",
                observations,
                List.of(),
                impacts,
                List.of()
        );
    }

    private static ConfigDriftViewerDeterministicContext deterministic() {
        return new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "system-1",
                "System 1",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(difference("difference-1"), difference("difference-2")),
                List.of(new ConfigDriftViewerFinding(
                        "finding-1",
                        "CONFIGURATION_CHANGE",
                        ConfigDriftViewerFindingSeverity.WARNING,
                        "spring.datasource",
                        List.of("difference-2", "difference-2"),
                        List.of()
                ))
        );
    }

    private static ConfigDriftViewerDifference difference(String id) {
        return new ConfigDriftViewerDifference(
                id,
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                0,
                "spring.datasource.url",
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                "source-token",
                "target-token"
        );
    }
}
