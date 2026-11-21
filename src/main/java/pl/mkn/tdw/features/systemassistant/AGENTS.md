# System Assistant

Konfigurowalny chat operacyjny, feature id `system-assistant`. Nie importuje sibling
feature'ów. API i orkiestracja należą do feature'a; parser/REST do
`integrations.openapi`, callbacki/MCP do `agenttools.openapi`.

- Włączany flagą `system-assistant.enabled`; nie ustawia ani nie waliduje
  adresu nasłuchiwania całego TDW. Docelowe API HTTP(S) nie mają ograniczenia hostów.
- Każda tura to NEW sesja SDK i bieżący zestaw narzędzi oraz instrukcji inline;
  `skillsEnabled=false`. Nie zmieniaj wspólnego skill root ani packaged seeda.
- Konfiguracja ma jeden aktualny zapis. Bez rewizji i archiwalnych promptów.
- Jedna aktywna tura na rozmowę; edycja nie przepina już przyjętej tury.
- Rozmowy mają trwały transkrypt V1 w LocalAnalysisRunStore i wspólnej historii.
  Powrót przez localRunId przywraca wiadomości i pierwotny pageContext, bez
  dawnych skilli/YAML-i i bez SDK resume. Wynik to wiadomość, nie initial
  AnalysisReport. Nowa rozmowa nie usuwa poprzedniej z historii.
- Przyjęcie zapisujemy przed AI; aktywna tura używa wspólnego operation guard
  z historią. Restart oznacza przerwaną turę błędem, nigdy nie ponawia API.
- Auth API, profile, załączniki i obowiązkowy approval są odrębnym zakresem.
- Nie zakładaj fikcyjnych danych ani lokalnego demo; timeout nie uprawnia do retry.

Kontrakt: `docs/architecture/system-assistant-runtime-flow.md`.
Obsługa: `docs/system-assistant.md`. Testy: feature, parser/REST,
MCP wire, frontend, Browser Tools oraz architecture guards.

Import przez modal zapisuje tylko wybrane operacje i ich zależności. Preview
oraz selection API nie zmieniają konfiguracji; publikacja następuje dopiero
przy istniejącym zapisie. Nie zachowuj pełnego źródła ani historii importów.
