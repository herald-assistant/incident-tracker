import { AnalysisReport, AnalysisReportSection } from '../../../core/models/analysis.models';
import {
  ConfigDriftViewerDeterministicContext,
  ConfigDriftViewerFinding,
  ConfigDriftViewerReference
} from '../models/config-drift-viewer.models';

export const CONFIG_DRIFT_FINDINGS_TITLE = 'Uwagi do porównania';
const FINDINGS_SECTION_ID = 'deterministic-findings';
type Side = 'source' | 'target';

export interface ConfigDriftFindingView {
  id: string;
  code: string;
  severity: string;
  severityLabel: string;
  title: string;
  description: string;
  locations: string[];
  links: { target: string; label: string }[];
}

type FindingMessage = Pick<ConfigDriftFindingView, 'title' | 'description' | 'locations'>;

const PARSER_MESSAGES: Record<string, { title: string; description: string }> = {
  YAML_DUPLICATE_KEY: {
    title: 'Ten sam klucz występuje więcej niż raz',
    description: 'Powtórzony klucz uniemożliwia jednoznaczny odczyt konfiguracji YAML. Usuń powtórzenie lub nadaj kluczom różne nazwy.'
  },
  VAR_DUPLICATE_KEY: {
    title: 'Ta sama zmienna jest zdefiniowana więcej niż raz',
    description: 'Powtórzona definicja uniemożliwia jednoznaczne ustalenie wartości. Sprawdź wskazane definicje i usuń konflikt.'
  },
  YAML_PARSE_ERROR: {
    title: 'Nie udało się odczytać struktury YAML',
    description: 'Analizator nie przetworzył całego pliku, więc porównanie może pomijać część ustawień. Sprawdź składnię i zgodność pliku z formatem obsługiwanym przez analizator.'
  },
  VAR_PARSE_ERROR: {
    title: 'Nie udało się odczytać struktury pliku zmiennych',
    description: 'Analizator nie przetworzył całego pliku, więc wartości zależne od niego mogą pozostać nieustalone. Sprawdź zapis wartości i zamknięcia bloków w podanej lokalizacji.'
  },
  VAR_UNEXPECTED_CLOSING_BRACE: {
    title: 'Zamknięcie bloku bez pasującego otwarcia',
    description: 'Analizator napotkał nawias zamykający bez rozpoznanego bloku. Sprawdź pary nawiasów oraz zapis poprzedzający wskazaną linię.'
  },
  VAR_UNSUPPORTED_SYNTAX: {
    title: 'Analizator nie obsługuje tej składni',
    description: 'Ten fragment nie został zinterpretowany. Nie przesądza to o błędzie konfiguracji; zweryfikuj go w narzędziu używanym do wdrożenia.'
  },
  VAR_UNSUPPORTED_EXPRESSION: {
    title: 'Analizator nie obsługuje tego wyrażenia',
    description: 'Analizator nie potrafi wyliczyć wartości tego wyrażenia. Nie przesądza to o błędzie konfiguracji; sprawdź jego wynik w narzędziu używanym do wdrożenia.'
  }
};

export function configDriftFindingSeverityLabel(severity: string): string {
  return ({ ERROR: 'Błąd', WARNING: 'Ostrzeżenie', INFO: 'Informacja' } as Record<string, string>)[severity] ?? 'Uwaga';
}

export function presentConfigDriftFindings(context: ConfigDriftViewerDeterministicContext): ConfigDriftFindingView[] {
  return context.findings.map(finding => ({
    id: finding.findingId,
    code: finding.code,
    severity: finding.severity,
    severityLabel: configDriftFindingSeverityLabel(finding.severity),
    ...findingMessage(finding, context),
    links: [
      ...finding.differenceIds.map((id, index) => ({
        target: id,
        label: linkLabel('Pokaż zmianę', context.differences.find(item => item.differenceId === id)?.path, index, finding.differenceIds.length)
      })),
      ...finding.referenceIds.map((id, index) => ({
        target: id,
        label: linkLabel('Pokaż powiązanie', context.references.find(item => item.referenceId === id)?.sourcePath, index, finding.referenceIds.length)
      }))
    ]
  }));
}

