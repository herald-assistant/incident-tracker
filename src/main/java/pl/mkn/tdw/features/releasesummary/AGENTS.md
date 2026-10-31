# Release summary

Feature posiada porównanie dwóch refów systemu, discovery Liquibase, prompt,
policy, raport, job i historię. Jedyny bieżący tryb to LIQUIBASE; API jest poza zakresem.
Nie importuj sibling feature'ów. Reuse integrations, agenttools, aiplatform i shared.
Refy są typowane i przypinane osobno per repo. Brak refa nie uruchamia fallbacku.
Initial prompt zawiera pełne encje Operational Context i pełny zebrany materiał.
AI używa report tools i GOAL_DRIVEN; source coverage nie oznacza pewności funkcjonalnej.
Wszystkie testy i przykłady są fikcyjnym CRM.

Kanoniczny runtime: `docs/architecture/release-summary-runtime-flow.md`.
Zbieraj pełne snapshoty i verified pliki, domykaj include/SQL/data i zachowuj
kolejność oraz wszystkie atrybuty. Niepełny odczyt, symlink, zmieniony submodule
lub brak zawartości LFS nie może zwrócić NO_CHANGES. SQL/custom change pozostaje
AI_IDENTIFIED; nie twórz pozornego dowodu kompletności semantycznej.

AnalysisReport jest źródłem wyniku. Wymagane elementId występują w obu tabelach,
Przed/Po są niezmienne, a źródła muszą być odczytane. Sprawdzaj też zgodność
indeksu z coverage. Duże pliki dziel bez utraty elementów; aktualny próg grupy
to 32. Report patches podlegają tej samej walidacji co pełny zapis sekcji.

Checkpoint utrwala poprawne sekcje. Kontynuacja zachowuje SHA, captured katalog
i istniejącą sesję. Zwykły follow-up jest read-only; mutacje wymagają jawnego
updateReport. Import V1 nie odtwarza prywatnej sesji. Usage chatu należy do
wiadomości i jest agregowane przez wspólny aside, nie ponownie w job.usage.

