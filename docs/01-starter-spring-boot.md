# Krok 1: Starter Spring Boot

Ten krok ma jeden cel:
aplikacja ma po prostu wstac.

## Co juz mamy

- projekt Maven,
- Spring Boot,
- zaleznosc `spring-ai-bom`,
- zaleznosc `spring-boot-starter-web`,
- zaleznosc `copilot-sdk-java`,
- zaleznosc `spring-ai-starter-mcp-server-webmvc`,
- klase startowa aplikacji,
- test ladowania kontekstu.

## Co jest wazne w tym kroku

Na razie:

- nie budujemy jeszcze endpointow biznesowych,
- nie budujemy jeszcze MCP servera,
- nie wywolujemy jeszcze Copilot SDK,
- nie integrujemy sie jeszcze z GitLabem, Elasticem ani Dynatrace.

W tym kroku zalezy nam tylko na czystej, dzialajacej bazie.

## Gdzie patrzec w kodzie

- `pom.xml`
- `src/main/java/pl/mkn/incidenttracker/IncidentTrackerApplication.java`
- `src/main/resources/application.properties`
- `src/test/java/pl/mkn/incidenttracker/IncidentTrackerApplicationTests.java`

## Co uruchomic

### 1. Test kontekstu

```powershell
mvn test
```

Oczekiwany efekt:
test przechodzi i Spring laduje kontekst aplikacji.

### 2. Budowanie JAR

```powershell
mvn -DskipTests package
```

Oczekiwany efekt:
powstaje plik `target/incident-tracker-0.0.1-SNAPSHOT.jar`.

### 3. Start aplikacji

```powershell
java -jar target/incident-tracker-0.0.1-SNAPSHOT.jar --server.port=0
```

Dlaczego `--server.port=0`:
bo wtedy Spring wybierze losowy wolny port i nie zderzy sie z innym procesem.

Oczekiwany efekt:
w logach zobaczysz linie `Started IncidentTrackerApplication`.

## Jak debugowac

### Breakpoint 1

Ustaw breakpoint w metodzie:

`IncidentTrackerApplication.main(...)`

Po co:
zobaczysz moment wejscia do aplikacji i start Spring Boot.

### Breakpoint 2

Ustaw breakpoint w tescie:

`IncidentTrackerApplicationTests.contextLoads()`

Po co:
zobaczysz, ze test dochodzi do momentu, w ktorym kontekst juz sie zaladowal.

## Co warto zrozumiec

Po tym kroku powinienes umiec odpowiedziec:

1. Po co w projekcie jest `spring-boot-starter-parent`?
2. Po co importujemy `spring-ai-bom`, skoro jeszcze nie uzywamy AI?
3. Co robi `@SpringBootApplication`?
4. Dlaczego w `application.properties` wylaczylismy MCP na start?
5. Czym rozni sie `mvn test` od `mvn package`?

## Kryterium zakonczonego kroku

Krok 1 uznajemy za zakonczony, gdy:

- projekt buduje sie lokalnie,
- test przechodzi,
- aplikacja startuje,
- rozumiesz role glownych plikow startowych.

## Co dalej

Nastepny krok to:
dodanie pierwszego bardzo prostego endpointu REST.
