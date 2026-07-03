---
name: flow-explorer-map-persistence-section
description: "Przygotowanie sekcji PERSISTENCE Flow Explorera dla trybow COMPACT i DEEP: code-first opis danych, operacji persistence oraz table/column/source mapping, gdy wymagany jest deep."
---

# Flow Explorer Map Persistence Section

Uzywaj tego skilla, gdy `sectionModes.PERSISTENCE` ma tryb `COMPACT` albo
`DEEP`. Tryb decyduje o szczegolowosci wyniku, nie o tym, czy skill jest
wlascicielem sekcji.

## Cel

Zbuduj material dla sekcji `PERSISTENCE` analizowanego endpointu:

- dla `COMPACT`: zwiezly opis, jakie dane endpoint czyta, zapisuje albo
  zmienia i po co,
- dla `DEEP`: code-first mapowanie tabel, kolumn i zrodel wartosci, gdy endpoint
  tworzy, aktualizuje, usuwa albo zmienia relacje.

Ten skill wykonuje konkretne zadanie i zwraca `PersistenceMappingSummary`.
Nie jest ogolnym formatem outputu ani zamiennikiem dla calego raportu Flow
Explorera.

## Kiedy Uzyc

Uzyj tego skilla, gdy:

- `PERSISTENCE` jest aktywne w trybie `COMPACT` albo `DEEP`,
- endpoint czyta dane, wykonuje lookup, create/update/delete/link/unlink,
  zapis kolekcji, zapis outbox/event albo zmiane relacji,
- wynik ma opisac persistence dla analityka/testera,
- w trybie `DEEP` wynik ma pokazac `TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS`,
  jezeli endpoint zapisuje albo relacyjnie zmienia dane.

Nie uzywaj tego skilla, gdy:

- `PERSISTENCE=OFF`,
- pytanie dotyczy tylko walidacji, functional flow albo integracji,
- endpoint nie dotyka persistence i nie ma czego opisac w tej sekcji.

## Wejscia

Wymagane:

- `EndpointFlowSummary`: entry point, use-case service, write operations i
  znane role plikow z artefaktow,
- canonical `branchRef`, `applicationName`, `projectName` i `filePath` z
  `flow-explorer/canonical-tool-inputs.md` oraz
  `flow-explorer/compact-flow-manifest.md`,
- aktualny `goal`, `sectionModes` i `reasoningEffort`,
- snippet/code evidence juz osadzone w promptcie.

Opcjonalne:

- `CodeGroundingSummary` z `flow-explorer-code-grounding`,
- `OperationalGroundingSummary` z `flow-explorer-operational-grounding`, gdy
  trzeba nazwac biznesowo zrodlo zewnetrzne,
- `IntegrationBoundarySummary`, gdy wartosc zapisywana pochodzi z odpowiedzi
  albo eventu z innego systemu.

## Procedura

1. Ustal tryb sekcji z `sectionModes.PERSISTENCE`: `COMPACT` albo `DEEP`.
2. Ustal persistence touchpoints endpointu: read/lookup, create, update,
   delete, link/unlink, zapis kolekcji, outbox/event albo relacja.
3. Dla kazdego touchpointu znajdz typ domenowy/encje, mapper, request/form DTO,
   repository albo service, ktory czyta albo przekazuje dane do persistence.
4. Dla `COMPACT` przygotuj `PersistenceMappingSummary` z operacjami, targetami,
   najwazniejszymi danymi i source refs. Nie domykaj wszystkich kolumn, jezeli
   nie zmienia to zrozumienia sekcji.
5. Dla `DEEP` czytaj kod code-first przez `flow-explorer-code-grounding`:
   Java/Spring implementation, JPA/Hibernate annotations, mappery i metody
   encji.
6. Dla `DEEP` uwzglednij `@Entity`, `@Table`, `@Column`, `@JoinColumn`,
   `@JoinTable`, `@CollectionTable`, `@AttributeOverride(s)`, `@Embedded`,
   `@Embeddable`, `@Enumerated`, `@MappedSuperclass` oraz pola/property
   mapowane domyslnie przez JPA/Hibernate.
7. Dla `DEEP` przejdz rekurencyjnie przez dziedziczenie, embeddables, klasy
   bazowe, parent entity, relacje, kolekcje `@OneToMany`, `@ManyToMany`,
   `@ElementCollection`, `List<XForm>`, `Set<XForm>` i join tables.
8. Dla `DEEP` wypisz wszystkie kolumny biorace udzial w zapisie albo zmianie
   relacji. Same kolumny join table typu `*_ID` nie domykaja wyniku; trzeba
   zejsc do tabeli elementu kolekcji.
9. Dla kazdej kolumny ustaw `SOURCE`:
   - `GENERATED`,
   - `REQUEST`,
   - `CALCULATED`,
   - biznesowa nazwa systemu albo komponentu zewnetrznego.
