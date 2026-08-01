package pl.mkn.tdw.features.runtimeconfigurationverification.presentation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiConclusion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiExecutionStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiObservationType;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiSecondOpinion;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationFunctionalImpact;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFinding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationFindingSeverity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationFileRole;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationDiffAnnotationServiceTest {

    private final RuntimeConfigurationDiffAnnotationService service =
            new RuntimeConfigurationDiffAnnotationService();

    @Test
    void shouldJoinDirectAndFindingTransitiveDifferenceIdsWithoutReplacingCommentValues() {
        var opinion = opinion(
                List.of(new RuntimeConfigurationAiObservation(
                        "observation-1",
                        RuntimeConfigurationAiObservationType.GROUNDED_OBSERVATION,
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
        assertEquals(RuntimeConfigurationDiffAnnotationKind.OBSERVATION, annotations.get(0).kind());
        assertFalse(annotations.get(0).hypothesis());
    }

    @Test
    void shouldCreateFunctionalImpactAnnotationWithConfidenceAndHypothesis() {
        var opinion = opinion(
                List.of(),
                List.of(new RuntimeConfigurationFunctionalImpact(
                        "impact-1",
                        "Obsługa klientów",
                        "Może kierować ruch do innej bazy.",
                        RuntimeConfigurationAiConfidence.MEDIUM,
                        true,
                        List.of("system-1"),
                        List.of(),
                        List.of("finding-1"),
                        List.of(),
                        List.of()
                ))
        );

        var annotation = service.create(deterministic(), opinion).get(0);

        assertEquals(RuntimeConfigurationDiffAnnotationKind.FUNCTIONAL_IMPACT, annotation.kind());
        assertEquals("Obsługa klientów: Może kierować ruch do innej bazy.", annotation.comment());
        assertEquals(RuntimeConfigurationAiConfidence.MEDIUM, annotation.confidence());
        assertTrue(annotation.hypothesis());
        assertEquals(List.of("difference-2"), annotation.differenceIds());
    }

    @Test
    void shouldSkipUngroundedAnnotationsAndNormalizeLongComment() {
        var longSummary = "  " + "ryzyko ".repeat(80) + "  ";
        var opinion = opinion(
                List.of(
                        new RuntimeConfigurationAiObservation(
                                "observation-long",
                                RuntimeConfigurationAiObservationType.HYPOTHESIS,
                                longSummary,
                                null,
                                List.of("difference-1"),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        new RuntimeConfigurationAiObservation(
                                "observation-unknown",
                                RuntimeConfigurationAiObservationType.GROUNDED_OBSERVATION,
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

    private static RuntimeConfigurationAiSecondOpinion opinion(
            List<RuntimeConfigurationAiObservation> observations,
            List<RuntimeConfigurationFunctionalImpact> impacts
    ) {
        return new RuntimeConfigurationAiSecondOpinion(
                RuntimeConfigurationAiExecutionStatus.COMPLETED,
                RuntimeConfigurationAiConclusion.REVIEW_REQUIRED,
                RuntimeConfigurationAiConfidence.MEDIUM,
                "Review.",
                observations,
                List.of(),
                impacts,
                List.of()
        );
    }

    private static RuntimeConfigurationDeterministicContext deterministic() {
        return new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "system-1",
                "System 1",
                "backend",
                "dev1",
                "zt001",
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(difference("difference-1"), difference("difference-2")),
                List.of(new RuntimeConfigurationFinding(
                        "finding-1",
                        "CONFIGURATION_CHANGE",
                        RuntimeConfigurationFindingSeverity.WARNING,
                        "spring.datasource",
                        List.of("difference-2", "difference-2"),
                        List.of()
                ))
        );
    }

    private static RuntimeConfigurationDifference difference(String id) {
        return new RuntimeConfigurationDifference(
                id,
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                0,
                "spring.datasource.url",
                RuntimeConfigurationChangeKind.CHANGED,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationSensitivity.NON_SENSITIVE,
                "source-token",
                "target-token"
        );
    }
}
