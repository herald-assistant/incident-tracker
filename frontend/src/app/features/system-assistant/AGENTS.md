# System Assistant

Chat-first konfigurowalny feature. Reuse shared chat, steps/activity/evidence,
usage, preferencje AI i kontrolki. Ekran posiada bieżący szkic konfiguracji,
walidację i requesty. Nie importuje innych feature'ów. Konfiguracja to jeden
aktualny zestaw, bez wersjonowania. Auth źródłowe: Basic/Bearer/OAuth2/OIDC, z formularzami inline i selectem Auth przy API w profilu, domyślnie No Auth. Bez załączników.
Browser Tools przekazuje wyłącznie mały page-context przez sprawdzony handshake.
Akcja osadza chat TDW w iframe /system-assistant/chat poza AppShell. Reuse
jednego template'u rozmowy; pełny ekran ma zakładki Skille, Kontrakty, Auth, Profile, Rozmowa i Uruchamianie w systemie. Transfer V1
sprawdza parent/contentWindow, origin, nonce i dokładne pola; nie przywracaj
opener/popup dla System Assistant. Schowanie panelu zachowuje ramkę i stan,
a usunięcie Browser Tools usuwa panel i listenery. Nie osłabiaj CSP.

OpenAPI wybieramy w feature-owned modalu CDK z filtrami i stronicowaniem.
Frontend nie parsuje YAML-a. Backend zwraca katalog i przygotowany podzbiór;
modal zmienia tylko szkic, a Zapisz aktywuje konfigurację. Pełne źródło
nie jest przechowywane po zamknięciu modalu; anulowanie zachowuje szkic.

Konfiguracja używa shared analysis-result-tabs. Auth ma formularze inline, bez modali CRUD.
Dane dostępowe są jawne w polach tekstowych i eksporcie konfiguracji.
OIDC używa zapisanej konfiguracji. Powrót ręcznego logowania wybiera Auth.
409 AUTH_LOGIN_REQUIRED przy wysłaniu rozpoczyna login i location.assign także
w ramce, bez zmiany sandbox/CSP. Backend wiąże chat/profil z state; powrót
otwiera pełny widok tego chatu i wybiera profil. Bez storage szkicu, ponawiania
wiadomości, popupów lub przekazywania tokenów do FE. Inne błędy nadal wymagają
dotychczasowego reconciliation; nie wolno przekierowywać po błędzie narzędzia.
Usunięcie używanego Auth jest blokowane do zmiany selectów zależnych przypisań w profilach.
Kontrakty dodajemy tylko w modalu importu z nazwą i wyborem operacji. Wiersz
ma podgląd Swagger UI, eksport bieżącego YAML-a i usunięcie. Nie przywracaj ręcznej edycji
YAML/ID/nazwy ani selekcji operacji istniejącego kontraktu. Swagger jest
lokalnie pakowany, osadzany w izolowanym iframe i nie wykonuje żądań API.

Belka kontraktu pokazuje liczbę aktywnych operacji z tooltipem metod i ścieżek.
Grupuj katalog po jawnym apiId, bez dopasowania prefiksów nazw narzędzi.
Szkic nie aktywuje operacji do zapisu; błąd katalogu nie oznacza zera.

Rozmowy są trwale zapisywane we wspólnej Analysis History. localRunId otwiera
zakładkę Rozmowa i pobiera istniejącą rozmowę; nowa wiadomość idzie na ten sam
ID z bieżącą konfiguracją. Błąd odczytu blokuje wysyłanie do jawnego retry
lub rozpoczęcia nowej rozmowy. Nowa rozmowa tylko odłącza bieżącą w UI,
nie usuwa jej historii. W panelu osadzonym nie renderuj toku AI/kosztów; na
pełnym ekranie zachowaj standardowy aside. Nie przywracaj inline aside.

Panel osadzony zaczyna od feature-owned pickera nowych/zapisanych rozmów,
korzystającego ze wspólnego history API wyłącznie na originie TDW. Rodzic
nie dostaje metadanych ani wiadomości. Ikona historii wraca do listy, wybór
nie remountuje iframe. Zachowuj szkice/błędy/wiadomości bez potwierdzenia
per ID; stare odpowiedzi HTTP nie mogą nadpisać nowego wyboru. Aktywna tura
blokuje przełączanie. Pełny ekran i kontrakt V1 pozostają bez zmian.

Profile są przypisaniami do wspólnych katalogów API/skilli. Per tura wymagane
profileId rozwiązuje bieżące zasoby i pomija brakujące; nie blokuje profilu
bez narzędzi. Nie ma fallbacku do wszystkich zasobów. Wybór nie zmienia
transkryptu, pierwotnego pageContext ani innych profili MCP. Braki są ostrzeżeniem
w konfiguracji. Select chatu jest feature-owned; pełny ekran pokazuje wszystkie
profile, ramka filtruje po ingress origin + pathname wildcardem *, niezależnie
od historycznego kontekstu rozmowy. Konfiguracja/import V1 wymaga profiles;
nie dodawaj migracji ani wersjonowania.

Import JSON/YAML obsługuje OpenAPI 3.0/3.1 i kompozycję schematów.
Kontrakt źródłowy, walidacja requestu i schemat argumentów mają odrębne role.
Nie zgaduj discriminator mapping i nie usuwaj ograniczeń allOf/oneOf.
Kanoniczne limity oraz semantykę x-tdw-operation-effect opisuje runtime
System Assistanta; GET bez potwierdzenia skutków pozostaje UNKNOWN.