10. W `SOURCE DETAILS` opisz sciezke danych jezykiem analityka/testera, np.
   `req.body.customer.email`, `param.customer.name`, `resp.address.street`
   albo krotka regula dla `CALCULATED`.
11. Nie uzywaj DDL, Liquibase, Flyway, changelogow ani migracji SQL. Nazwy
   tabel i kolumn wyprowadzaj z implementacji Java, adnotacji ORM i
   standardowych konwencji Hibernate/Spring naming.
12. Gdy `maxDepth reached`, ambiguous implementation albo unresolved interface
    blokuje mapowanie, wykonaj focused read/search po konkretnym typie,
    mapperze, encji, klasie bazowej albo embeddable. Dopiero po takim probe
    wpisz limitation.

## Petla Poglebiania

Po wstepnym `PersistenceMappingSummary` wykonaj gate zgodny z trybem sekcji:

- `COMPACT`: summary ma nazwac glowne operacje danych, targety, sens dla
  endpointu i source refs,
- `DEEP`: summary ma domknac tabele, kolumny, `SOURCE`, `SOURCE DETAILS` oraz
  luki dla kazdego zapisu albo zmiany relacji.

Jezeli summary jest zbyt plytkie dla aktywnego trybu:

- nazwij brak jako konkretne pytanie evidence,
- uzyj `flow-explorer-code-grounding` dla najmniejszego typu, mappera, encji,
  metody, klasy bazowej albo embeddable,
- uzyj `flow-explorer-operational-grounding`, gdy brakuje biznesowej nazwy
  `SOURCE`,
- po targeted retry ponownie sprawdz gate,
- jezeli retry nie rozstrzyga braku, wpisz `visibility_gap` dla konkretnego
  wiersza albo `visibilityLimits` dla sekcji.

Nie finalizuj wyniku `DEEP` jako `COMPACT` tylko dlatego, ze pierwsze evidence
bylo za plytkie. Nie dopisuj brakujacych tabel, kolumn ani `SOURCE` z domyslow.

## Kontrakt Wyniku

Zwroc `PersistenceMappingSummary`:

```text
persistenceMode: compact | deep
dataOperations:
  - operation: read | lookup | create | update | delete | link | unlink | event/outbox
    target: <encja/tabela/relacja>
    meaning: <co endpoint robi z danymi>
tables:
  - tableName: <TABLE_NAME>
    columns:
      - column: <COLUMN>
        source: GENERATED | REQUEST | CALCULATED | <biznesowa nazwa systemu>
        sourceDetails: <opis sciezki wartosci>
        confidence: fact | inference | visibility_gap
sourceRefs:
  - <artifact/tool/projectName:path:Lx-Ly>
visibilityLimits:
  - <konkretny brak>
openQuestions:
  - <pytanie, jezeli potrzebne>
```

Dla `COMPACT` przekaz do raportu zwiezly opis `dataOperations`, glownych
targetow i source refs. Dla `DEEP` przekaz przede wszystkim tabele:

| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |
| --- | --- | --- | --- |

## Walidacja

Przed zakonczeniem sprawdz:

- kazda create/update/delete/link/unlink operation ma przypisana tabele albo
  precyzyjny visibility limit,
- tryb `COMPACT` opisuje najwazniejsze touchpoints bez udawania pelnego
  column mappingu,
- w trybie `DEEP` kazda zapisywana kolumna ma `SOURCE`,
- `SOURCE` nie zawiera nazw klas, metod, beanow, frameworkow, repozytoriow ani
  endpointow technicznych,
- join tables i kolekcje maja domkniete tabele elementow,
- source refs wskazuja kod, artefakty albo tool results, nie DDL/migracje,
- standardowe wnioskowanie ORM nie jest oznaczone jako limitation.

## Fallbacki

Jezeli brakuje required evidence:

- wykonaj najmniejszy focused GitLab read przez `flow-explorer-code-grounding`,
- jezeli tool jest niedostepny albo wynik nie rozstrzyga, zwroc czesciowy
  `PersistenceMappingSummary` i wpisz konkretny brak w `visibilityLimits`,
- nie wpisuj technicznego placeholdera do `SOURCE`.

Jezeli wynik jest niepewny:

- oznacz konkretny wiersz jako `inference` albo `visibility_gap`,
- wpisz, ktory typ, mapper, relacja, source wartosci albo kolumna wymaga
  potwierdzenia.

## Artefakty Handoffu

Gdy uzywa go orkiestrator albo `flow-explorer-write-report`, wystaw:

- `PersistenceMappingSummary`,
- liste source refs dla sekcji `PERSISTENCE`,
- visibility limits i open questions dotyczace tylko persistence.
