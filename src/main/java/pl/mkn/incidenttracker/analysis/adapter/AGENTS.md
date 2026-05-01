# AGENTS

## Zakres

Ten katalog jest historyczny po przeniesieniu adapterow do
`pl.mkn.incidenttracker.integrations`. Nie dodawaj tu nowego kodu Java.

Obejmuje tylko ten plik z instrukcja-retencja, zeby nie przywracac starych
pakietow przez przypadek.

Aktualne capability adapters mieszkaja w:

- `pl.mkn.incidenttracker.integrations.dynatrace`
- `pl.mkn.incidenttracker.integrations.elasticsearch`
- `pl.mkn.incidenttracker.integrations.gitlab`
- `pl.mkn.incidenttracker.integrations.operationalcontext`
- `pl.mkn.incidenttracker.integrations.database`

## Zasady modyfikacji

- Nie przywracaj podpakietow `analysis.adapter.*`.
- Nowe lub przenoszone integracje dodawaj pod `integrations.*`.
- Jesli trafisz na import `pl.mkn.incidenttracker.analysis.adapter.*`, traktuj
  go jako drift do migracji, nie jako wzorzec.

## Testy

- `PackageDependencyGuardTest` pilnuje, zeby `integrations.*` nie importowalo
  warstw aplikacyjnych.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/01-system-overview.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/architecture/05-package-dependencies.md`
- `docs/architecture/06-modular-architecture-roadmap.md`
- `docs/onboarding/07-adapters-and-external-systems.md`
