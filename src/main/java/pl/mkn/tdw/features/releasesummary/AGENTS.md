# Release summary

Feature posiada porównanie dwóch refów systemu, discovery Liquibase, prompt,
policy, raport, job i historię. Jedyny bieżący tryb to LIQUIBASE; API jest poza zakresem.
Nie importuj sibling feature'ów. Reuse integrations, agenttools, aiplatform i shared.
Refy są typowane i przypinane osobno per repo. Brak refa nie uruchamia fallbacku.
Initial prompt zawiera pełne encje Operational Context i pełny zebrany materiał.
AI używa report tools i GOAL_DRIVEN; source coverage nie oznacza pewności funkcjonalnej.
Wszystkie testy i przykłady są fikcyjnym CRM.

Kanoniczny runtime: `docs/architecture/release-summary-runtime-flow.md`.
QUEUED z planem kroków zapisuj przed dispatch. COLLECT_LIQUIBASE publikuje
contextSections per repo/plik z pełnym beforeContent/afterContent i lukami.
Projekcja UI nie dubluje źródeł w prompcie. PREPARE_PROMPT utrwala dokładny
tekst jako COMPLETED przed sprawdzeniem modelu i wykonaniem AI; zachowuj
ukończone kroki i evidence po błędzie oraz przy odtworzeniu historii.
Zbieraj pełne snapshoty i verified pliki, domykaj include/SQL/data i zachowuj
kolejność oraz wszystkie atrybuty. Niepełny odczyt, symlink, zmieniony submodule
lub brak zawartości LFS nie może zwrócić NO_CHANGES. SQL/custom change pozostaje
AI_IDENTIFIED; nie twórz pozornego dowodu kompletności semantycznej.

AnalysisReport jest źródłem wyniku dla analityka systemowego. Inwentarz raportu
scala techniczne paczki evidence w zakresy plików: kolumna razem z constraints,
indeks razem z uporządkowanymi kolumnami. Pełne evidence pozostaje bez zmian.
Tabela szczegółów ma Obiekt, Zmiana techniczna, Znaczenie funkcjonalne i Źródła.
Wszystkie rozpoznane pozycje i ich deterministyczne definicje są obowiązkowe;
SQL wymaga interpretacji. Źródła są krótkimi linkami do odczytanych materiałów.
Nie pokazuj hashy, AST, JSON ani placeholderów w raporcie. Scaffold jest prywatny;
dopiero zaakceptowana sekcja może opublikować raport. Model jest wymagany.
Report patches podlegają tej samej walidacji co pełny zapis sekcji. Bieżący
resultContract to release-summary-analyst-report-v1, format zapisu ma wersję 1.

Checkpoint utrwala poprawne sekcje. Kontynuacja zachowuje SHA, captured katalog
i istniejącą sesję. Zwykły follow-up jest read-only; mutacje wymagają jawnego
updateReport. Import V1 nie odtwarza prywatnej sesji. Usage chatu należy do
wiadomości i jest agregowane przez wspólny aside, nie ponownie w job.usage.

