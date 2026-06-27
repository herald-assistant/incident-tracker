# Local Workspace I Launcher

TDW jest uruchamiane lokalnie jako JAR z prostym skryptem startowym. Skrypt ma
lezyc w tym samym katalogu co JAR i przekazywac backendowi katalog danych przez
property `tdw.workspace.directory`.

## Uruchomienie

Klikany launcher dla Windows:

```powershell
.\run-tdw.cmd
```

`run-tdw.cmd` jest cienkim wrapperem dla `run-tdw.ps1`; przekazuje do niego
wszystkie argumenty i uruchamia PowerShell z `ExecutionPolicy Bypass` tylko dla
tego procesu. Przy bledzie zostawia okno otwarte, zeby komunikat byl czytelny.

Wlasciwy skrypt PowerShell mozna tez uruchomic bezposrednio:

```powershell
.\run-tdw.ps1
```

Skrypt szuka JAR-a w swoim katalogu. Dla wygody developerskiej, gdy jest
uruchamiany z katalogu repozytorium, sprawdza tez `target/`. Jezeli znajdzie
wiecej niz jeden pasujacy JAR, mozna wskazac plik jawnie:

```powershell
.\run-tdw.ps1 -JarPath .\incident-tracker-0.0.1-SNAPSHOT.jar
```

Te same argumenty mozna przekazac przez klikany wrapper:

```powershell
.\run-tdw.cmd -JarPath .\incident-tracker-0.0.1-SNAPSHOT.jar
```

Domyslny katalog danych to `tdw-data` obok skryptu i JAR-a. Mozna go nadpisac:

```powershell
.\run-tdw.ps1 -WorkspaceDirectory .\my-tdw-data
```

Do sprawdzenia, jaki JAR i katalog danych zostana uzyte bez startowania
aplikacji, sluzy:

```powershell
.\run-tdw.ps1 -DryRun
```

albo:

```powershell
.\run-tdw.cmd -DryRun
```

Przy bezposrednim uruchomieniu JAR-a trzeba przekazac te sama property:

```powershell
java -jar .\incident-tracker-0.0.1-SNAPSHOT.jar --tdw.workspace.directory=.\tdw-data
```

Spring Boot obsluzy tez zmienna srodowiskowa `TDW_WORKSPACE_DIRECTORY`, ale
rekomendowanym sposobem dla lokalnej paczki jest skrypt.

## Co Powstaje Lokalnie

Po uruchomieniu przez `run-tdw.ps1` powstaje katalog:

```text
tdw-data/
```

Kolejne pliki i katalogi powstaja dopiero wtedy, gdy sa potrzebne:

```text
tdw-data/
  index.json
  tokens.json
  copilot/
  runs/
    <analysisId>/
      run.json
```

`index.json` jest lekkim indeksem UI dla ekranu `Analysis History`. Lista
historii czyta ten plik bez ladowania wszystkich pelnych runow.

`runs/<analysisId>/run.json` jest pelnym lokalnym rekordem runu. Jest ladowany
dopiero po otwarciu konkretnej analizy, eksporcie albo kontynuacji follow-up
chat.

`tokens.json` przechowuje lokalne access tokeny zapisane z UI. Nie jest czescia
exportu i nie powinien byc udostepniany innym osobom.

`copilot/` przechowuje lokalny stan GitHub Copilot SDK/CLI, w tym material
potrzebny do kontynuacji tej samej sesji.

## Local Workspace A Export

Local workspace jest prywatnym, lokalnym stanem aplikacji. Pozwala pokazac
historie analiz, otworzyc zakonczony run i kontynuowac follow-up chat, dopoki
aplikacja ma dostep do tego samego katalogu danych.

Export JSON jest read-only pakietem do podgladu albo dzielenia sie wynikiem.
Export nie zawiera `tokens.json`, lokalnego `copilotSessionId`, hidden context,
stanu Copilota ani sciezek lokalnych. Import exportu laduje wynik do UI bez
zapisywania go jako kontynuowalnego runu.

Pelny backup robi sie przez skopiowanie calego katalogu `tdw-data`, najlepiej
przy zatrzymanej aplikacji. Zwykly export JSON nie jest backupem kontynuowalnej
sesji.

## Usuwanie Danych

Usuniecie `tdw-data` usuwa:

- liste historii analiz,
- pelne lokalne `run.json`,
- mozliwosc kontynuacji follow-up chat dla tych runow,
- tokeny zapisane lokalnie w `tokens.json`,
- lokalny stan sesji Copilota.

Po usunieciu katalogu aplikacja moze wystartowac od nowa, ale poprzednie lokalne
runy nie beda widoczne ani kontynuowalne.

## Bezpieczenstwo

`tdw-data` moze zawierac wrazliwe informacje operacyjne, fragmenty promptow,
wyniki analiz, evidence oraz lokalne access tokeny. Tego katalogu nie nalezy
commitowac, wysylac jako exportu ani kopiowac poza zaufane miejsce.
