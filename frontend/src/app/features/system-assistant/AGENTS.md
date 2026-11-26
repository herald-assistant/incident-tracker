# System Assistant

Chat-first konfigurowalny feature. Reuse shared chat, steps/activity/evidence,
usage, preferencje AI i kontrolki. Ekran posiada bieżący szkic konfiguracji,
walidację i requesty. Nie importuje innych feature'ów. Konfiguracja to jeden
aktualny zestaw, bez wersjonowania. Auth źródłowe: Basic/Bearer/OAuth2/OIDC, z formularzami inline i selectem Auth przy API w profilu, domyślnie No Auth. Oba widoki mają przycisk wielokrotnego uploadu PNG/JPEG/WebP/GIF, PDF i DOCX w istniejącym slocie akcji chatu oraz nazwy zapisanych plików. Upload jest sekwencyjny, strumieniowy, bez limitów TDW i kontroli modelu; blokuje wysyłanie/nawigację i jest niedostępny podczas tury. Pliki zapisujemy trwale przy rozmowie, bez tymczasowego storage FE/OIDC. Usuwa je wyłącznie usunięcie rozmowy.
Browser Tools przekazuje wyłącznie mały page-context przez sprawdzony handshake.
Nazwy nowych plików renderuj nad pierwszą przyjętą wiadomością wskazaną przez
messageId w metadanych. Przed wysłaniem są przy composerze. Nie powielaj etykiet
w następnych turach ani nie twórz zbiorczej listy nad historią. Shared chat
posiada prezentację; feature dostarcza mapę nazw i listę oczekujących plików.
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
Skille, profile i Auth startują zwinięte do belek z nazwą oraz odpowiednio ID,
wzorcem URL lub typem uwierzytelniania. Nowy wpis jest rozwinięty. Stan zwinięcia
należy do strony, przetrwa przełączenie zakładki i nie trafia do konfiguracji ani
storage. Import resetuje go do zwiniętego; niepoprawny Auth rozwija się przy zapisie.
Hasło, token i client secret są domyślnie maskowane przez shared text-field
z passwordToggle, z przyciskiem oka jak w Workspace Settings. JSON i eksport
konfiguracji nadal zawierają jawne wartości.
Listy czterech zakładek mają licznik wpisów i Dodaj bez powtórzonego nagłówka,
odstępy zamiast wewnętrznych separatorów oraz hover całej belki z obszarem
akcji. Małe ikony są po prawej; usuwanie jest ostatnią akcją.
Jeden sticky pasek grupuje import, eksport i Zapisz konfigurację dla całego
zestawu. Pokazuje tylko niezapisane zmiany i komunikaty. Zakres zapisu jest
w tooltipie. Zakładki używają shared equalWidth z równą szerokością i paginacją.
OIDC używa zapisanej konfiguracji. Powrót ręcznego logowania wybiera Auth.
Pełny ekran chroni szkic przy wyjściu z trasy, zamknięciu, odświeżeniu i OIDC
z rozmowy. Porównuj treść z ostatnim poprawnym odczytem lub zapisem. Zmiana
zakładki lub rozmowy zachowuje szkic bez pytania. Przy anulowaniu OIDC zachowaj
wiadomość w polu i odblokuj chat.
409 AUTH_LOGIN_REQUIRED przy wysłaniu rozpoczyna login i location.assign także
w ramce, bez zmiany sandbox/CSP. Backend wiąże chat/profil/returnPath z state;
returnPath to wyłącznie /system-assistant lub /system-assistant/chat.
Powrót odtwarza localRunId i profil w obu trybach, embedded zamyka picker.
Na czas loginu oba widoki zapisują w sessionStorage odrzuconą wiadomość,
conversationId, profileId, returnPath, model/reasoning i termin ważności (5 min); tylko ramka
dołącza opcjonalny zweryfikowany ingress. Cache ma jeden aktualny kształt,
jest ściśle walidowany, wiązany z rozmową/profilem/widokiem i konsumowany
jednorazowo. Callback success wznawia wysłanie dokładnie raz po gotowości
chatu, rozmowy, konfiguracji, katalogu AI i PAT, z tym samym profilem oraz
preferencjami. Bez komunikatu o przywracaniu tekstu. Failed lub brak gotowości
zachowuje tekst w polu; nie uzbraja późniejszej wysyłki po zmianie scope.
Ponowny AUTH_LOGIN_REQUIRED wznowionej próby kończy się błędem, bez pętli loginu.
Błąd storage blokuje przekierowanie i zachowuje
tekst; błąd rozpoczęcia loginu usuwa cache, ale zachowuje pole.
Nie używaj historycznego pageContext do filtra profili. Bez storage szkicu
konfiguracji, ponawiania zaakceptowanych wiadomości, popupów lub tokenów w FE. Inne błędy wymagają
dotychczasowego reconciliation; nie wolno przekierowywać po błędzie narzędzia.
Usunięcie używanego Auth jest blokowane do zmiany selectów zależnych przypisań w profilach.
Kontrakty dodajemy tylko w modalu importu z nazwą i wyborem operacji. Wiersz
ma edycję nazwy API inline pod ikoną ołówka, podgląd Swagger UI, eksport bieżącego YAML-a
i usunięcie. Nazwa ma 1–100 znaków, nie może być samymi białymi znakami i jest
zapisywana wspólnym zapisem konfiguracji; ID, YAML i przypisania pozostają bez zmian.
Nie przywracaj ręcznej edycji YAML/ID ani selekcji operacji istniejącego kontraktu. Swagger jest
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
nie dostaje metadanych ani wiadomości. Ten sam picker i ikona historii są
dostępne na pełnym ekranie w zakładce Rozmowa. Pełny ekran nadal zaczyna od
Skilli, bez automatycznego otwierania historii. Ikona wraca do listy, wybór
nie remountuje iframe. Zachowuj szkice/błędy/wiadomości bez potwierdzenia
per ID; stare odpowiedzi HTTP nie mogą nadpisać nowego wyboru. Aktywna tura
blokuje przełączanie w obu trybach. Chat pozostaje zamontowany pod ukrytą
historią; aside jest widoczny tylko przy rozmowie na pełnym ekranie.
Wybór na pełnym ekranie aktualizuje localRunId; kontrakt V1 pozostaje bez zmian.

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

Panel Browser Tools potwierdza bieżący kontekst przed wysyłką. Każda przyjęta tura
otrzymuje pełny URL z query i fragmentem. Profil filtruj po origin + pathname.
Po OIDC ponownie uzgodnij stronę z rodzicem przed wznowieniem wysyłki.
