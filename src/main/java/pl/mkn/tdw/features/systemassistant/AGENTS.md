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
- Auth API ma katalog profili Basic/Bearer/OAuth2/OIDC w konfiguracji; profil zawiera apiBindings z apiId i authProfileId lub null (No Auth).
  Neutralny runtime jest w integrations.httpauth; Auth nie trafia do promptu ani schema tooli.
  Pola operatora są jawne w JSON/UI/eksporcie konfiguracji. Tokeny OIDC są pamięciowe.
- Przed przyjęciem tury sprawdzaj/odnawiaj OIDC tylko aktywnych operacji profilu.
  409 AUTH_LOGIN_REQUIRED zawiera authProfileId; brak gotowości nie zapisuje tury
  i nie uruchamia AI. Backend wiąże dozwoloną ścieżkę powrotu, rozmowę i profil
  ze state i przeglądarką. Cookie ma HttpOnly/SameSite=None/Secure. Jednorazowe
  wznowienie odrzuconej wiadomości w obu widokach opisuje
  [kanoniczny przebieg OIDC](../../../../../../../../docs/architecture/system-assistant-runtime-flow.md#uwierzytelnianie-źródłowych-api).
- Załączniki PNG/JPEG/WebP/GIF, PDF i DOCX zapisujemy strumieniowo w runs/{id}/attachments/{uuid}.{ext}. Metadane są w storeSnapshot run.json i GET rozmowy, poza exportEnvelope. Każda NEW tura dostaje wszystkie pliki jako SDK blobs. PDFBox renderuje PDF do JPEG wszystkich stron w pamięci (144 DPI), jednakowo dla każdego modelu; bez zapisu pochodnych i OCR. Upload blokuje ten sam operation guard co tura/delete. Pliki są usuwane tylko z runem, bez limitów TDW i walidacji kompatybilności modelu. Bez tymczasowego storage OIDC. Obowiązkowy approval pozostaje odrębnym zakresem.
- Nie zakładaj fikcyjnych danych ani lokalnego demo; timeout nie uprawnia do retry.
- Metadane pliku mają opcjonalne messageId pierwszej przyjętej wiadomości USER.
  Oczekujące pliki mają null. Powiązanie zapisuj przed AI, cofaj razem z odrzuconą
  wiadomością i odtwarzaj z lokalnego snapshotu; nie eksportuj go ani nie
  wyprowadzaj historycznie z diagnostyki. Nie zmienia to plików dostarczanych AI.
- Pliki z odpowiedzi OpenAPI są wynikami bieżącego narzędzia, bez zapisu przy
  rozmowie i bez automatycznego dołączania w przyszłych turach. Ponowne wywołanie
  wykonuje nowe HTTP. Neutralne tools przygotowują PDF/obrazy/DOCX, platforma
  mapuje zawartość binarną na SDK; bez semantyki feature'a w tych warstwach.

Kontrakt: `docs/architecture/system-assistant-runtime-flow.md`.
Obsługa: `docs/system-assistant.md`. Testy: feature, parser/REST,
MCP wire, frontend, Browser Tools oraz architecture guards.

Import przez modal zapisuje tylko wybrane operacje i ich zależności. Preview
oraz selection API nie zmieniają konfiguracji; publikacja następuje dopiero
przy istniejącym zapisie. Nie zachowuj pełnego źródła ani historii importów.

Profile są przypisaniami do wspólnych katalogów API/skilli. Per tura wymagane
profileId rozwiązuje bieżące zasoby i pomija brakujące; nie blokuje profilu
bez narzędzi. Nie ma fallbacku do wszystkich zasobów. Wybór nie zmienia
transkryptu, pierwotnego pageContext ani innych profili MCP. Braki są ostrzeżeniem
w konfiguracji. Select chatu jest feature-owned; pełny ekran pokazuje wszystkie
profile, ramka filtruje po ingress origin + pathname wildcardem *, niezależnie
od historycznego kontekstu rozmowy. Konfiguracja/import V1 wymaga profiles;
nie dodawaj migracji ani wersjonowania.
