# Operational Context Frontend AGENTS

## Rola ekranu

`/operational-context` jest ekranem `Tool Workbench / Operational Context`.
To shared context/catalog capability dla feature'ow i tools, nie osobny
produktowy feature i nie element sekcji `Platform`.

## API

Uzywaj istniejacych shared/operator endpointow
`/api/operational-context/*`. Ten ekran nie powinien zmieniac modelu danych ani
dodawac incident-specific scope'u.

Operational Context tools sa neutralne `opctx_*` i nie przyjmuja
`correlationId`, `environment`, `gitLabGroup` ani `gitLabBranch` jako
model-facing input. Frontend nie powinien sugerowac inaczej.

## Layout

Ekran powinien pozostac roboczy i gesty informacyjnie:

- kompaktowy status strip,
- zakladki katalogowe,
- Signal Resolver,
- katalogowe listy encji,
- `Validation` jako inbox utrzymaniowy,
- `Open Questions` jako inbox utrzymaniowy,
- prawy detail drawer.

Nie dodawaj lokalnego hero ani `workbench-header`.

## Validation i Open Questions

Maintenance inbox ma priorytetowac:

- severity/status,
- krotki tytul/pytanie,
- entity target,
- source file/path,
- akcje kopiowania maintenance targetu.

Filtry powinny zawężac aktualny inbox bez zmiany API. Kopiowanie targetu ma
dawac operatorowi konkretna sciezke/fakt do poprawy katalogu.

## Detail drawer

Szczegoly encji pokazuj w prawym drawerze, nie w modalu. Drawer ma stale akcje:

- `Copy`,
- `Open raw`,
- `Close`.

Raw source preview i read model payloads moga byc przewijane w drawerze.
Drawer nie powinien zajmowac calego desktopowego ekranu, chyba ze wymusza to
mobile viewport.
