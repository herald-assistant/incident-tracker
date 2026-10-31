# Release summary

Feature posiada porównanie dwóch refów systemu, discovery Liquibase, prompt,
policy, raport, job i historię. Jedyny bieżący tryb to LIQUIBASE; API jest poza zakresem.
Nie importuj sibling feature'ów. Reuse integrations, agenttools, aiplatform i shared.
Refy są typowane i przypinane osobno per repo. Brak refa nie uruchamia fallbacku.
Initial prompt zawiera pełne encje Operational Context i pełny zebrany materiał.
AI używa report tools i GOAL_DRIVEN; source coverage nie oznacza pewności funkcjonalnej.
Wszystkie testy i przykłady są fikcyjnym CRM.