function findingMessage(finding: ConfigDriftViewerFinding, context: ConfigDriftViewerDeterministicContext): FindingMessage {
  if (finding.code === 'INCOMPLETE_SOURCE_COVERAGE' || finding.code === 'INCOMPLETE_TARGET_COVERAGE') {
    return coverageMessage(context, finding.code === 'INCOMPLETE_SOURCE_COVERAGE' ? 'source' : 'target');
  }
  const side: Side | null = finding.code.startsWith('SOURCE_') ? 'source'
    : finding.code.startsWith('TARGET_') ? 'target' : null;
  const parserMessage = side ? PARSER_MESSAGES[finding.code.replace(/^(SOURCE|TARGET)_/, '')] : null;
  if (parserMessage && side) {
    const linkedUnresolved = context.references.some(reference => finding.referenceIds.includes(reference.referenceId)
      && (side === 'source' ? reference.sourceStatus : reference.targetStatus) === 'UNRESOLVED');
    return {
      ...parserMessage,
      description: parserMessage.description + (linkedUnresolved
        ? ' Z tym samym problemem powiązano nieustaloną wartość zmiennej.' : ''),
      locations: [location(context, side, finding.filePath, finding.path, finding.line)]
    };
  }
  if (finding.code === 'UNRESOLVED_REFERENCE' || finding.code === 'CYCLIC_REFERENCE') {
    return referenceMessage(finding, context);
  }
  if (finding.code === 'HARDCODED_SENSITIVE_VALUE_ADDED') {
    const differences = context.differences.filter(item => finding.differenceIds.includes(item.differenceId));
    return {
      title: 'Dodano wrażliwą wartość bezpośrednio w konfiguracji',
      description: `W środowisku „${context.targetBranch}” dodano wartość oznaczoną jako wrażliwa, zamiast odwołania do bezpiecznego źródła. Sprawdź ją i zastąp takim odwołaniem przed wdrożeniem.`,
      locations: unique(differences.length ? differences.map(item => location(context, 'target',
        documentPath(context, 'target', item.role, item.documentIndex), item.path))
        : [location(context, 'target', finding.filePath, finding.path, finding.line)])
    };
  }
  return {
    title: 'Uwaga wymagająca sprawdzenia',
    description: 'Analiza zgłosiła uwagę, dla której nie ma jeszcze szczegółowego opisu. Sprawdź wskazane miejsce i dane diagnostyczne analizy; na tej podstawie nie można określić przyczyny ani wpływu na wynik.',
    locations: [location(context, side, finding.filePath, finding.path, finding.line)]
  };
}

function coverageMessage(context: ConfigDriftViewerDeterministicContext, side: Side): FindingMessage {
  const branch = side === 'source' ? context.sourceBranch : context.targetBranch;
  const coverage = side === 'source' ? context.sourceCoverage : context.targetCoverage;
  const title = `Nie udało się odczytać pełnej konfiguracji ${branch}`;
  if (!coverage) {
    return { title, description: 'W wyniku brakuje informacji o odczycie plików. Nie można potwierdzić kompletności porównania. Sprawdź przebieg pobierania konfiguracji.', locations: [branch] };
  }
  if (!coverage.branchExists) {
    return { title, description: `Nie znaleziono brancha „${branch}”. Nie można potwierdzić konfiguracji tego środowiska. Sprawdź nazwę brancha i jego obecność w wybranym repozytorium.`, locations: [branch] };
  }
  const unavailable = coverage.files.filter(file => file.status !== 'AVAILABLE');
  const descriptions = unavailable.map(file => {
    const path = file.path ? ` „${file.path}”` : '';
    switch (file.status) {
      case 'MISSING': return `Nie znaleziono pliku${path}. Sprawdź jego obecność w tym branchu.`;
      case 'AMBIGUOUS': return `Nie można jednoznacznie wybrać pliku${path}. Sprawdź, czy nie występuje kilka pasujących wariantów konfiguracji.`;
      case 'TRUNCATED': return `Odczyt pliku${path} jest niepełny. Sprawdź rozmiar pliku i ograniczenia odczytu.`;
      case 'ERROR': return `Nie udało się odczytać pliku${path}. Sprawdź dostępność repozytorium i możliwość odczytu pliku.`;
      case 'BRANCH_MISSING': return `Przy odczycie pliku${path} nie znaleziono brancha „${branch}”. Sprawdź jego obecność w repozytorium.`;
      default: return `Nie potwierdzono pełnego odczytu pliku${path}. Sprawdź szczegóły pobierania konfiguracji.`;
    }
  });
  if (!descriptions.length) {
    descriptions.push(coverage.files.length < 3
      ? `W wyniku są informacje o ${coverage.files.length} z 3 oczekiwanych plików. Sprawdź zakres pobranej konfiguracji.`
      : 'Wynik zgłasza niepełny odczyt, ale nie wskazuje konkretnego niedostępnego pliku. Sprawdź szczegóły pobierania konfiguracji.');
  }
  return {
    title,
    description: `${descriptions.join(' ')} Porównanie może pomijać część ustawień lub ich wartości.`,
    locations: unique(unavailable.length ? unavailable.map(file => location(context, side, file.path)) : [branch])
  };
}

