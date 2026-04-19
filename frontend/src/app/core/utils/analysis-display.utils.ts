import {
  AnalysisEvidenceAttribute,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisJobResponse,
  AnalysisJobStatus,
  AnalysisJobStepResponse,
  ExportState
} from '../models/analysis.models';

const TERMINAL_STATUSES = new Set<AnalysisJobStatus>(['COMPLETED', 'FAILED', 'NOT_FOUND']);

const STATUS_LABELS: Record<string, string> = {
  QUEUED: 'Oczekuje',
  COLLECTING_EVIDENCE: 'Zbieranie danych',
  ANALYZING: 'Analiza',
  COMPLETED: 'Zakończona',
  FAILED: 'Błąd',
  NOT_FOUND: 'Brak danych',
  PENDING: 'Oczekuje',
  IN_PROGRESS: 'W toku',
  SKIPPED: 'Pominięto'
};

const SECTION_TITLES: Record<string, string> = {
  'elasticsearch|logs': 'Elasticsearch · Logi',
  'deployment-context|resolved-deployment': 'Deployment · Kontekst deploymentu',
  'dynatrace|traces': 'Dynatrace · Trace',
  'dynatrace|runtime-signals': 'Dynatrace · Sygnały runtime',
  'exploratory-flow|reconstructed-flow': 'Exploratory · Zrekonstruowany flow',
  'gitlab|resolved-code': 'GitLab · Kod źródłowy',
  'operational-context|matched-context': 'Kontekst operacyjny · Dopasowania'
};

const LARGE_TEXT_ATTRIBUTES = new Set([
  'message',
  'exception',
  'content',
  'rationale',
  'evidenceSummary'
]);

export function isTerminalStatus(status: string | null | undefined): boolean {
  return TERMINAL_STATUSES.has(status ?? '');
}

export function formatStatus(status: string | null | undefined): string {
  return STATUS_LABELS[status ?? ''] || status || 'Nieznany';
}

export function statusClassName(status: string | null | undefined): string {
  if (status === 'COMPLETED') {
    return 'status-pill--done';
  }

  if (status === 'FAILED' || status === 'NOT_FOUND') {
    return 'status-pill--error';
  }

  if (status === 'COLLECTING_EVIDENCE' || status === 'ANALYZING') {
    return 'status-pill--running';
  }

  return 'status-pill--queued';
}

export function bannerClassName(status: string | null | undefined): string {
  if (status === 'FAILED' || status === 'NOT_FOUND') {
    return 'job-banner--error';
  }

  if (status === 'COMPLETED') {
    return 'job-banner--done';
  }

  return 'job-banner--running';
}

export function buildJobTitle(job: AnalysisJobResponse): string {
  return job.correlationId ? `Analiza ${job.correlationId}` : `Analiza ${shortId(job.analysisId)}`;
}

export function buildJobBannerMessage(job: AnalysisJobResponse): string {
  if (job.status === 'FAILED' || job.status === 'NOT_FOUND') {
    return job.errorMessage || 'Analiza zakończona błędem.';
  }

  if (job.status === 'COMPLETED') {
    return 'Analiza została zakończona. Wszystkie zebrane dane i wynik końcowy są już dostępne.';
  }

  if (job.currentStepLabel) {
    return `Aktualny krok: ${job.currentStepLabel}`;
  }

  return 'Analiza oczekuje na rozpoczęcie przetwarzania.';
}

export function buildStepMeta(step: AnalysisJobStepResponse): string[] {
  const meta: string[] = [];

  if (typeof step.itemCount === 'number') {
    meta.push(`Elementy: ${step.itemCount}`);
  }

  if (step.completedAt) {
    meta.push(`Zakończono: ${formatTime(step.completedAt)}`);
  } else if (step.startedAt) {
    meta.push(`Rozpoczęto: ${formatTime(step.startedAt)}`);
  }

  return meta;
}

export function formatTime(value: string | null | undefined): string {
  try {
    const date = new Date(value ?? '');
    if (Number.isNaN(date.getTime())) {
      return value ?? '';
    }

    return date.toLocaleTimeString('pl-PL', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  } catch {
    return value ?? '';
  }
}

export function formatDateTime(value: string | null | undefined): string {
  try {
    const date = new Date(value ?? '');
    if (Number.isNaN(date.getTime())) {
      return value ?? '';
    }

    return date.toLocaleString('pl-PL', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  } catch {
    return value ?? '';
  }
}

export function buildAnalysisActionsHint(state: ExportState | null): string {
  if (!state) {
    return 'Możesz wczytać zapis zakończonej analizy z pliku JSON.';
  }

  if (state.origin === 'imported') {
    let message = state.fileName
      ? `Wczytano zapis analizy z pliku ${state.fileName}.`
      : 'Wczytano zapis analizy z pliku JSON.';

    if (state.exportedAt) {
      message += ` Eksport utworzono: ${formatDateTime(state.exportedAt)}.`;
    }

    return message;
  }

  return 'Analiza zakończona. Możesz wyeksportować wynik do pliku JSON i udostępnić go dalej.';
}

export function formatEvidenceSectionTitle(section: AnalysisEvidenceSection): string {
  const key = `${String(section.provider || '')}|${String(section.category || '')}`;
  if (SECTION_TITLES[key]) {
    return SECTION_TITLES[key];
  }

  const tokens = [formatToken(section.provider), formatToken(section.category)].filter(Boolean);
  return tokens.length > 0 ? tokens.join(' · ') : 'Sekcja evidence';
}

export function buildEvidenceSectionKey(section: AnalysisEvidenceSection, index: number): string {
  return [section.provider || 'provider', section.category || 'category', index].join('|');
}

export function buildEvidenceItemKey(
  section: AnalysisEvidenceSection,
  item: AnalysisEvidenceItem,
  itemIndex: number
): string {
  return [
    section.provider || 'provider',
    section.category || 'category',
    item.title || 'item',
    itemIndex
  ].join('|');
}

export function isLargeAttribute(attribute: AnalysisEvidenceAttribute): boolean {
  return (
    LARGE_TEXT_ATTRIBUTES.has(attribute.name) ||
    Boolean(attribute.value && (attribute.value.includes('\n') || attribute.value.length > 180))
  );
}

export function hasMeaningfulValue(value: string | null | undefined): boolean {
  return Boolean(value && value.trim());
}

export function valueOrFallback(value: string | null | undefined): string {
  return value && value.trim() ? value : 'not-resolved';
}

export function shortId(value: string | null | undefined): string {
  if (!value) {
    return 'n/a';
  }

  return value.length > 12 ? `${value.substring(0, 12)}...` : value;
}

export function defaultErrorMessage(status: number): string {
  if (status === 404) {
    return 'Nie znaleziono wskazanego joba analizy lub danych diagnostycznych.';
  }

  if (status >= 500) {
    return 'Backend zwrócił błąd serwera podczas analizy incydentu.';
  }

  return 'Nie udało się przetworzyć żądania analizy.';
}

function formatToken(value: string | null | undefined): string {
  return String(value || '')
    .split(/[-_]/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(' ');
}
