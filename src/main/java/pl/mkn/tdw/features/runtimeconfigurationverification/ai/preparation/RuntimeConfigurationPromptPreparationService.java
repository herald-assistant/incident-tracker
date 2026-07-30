package pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationPromptPreparationService {

    private final RuntimeConfigurationAiArtifactService artifactService;

    public RuntimeConfigurationPromptPreparation prepare(
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext
    ) {
        var mode = request != null && request.mode() != null
                ? request.mode()
                : RuntimeConfigurationVerificationMode.BASIC;
        var artifacts = artifactService.render(mode, deterministic, deepContext);
        var prompt = """
                # Runtime Configuration Verification — druga opinia AI

                ## Tryb
                `%s`

                ## Zadanie
                - Najpierw załaduj skill `%s` przez built-in tool `skill`.
                - Przeanalizuj pełny zanonimizowany manifest, structural diff i deterministic findings.
                - Manifest obejmuje parametry zmienione i niezmienione oraz zachowuje dokumenty/profile i zagnieżdżenie YAML.
                - Nie próbuj odtwarzać wartości z `valueToken`; token oznacza wyłącznie relację w bieżącym runie.
                - Wynik deterministyczny jest niemutowalny. Nie usuwaj findingów, nie zmieniaj diffu, coverage, ownershipu ani statusu.
                - Obserwacja typu `GROUNDED_OBSERVATION` musi wskazywać co najmniej jeden istniejący `differenceId` lub `findingId`.
                - Wniosek bez takiego oparcia oznacz jako `HYPOTHESIS`.
                - Zapisuj tylko sekcje raportu dozwolone przez ukryty scope report tools.
                - Finalna odpowiedź musi być wyłącznie jednym obiektem JSON zgodnym z `runtime-configuration/response-contract.json`.
                %s

                ## Artefakty osadzone w promptcie
                %s
                """.formatted(
                mode,
                mode == RuntimeConfigurationVerificationMode.DEEP
                        ? "runtime-configuration-deep-review"
                        : "runtime-configuration-basic-review",
                modeInstruction(mode),
                artifacts.contents().entrySet().stream()
                        .map(entry -> "\n### " + entry.getKey() + "\n```json\n" + entry.getValue() + "\n```")
                        .collect(Collectors.joining("\n"))
        ).trim();
        return new RuntimeConfigurationPromptPreparation(
                prompt,
                artifacts.contents(),
                artifacts.visibilityLimits()
        );
    }

    private String modeInstruction(RuntimeConfigurationVerificationMode mode) {
        if (mode == RuntimeConfigurationVerificationMode.DEEP) {
            return """
                    - Użyj przygotowanego deep context do nazwania systemów i funkcjonalności, których dotyczy rozjazd.
                    - Operational Context i GitLab code tools są wyłącznie fallbackiem do focused verification w ukrytym scope wybranego `internal-system`.
                    - `functionalImpacts` muszą używać wyłącznie przekazanych system/context/code IDs.
                    - Ownership jest faktem backendowym: opisz, do kogo zwrócić się po szczegóły, ale nie zmieniaj ownerów ani resolution path.
                    """.trim();
        }
        return """
                - Nie używaj Operational Context ani kodu. W BASIC interpretuj wyłącznie konfigurację, diff i findings.
                - `functionalImpacts` musi pozostać pustą listą.
                """.trim();
    }
}
