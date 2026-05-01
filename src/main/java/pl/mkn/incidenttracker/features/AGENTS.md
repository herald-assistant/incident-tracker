# AGENTS

## Zakres

Ten katalog zawiera dedykowane feature'y analityczne zbudowane na reusable
warstwach: `aiplatform`, `agenttools`, `integrations`, `shared` i `common`.

Obecnie pierwszy feature to `incidentanalysis`.

## Zasady

- Feature moze zalezec od platformy AI, reusable tools, integracji oraz malych
  kontraktow shared/common.
- Platforma AI, tools, integracje, shared i common nie moga importowac
  `features.*`.
- `features.incidentanalysis` jest wlascicielem incident promptu, digestu,
  response parsera, quality gate, coverage heurystyk, incident tool policy,
  hidden tool contextu i mapowania incident artifacts na platformowy run
  request oraz user-facing tool evidence.
- Nie traktuj `features.incidentanalysis` jako generycznego core dla kolejnych
  analiz. Nowy feature powinien dostarczyc wlasny prompt, skille, policy,
  hidden context i kontrakt odpowiedzi.
- URL-e publiczne moga nadal uzywac product-facing nazwy `analysis`, nawet gdy
  implementacja Javy mieszka pod `features.incidentanalysis`.

## Stan Przejsciowy

- `features.incidentanalysis.ai.copilot.preparation` zawiera przeniesione
  incident preparation dla initial/follow-up runow Copilota.
- `features.incidentanalysis.ai.copilot.coverage` zawiera incident-specific
  coverage/gap evaluation.
- `features.incidentanalysis.ai.copilot.response` i
  `features.incidentanalysis.ai.copilot.quality` zawieraja incidentowy
  JSON-only response contract, parser oraz report-only quality gate.
- `features.incidentanalysis.ai.copilot.tools` zawiera incident-specific
  GitLab/DB tool evidence capture nad generycznymi eventami invocation.
- Czesc kontraktow AI, job, flow i evidence nadal mieszka w `analysis.*`.
  Przenos kolejne fragmenty inkrementalnie, bez zmian zachowania runtime.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby reusable warstwy nie importowaly
  `features.*`.
