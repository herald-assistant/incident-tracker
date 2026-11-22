# System Assistant — obsługa

System Assistant korzysta z kontraktów OpenAPI i instrukcji utrzymywanych
przez operatora. Umożliwia zadawanie pytań, odczyt stanu, obliczenia
i zlecanie operacji udostępnionych w kontrakcie. API może wskazywać dowolny
poprawny host HTTP(S), w obsługiwanym podzbiorze OpenAPI 3.0.x.
Feature jest domyślnie wyłączony; uruchom TDW z
`--system-assistant.enabled=true`. Wybór adresu nasłuchiwania TDW jest
niezależny od tej flagi. AI wymaga skonfigurowanego PAT Copilota w Workspace Settings.

## Konfiguracja

Otwórz `/system-assistant`. Ekran zaczyna od `Skilli` i ma pięć zakładek:
`Skille`, `Kontrakty`, `Profile`, `Rozmowa` oraz `Uruchamianie w systemie`.
W zakładce `Skille` dodaj instrukcje pracy
asystenta. W zakładce `Kontrakty` wybierz `Dodaj OpenAPI` i wskaż plik YAML
swojego API. W modalu wybierz operacje, nadaj nazwę kontraktu i użyj
`Użyj wybranych operacji`. Do szkicu trafiają tylko wybrane operacje
i potrzebne im schematy. Kliknij `Zapisz`, aby aktywować konfigurację.
Następna przyjęta tura korzysta z aktualnie zapisanego zestawu.
Zmiana szkicu nie wpływa na chat przed zapisem. Tura już rozpoczęta kończy
pracę na swoim zestawie. Przyciski dodawania oraz `Zapisz` są na dole po
prawej, a błędy zapisu nad nimi. Przełączanie zakładek zachowuje szkic.

Swagger UI pozwala przejrzeć parametry, odpowiedzi i schematy. Wyszukiwanie,
filtry metody/tagu i `Tylko wybrane` ułatwiają wybór. `Zaznacz wszystko`
obejmuje wszystkie dostępne operacje kontraktu, niezależnie od filtrów
i strony; `Odznacz wszystko` usuwa cały wybór. Niedostępne operacje mają
opis powodu. Anulowanie importu zachowuje poprzedni szkic.

Wiersz kontraktu ma akcje `Podgląd`, `Eksportuj YAML` i `Usuń`. Podgląd nie
wykonuje żądań do API. Aby zmienić zestaw operacji, usuń kontrakt ze szkicu
i zaimportuj nowy plik; oba kroki możesz aktywować jednym zapisem.
Licznik na belce pokazuje aktywne operacje zapisanego kontraktu, a tooltip
ich metody i ścieżki. Nowo dodany kontrakt ma zero aktywnych operacji do zapisu.

Ikony obok zakładek oferują `Importuj konfigurację` i `Eksportuj konfigurację`.
Eksport obejmuje cały bieżący szkic kontraktów i skilli, również niezapisany.
Import zastępuje szkic; wymaga `Zapisz`, aby aktywować zestaw. Niepoprawny
plik nie zmienia szkicu. Rozmowy, preferencje AI i autentykacja pozostają
poza tym eksportem.

## Profile

W zakładce `Profile` dodaj profil, nadaj nazwę i przypisz istniejące API oraz
skille checkboxami. Domyślny `Wzorzec URL` to `*`. Możesz wpisać np.
`https://crm.example.com/customers/*`, aby profil pojawiał się tylko na
pasujących stronach w Browser Tools. Gwiazdka oznacza dowolny ciąg; pozostałe
znaki są literalne. Dopasowanie obejmuje origin i ścieżkę, bez query/hash.
Pełny ekran TDW zawsze pokazuje wszystkie profile.

Kliknij `Zapisz`, a w chacie wybierz profil obok modelu i reasoningu.
Przełączenie zachowuje rozmowę i jej kontekst. Kolejna tura używa bieżących
treści przypisanych zasobów; rozpoczęta kończy pracę na swoim zestawie.
Wybór profilu nie jest zapisywany w historii — po ponownym wejściu wybierz
profil do następnej wiadomości. Nie ma automatycznego wyboru pierwszego.

Usunięte API lub skill nie blokuje profilu. W edytorze zobaczysz ikonę
ostrzeżenia z tooltipem zawierającym brakujące ID. Możesz usunąć takie
przypisanie; pozostałe zasoby nadal działają. Profil bez dostępnych zasobów
pozwala rozmawiać bez narzędzi. Import/eksport konfiguracji obejmuje również
profile; bieżący format V1 wymaga listy profiles i nie importuje starego kształtu.

