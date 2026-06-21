# Frontend AGENTS

## Cel frontendu

Frontend jest product-facing UI dla `Team Delivery Workspace`. Nie opisuj go
jako osobnej aplikacji od historycznej nazwy repo ani jako narzedzia tylko dla
analitykow. Incident Analysis jest pierwszym feature'em, ale UI ma wspierac
caly zespol wytworczy i sposob pracy oparty o skills/capabilities.

## Struktura produktu w UI

Glowny shell ma trzy grupy nawigacji:

- `Analysis Features` - dedykowane feature'y produktowe, na start
  `Incident Analysis`.
- `Tool Workbench` - analysis-independent zaplecze do testow, debugowania i
  recznego zbierania inputu: Elastic Logs, GitLab Source, Database Tools i
  Operational Context.
- `Platform` - customizacja Team Delivery Workspace: workspace settings,
  personalizacja, autentykacja, konfiguracja modeli i inne ustawienia
  platformy.

`Operational Context` zostaje w `Tool Workbench`. Nie przenos go do sekcji
`Platform`; Platform dotyczy konfiguracji samego workspace'u.

## Brand i runtime config

- Domyslny tytul UI to `Team Delivery Workspace`.
- Runtime config przychodzi z `GET /api/ui/config`.
- Gdy backend nie ustawia `app.ui.title`, pokazuj tylko
  `Team Delivery Workspace` bez podtytulu.
- Gdy backend ustawia `app.ui.title`, pokazuj property jako tytul i
  `Team Delivery Workspace` jako podtytul.
- Glowny brand uzywa `assets/brand/main-logo.png`.

## Styl i UX

- V1 jest jasnym motywem, ale style maja byc oparte o tokeny CSS i gotowe na
  przyszle warianty.
- UI ma byc spokojne, korporacyjne, czytelne i gesto informacyjne.
- Powtarzalne elementy pracy analitycznej musza byc spojne miedzy feature'ami:
  przebieg runu, tok pracy AI, tool evidence, follow-up chat, import/export,
  usage/cost, empty/error/loading states i drobne elementy prezentacji wyniku.
  Zanim dodasz lokalny markup, model albo styl w feature screen, sprawdz czy
  istnieje shared komponent/model w `core` albo `components`; jezeli wzorzec
  ma zostac uzyty przez wiecej niz jeden feature, wydziel go zamiast kopiowac.
- Result body moze byc feature-specific, bo odpowiada na inny problem
  produktowy, ale jego powtarzalne fragmenty UI/UX powinny uzywac poznanych
  juz wzorcow, zeby uzytkownik nie uczyl sie nowego interfejsu dla tej samej
  czynnosci.
- Nie dodawaj marketingowych hero do narzedzi codziennej pracy.
- Nie uzywaj dekoracyjnych gradientow/orbow ani duzych opisowych kart jako
  glownej kompozycji.
- Jeden ekran albo sekcja robocza powinna miec jeden dominujacy primary action.
- Topbar jest kontekstowy. Nawigacja mieszka w sidebarze.
- Capability context ekranow Workbench pokazuj w topbarze pod ikona info, a
  nie przez lokalne `workbench-header` cards.
- Drawer sluzy do szczegolow, kodu, payloadow, raw preview i encji katalogu.
  Modale zostaw dla akcji blokujacych.

## Tool Workbench

Workbench widoki nie sa feature'ami produktowymi. To laboratorium operatora
nad reusable capability.

Zasady:

- Nie projektuj stalego trzykolumnowego layoutu z response w prawej kolumnie.
- Lewy panel zawiera wspolny scope i liste elementow do testowania.
- Glowna przestrzen pokazuje formularz wybranego elementu, a response pod
  formularzem dopiero po wykonaniu requestu.
- `Request preview` i `JSON response` maja byc zwijalne; po odpowiedzi request
  moze byc domyslnie zwiniety.
- Request/response JSON uzywaja spojnych ikonowych akcji copy/download.
- Workbench API nie powinno eksponowac `analysisRunId` ani incidentowego
  session scope'u. Scope AI nalezy do feature-owned hidden `ToolContext`.

## Weryfikacja

Po zmianach UI uruchom adekwatnie:

- `npm test -- --watch=false`
- `npm run build`

Build produkcyjny aktualizuje `src/main/resources/static`. Nie edytuj
wygenerowanego bundle recznie; generuj go przez build.