function referenceMessage(finding: ConfigDriftViewerFinding, context: ConfigDriftViewerDeterministicContext): FindingMessage {
  const cyclic = finding.code === 'CYCLIC_REFERENCE';
  const status = cyclic ? 'CYCLIC' : 'UNRESOLVED';
  const affected: { reference: ConfigDriftViewerReference; side: Side }[] = [];
  for (const reference of context.references.filter(item => finding.referenceIds.includes(item.referenceId))) {
    if (reference.sourceStatus === status) affected.push({ reference, side: 'source' });
    if (reference.targetStatus === status) affected.push({ reference, side: 'target' });
  }
  const details = affected.map(({ reference, side }) => {
    const branch = side === 'source' ? context.sourceBranch : context.targetBranch;
    return cyclic
      ? `W środowisku „${branch}” odwołanie z „${reference.sourcePath}” do „${reference.targetPath}” prowadzi do pętli zależności.`
      : `W środowisku „${branch}” nie ustalono wartości „${reference.targetPath}” używanej przez „${reference.sourcePath}”.`;
  });
  return {
    title: cyclic ? 'Zmienne odwołują się do siebie w pętli' : 'Nie udało się ustalić wartości parametru',
    description: [
      ...unique(details),
      !affected.length ? 'Wynik nie wskazuje szczegółów powiązania ani strony, której dotyczy problem.' : '',
      cyclic ? 'Nie można wyliczyć końcowej wartości. Sprawdź definicje zmiennych i przerwij pętlę odwołań.'
        : 'Porównanie efektywnej wartości jest niepełne. Sprawdź definicję zmiennej i jej zależności.'
    ].filter(Boolean).join(' '),
    locations: unique(affected.length ? affected.map(({ reference, side }) => location(context, side,
      documentPath(context, side, reference.sourceRole, reference.documentIndex), reference.sourcePath))
      : [location(context, null, finding.filePath, finding.path, finding.line)])
  };
}

function documentPath(context: ConfigDriftViewerDeterministicContext, side: Side, role: string, index: number): string | null {
  const document = context.documents.find(item => item.role === role && item.documentIndex === index);
  return (side === 'source' ? document?.sourcePath : document?.targetPath) ?? null;
}

function location(context: ConfigDriftViewerDeterministicContext, side: Side | null, file?: string | null, path?: string, line?: number | null): string {
  const branch = side === 'source' ? context.sourceBranch : side === 'target' ? context.targetBranch : '';
  const fileLocation = file ? `${file}${line && line > 0 ? `:${line}` : ''}` : '';
  return [branch, fileLocation, path ? `parametr ${path}` : ''].filter(Boolean).join(' · ') || 'Lokalizacja nie została wskazana w wyniku.';
}

function linkLabel(action: string, path: string | undefined, index: number, count: number): string {
  return path ? `${action}: ${path}` : `${action}${count > 1 ? ` ${index + 1}` : ''}`;
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

export function configDriftFindingsMarkdown(findings: ConfigDriftFindingView[]): string {
  return findings.map(finding => [
    `### ${markdownText(finding.severityLabel)} · ${markdownText(finding.title)}`,
    markdownText(finding.description),
    `**Lokalizacja:**\n${finding.locations.map(value => `- ${markdownText(value)}`).join('\n')}`
  ].join('\n\n')).join('\n\n');
}

function markdownText(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/[\\`*_[\]]/g, '\\$&').replace(/\r?\n/g, ' ');
}

export function presentConfigDriftFindingsReport(report: AnalysisReport, findings: ConfigDriftFindingView[]): AnalysisReport {
  const section = report.sections.find(item => item.id === FINDINGS_SECTION_ID);
  const presented: AnalysisReportSection = {
    id: FINDINGS_SECTION_ID,
    title: CONFIG_DRIFT_FINDINGS_TITLE,
    order: section?.order ?? Math.max(-1, ...report.sections.map(item => item.order ?? 0)) + 1,
    markdown: configDriftFindingsMarkdown(findings),
    meta: section?.meta ?? {
      references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: '', warnings: []
    }
  };
  return {
    ...report,
    sections: section
      ? report.sections.flatMap(item => item.id !== FINDINGS_SECTION_ID ? [item] : findings.length ? [presented] : [])
      : [...report.sections, ...(findings.length ? [presented] : [])]
  };
}