## Rozmowa i Browser Tools

Zakładka `Rozmowa` pozwala wybrać model AI i rozpocząć chat. Instrukcje mogą
określać sposób odczytu danych, wykonywania obliczeń i potwierdzania zmian.
Rozmowy są trwale zapisywane w historii. Kontynuacja zachowuje wcześniejszy
kontekst, ale korzysta z bieżących skilli i narzędzi.
Z `Platform / Analysis History` otwórz zapis, aby wrócić do zakładki Rozmowa.
`Nowa rozmowa` pozostawia poprzednią w historii. Usuwanie jest osobną akcją
i pozostaje zablokowane podczas aktywnej tury.

W zakładce `Uruchamianie w systemie` przeciągnij link `TDW Browser Tools`
na pasek zakładek Chrome. Na stronie
źródłowej wybierz kafelek `System Assistant`. Panel umożliwia wyszukanie
i kontynuowanie wcześniejszej rozmowy lub rozpoczęcie nowej bez otwierania
pełnego TDW. Ikona historii pozwala wrócić do listy rozmów.
Zamknięcie i ponowne otwarcie panelu zachowuje rozmowę i szkic wiadomości.
Lewą krawędź można przeciągać; ikona obok zamknięcia maksymalizuje panel.
W panelu nie pokazujemy toku działania AI ani kosztów. Na pełnym ekranie
TDW są dostępne we wspólnym panelu szczegółów. Enter wysyła wiadomość,
a Shift+Enter dodaje nową linię.

Nowa rozmowa otrzymuje origin, pathname, tytuł i czas otwarcia strony.
Nie przekazuje query/hash, formularzy, cookies ani autentykacji systemu
źródłowego. CSP lub polityka osadzania może zablokować ramkę; panel oferuje
ponowienie i link do pełnego TDW.
Wybranie wcześniejszej rozmowy zachowuje kontekst przekazany przy jej
utworzeniu, również gdy otwierasz ją z innej strony. Nawigacja w aplikacji
nie przepina automatycznie kontekstu chatu.

## Zapis i ograniczenia

Wymagany jest włączony lokalny workspace TDW. Aktualna konfiguracja jest
zapisana w `${tdw.workspace.directory}/system-assistant/configuration.json`,
a transkrypty w `${tdw.workspace.directory}/runs/{id}/run.json` ze wspólnym
indeksem historii. Skille i kontrakty nie mają rewizji ani archiwum;
historia rozmowy nie przywraca dawnej konfiguracji.

Po restarcie można kontynuować rozmowę. Tura przerwana przez restart jest
oznaczana błędem i nie jest automatycznie ponawiana. Przy nieustalonym
wyniku operacji sprawdź stan systemu przed ponownym zleceniem zapisu.
Błąd odświeżania chatu pozwala ponowić odczyt, bez ponawiania polecenia.

Feature nie obsługuje uwierzytelniania do podłączonych API, przejmowania
logowania ze strony, załączników. Skille są
instrukcjami pracy, nie mechanizmem nadawania uprawnień. Importer obsługuje
wybrane konstrukcje OpenAPI, w tym readOnly, format i nullable;
pełna macierz i limity są w [runtime flow](architecture/system-assistant-runtime-flow.md#importowany-podzbiór-openapi).

## Testy i wyłączenie

Kontrakty CRM do testów automatycznych znajdują się w
`src/test/resources/openapi/{crm-api,contacts-api}.yaml`. Testy uruchamiają
własne izolowane serwery HTTP i nie wymagają osobnej aplikacji demo.
Test z rzeczywistym Copilotem jest uruchamiany tylko jawnie:

```powershell
$env:SYSTEM_ASSISTANT_LIVE_TEST='true'
mvn -q '-Dtest=SystemAssistantCopilotLiveTest' test
Remove-Item Env:SYSTEM_ASSISTANT_LIVE_TEST
```

Ten test wymaga istniejącego PAT, korzysta z fikcyjnych danych i izolowanego
katalogu CLI; nie zastępuje konfiguracji asystenta operatora.

Wyłączenie wymaga ponownego uruchomienia TDW z
`--system-assistant.enabled=false`. API i MCP asystenta zwracają 404,
a nawigacja i nowo załadowany Browser Tools ukrywają akcję. Bieżąca
konfiguracja pozostaje na dysku. Wyłączenie nie wycofuje zewnętrznych zapisów.
Kontrakty API, MCP, obsługiwaną składnię i limity opisuje
[runtime flow](architecture/system-assistant-runtime-flow.md).
