package pl.mkn.tdw.features.configdriftviewer.ai.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerPromptPreparationService {

    private final ConfigDriftViewerAiArtifactService artifactService;

    public ConfigDriftViewerPromptPreparation prepare(
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext
    ) {
        if (request == null || request.mode() != ConfigDriftViewerMode.DEEP) {
            throw new IllegalArgumentException("AI prompt preparation is available only for DEEP verification.");
        }
        var artifacts = artifactService.render(deterministic, deepContext);
        var prompt = """
                # Config Drift Viewer — druga opinia AI

                ## Tryb
                `DEEP`

                ## Zadanie
                - Najpierw załaduj skill `%s` przez built-in tool `skill`.
                - Przeanalizuj kompaktowe `configuration-tree.yaml`, `changes.json` i deterministic findings.
                - Drzewo obejmuje parametry zmienione i niezmienione, zachowuje granice dokumentów/profile i zagnieżdżenie YAML.
                - Rozwiń kody wyłącznie według legend artefaktu: `p:*` to run-local pseudonym, representation `M` to suppression sekretu, `O` to węzeł struktury, a `A` to brak po jednej stronie.
                - Nie próbuj odtwarzać wartości z pseudonimu; służy wyłącznie do porównania relacji w bieżącym runie.
                - Wynik deterministyczny jest niemutowalny. Nie usuwaj findingów, nie zmieniaj diffu, coverage, ownershipu ani statusu.
                - Obserwacja typu `GROUNDED_OBSERVATION` musi wskazywać co najmniej jeden istniejący `differenceId` lub `findingId`.
                - Wniosek bez takiego oparcia oznacz jako `HYPOTHESIS`.
                - Zapisuj tylko sekcje raportu dozwolone przez ukryty scope report tools.
                - Finalna odpowiedź musi być wyłącznie jednym obiektem JSON zgodnym z `config-drift-viewer/response-contract.json`.
                %s

                ## Artefakty osadzone w promptcie
                %s
                """.formatted(
                "config-drift-viewer-deep-review",
                deepInstruction(),
                artifacts.contents().entrySet().stream()
                        .map(entry -> "\n### " + entry.getKey() + "\n```"
                                + language(entry.getKey()) + "\n" + entry.getValue() + "\n```")
                        .collect(Collectors.joining("\n"))
        ).trim();
        return new ConfigDriftViewerPromptPreparation(
                prompt,
                artifacts.contents(),
                artifacts.visibilityLimits()
        );
    }

    private String language(String artifactName) {
        return artifactName != null
                && (artifactName.endsWith(".yaml") || artifactName.endsWith(".yml"))
                ? "yaml"
                : "json";
    }

    private String deepInstruction() {
        return """
                - Użyj przygotowanego deep context do nazwania systemów i funkcjonalności, których dotyczy rozjazd.
                - Operational Context i GitLab code tools są wyłącznie fallbackiem do focused verification w ukrytym scope wybranego `internal-system`.
                - `functionalImpacts` muszą używać wyłącznie przekazanych system/context/code IDs.
                - Ownership jest faktem backendowym: opisz, do kogo zwrócić się po szczegóły, ale nie zmieniaj ownerów ani resolution path.
                """.trim();
    }
}
