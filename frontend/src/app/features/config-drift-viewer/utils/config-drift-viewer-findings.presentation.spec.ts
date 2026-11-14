import { marked } from 'marked';
import { AnalysisReport } from '../../../core/models/analysis.models';
import { buildReportShareDocument } from '../../../core/utils/analysis-share.utils';
import {
  ConfigDriftViewerBranchCoverage,
  ConfigDriftViewerDeterministicContext,
  ConfigDriftViewerFinding,
  SanitizedConfigurationNode
} from '../models/config-drift-viewer.models';
import {
  configDriftFindingSeverityLabel,
  configDriftFindingsMarkdown,
  presentConfigDriftFindings,
  presentConfigDriftFindingsReport
} from './config-drift-viewer-findings.presentation';

describe('Config Drift finding presentation', () => {
  it.each([
    ['MISSING', 'Nie znaleziono pliku'],
    ['AMBIGUOUS', 'Nie można jednoznacznie wybrać pliku'],
    ['TRUNCATED', 'Odczyt pliku'],
    ['ERROR', 'Nie udało się odczytać pliku'],
    ['BRANCH_MISSING', 'nie znaleziono brancha'],
    ['FUTURE_FILE_STATUS', 'Nie potwierdzono pełnego odczytu']
  ])('explains target coverage from the actual %s file status', (status, message) => {
    const data = context([finding('INCOMPLETE_TARGET_COVERAGE', { path: '' })]);
    data.targetCoverage!.files[1].status = status;
    const [view] = presentConfigDriftFindings(data);

    expect(view.title).toBe('Nie udało się odczytać pełnej konfiguracji test');
    expect(view.description).toContain(message);
    expect(view.description).toContain('crm/customer-service/local.var');
    expect(view.description).toContain('Porównanie może pomijać');
    expect(view.locations).toEqual(['test · crm/customer-service/local.var']);
    expect(view.severityLabel).toBe('Błąd');
    const markdown = configDriftFindingsMarkdown([view]);
    expect(markdown).not.toContain('INCOMPLETE_TARGET_COVERAGE');
    expect(markdown).not.toContain('finding-test');
    if (status !== 'MISSING') expect(view.description).not.toContain('Nie znaleziono pliku');
    expect(view.description).not.toContain('global.var');
  });

  it('explains missing source branch without claiming a missing target file', () => {
    const data = context([finding('INCOMPLETE_SOURCE_COVERAGE', { path: '' })]);
    data.sourceCoverage!.branchExists = false;
    const [view] = presentConfigDriftFindings(data);
    expect(view.description).toContain('Nie znaleziono brancha „dev”');
    expect(view.description).not.toContain('Nie znaleziono pliku');
    expect(view.locations).toEqual(['dev']);
  });

  it('keeps missing coverage and partial file manifests explicit instead of inventing a cause', () => {
    const data = context([finding('INCOMPLETE_TARGET_COVERAGE', { path: '' })]);
    data.targetCoverage = null;
    expect(presentConfigDriftFindings(data)[0].description).toContain('brakuje informacji o odczycie plików');
    data.targetCoverage = coverage('test');
    data.targetCoverage.files.pop();
    expect(presentConfigDriftFindings(data)[0].description).toContain('2 z 3 oczekiwanych plików');
    expect(presentConfigDriftFindings(data)[0].description).not.toContain('Nie znaleziono');
  });

  const parserCases = [
    ['YAML_DUPLICATE_KEY', 'Ten sam klucz występuje więcej niż raz'],
    ['VAR_DUPLICATE_KEY', 'Ta sama zmienna jest zdefiniowana więcej niż raz'],
    ['YAML_PARSE_ERROR', 'Nie udało się odczytać struktury YAML'],
    ['VAR_PARSE_ERROR', 'Nie udało się odczytać struktury pliku zmiennych'],
    ['VAR_UNEXPECTED_CLOSING_BRACE', 'Zamknięcie bloku bez pasującego otwarcia'],
    ['VAR_UNSUPPORTED_SYNTAX', 'Analizator nie obsługuje tej składni'],
    ['VAR_UNSUPPORTED_EXPRESSION', 'Analizator nie obsługuje tego wyrażenia']
  ];
  it.each(parserCases.flatMap(([code, title]) => ['SOURCE', 'TARGET'].map(side => ({ code, title, side }))))(
    'describes $side $code with its recorded location', ({ code, title, side }) => {
      const data = context([finding(`${side}_${code}`, { filePath: 'crm/customer-service/local.var', line: 7 })]);
      const [view] = presentConfigDriftFindings(data);
      expect(view.title).toBe(title);
      expect(view.description.length).toBeGreaterThan(30);
      expect(view.locations).toEqual([`${side === 'SOURCE' ? 'dev' : 'test'} · crm/customer-service/local.var:7 · parametr timeouts.read`]);
      expect(configDriftFindingsMarkdown([view])).not.toContain(code);
      if (code.includes('UNSUPPORTED')) expect(view.description).toContain('Nie przesądza to o błędzie konfiguracji');
    }
  );

  it.each([
    { sourceStatus: 'UNRESOLVED', targetStatus: 'RESOLVED', branches: ['dev'] },
    { sourceStatus: 'RESOLVED', targetStatus: 'UNRESOLVED', branches: ['test'] },
    { sourceStatus: 'UNRESOLVED', targetStatus: 'UNRESOLVED', branches: ['dev', 'test'] }
  ])('locates unresolved values only on the affected side: $branches', ({ sourceStatus, targetStatus, branches }) => {
    const data = referenceContext('UNRESOLVED_REFERENCE', sourceStatus, targetStatus);
    const [view] = presentConfigDriftFindings(data);
    expect(view.title).toBe('Nie udało się ustalić wartości parametru');
    expect(view.description).toContain('„local.read_timeout” używanej przez „timeouts.read”');
    expect(view.locations).toEqual(branches.map(branch => `${branch} · crm/customer-service/application.yml.kv · parametr timeouts.read`));
    expect(view.links).toEqual([{ target: 'reference-timeout', label: 'Pokaż powiązanie: timeouts.read' }]);
    expect(view.description).not.toContain('Nie znaleziono');
  });

  it('describes a cycle without claiming an absent variable', () => {
    const [view] = presentConfigDriftFindings(referenceContext('CYCLIC_REFERENCE', 'RESOLVED', 'CYCLIC'));
    expect(view.title).toBe('Zmienne odwołują się do siebie w pętli');
    expect(view.description).toContain('przerwij pętlę odwołań');
    expect(view.locations[0]).toContain('test ·');
    expect(view.description).not.toContain('brakuje');
  });

  it('links parser consequences only when the matching unresolved reference is present', () => {
    const data = referenceContext('TARGET_VAR_UNSUPPORTED_SYNTAX', 'RESOLVED', 'UNRESOLVED');
    expect(presentConfigDriftFindings(data)[0].description).toContain('Z tym samym problemem');
    data.references = [];
    expect(presentConfigDriftFindings(data)[0].description).not.toContain('Z tym samym problemem');
  });

  it('does not invent the environment or file for a dangling reference', () => {
    const [view] = presentConfigDriftFindings(context([finding('UNRESOLVED_REFERENCE', { referenceIds: ['missing-reference'] })]));
    expect(view.description).toContain('nie wskazuje szczegółów powiązania ani strony');
    expect(view.locations).toEqual(['parametr timeouts.read']);
    expect(view.links[0].label).toBe('Pokaż powiązanie');
    expect(configDriftFindingsMarkdown([view])).not.toContain('missing-reference');
  });

  it('describes a sensitive addition without including its value or token', () => {
    const data = context([finding('HARDCODED_SENSITIVE_VALUE_ADDED', { path: 'auth.credential', differenceIds: ['difference-credential'] })]);
    data.differences = [{
      differenceId: 'difference-credential', role: 'APPLICATION_YAML', documentIndex: 0,
      path: 'auth.credential', kind: 'ADDED', sourceType: null, targetType: 'STRING',
      sensitivity: 'SENSITIVE', sourceValueToken: null, targetValueToken: 'redacted-value-token'
    }];
    const [view] = presentConfigDriftFindings(data);
    expect(view.title).toContain('wrażliwą wartość');
    expect(view.description).toContain('„test”');
    expect(view.locations).toEqual(['test · crm/customer-service/application.yml.kv · parametr auth.credential']);
    expect(view.links[0].target).toBe('difference-credential');
    expect(JSON.stringify(view)).not.toContain('redacted-value-token');
  });

  it('uses a safe readable fallback for an unknown finding and unknown severity', () => {
    const [view] = presentConfigDriftFindings(context([finding('FUTURE_FINDING', { path: '', severity: 'FUTURE_SEVERITY' })]));
    expect(view.title).toBe('Uwaga wymagająca sprawdzenia');
    expect(view.severityLabel).toBe('Uwaga');
    expect(view.locations).toEqual(['Lokalizacja nie została wskazana w wyniku.']);
    expect(configDriftFindingsMarkdown([view])).not.toMatch(/FUTURE_|finding-test/);
    expect(view.code).toBe('FUTURE_FINDING');
  });

  it.each([['ERROR', 'Błąd'], ['WARNING', 'Ostrzeżenie'], ['INFO', 'Informacja']])('labels %s as %s', (severity, label) => {
    expect(configDriftFindingSeverityLabel(severity)).toBe(label);
  });

  it('escapes file and parameter text in shared Markdown', () => {
    const data = context([finding('TARGET_YAML_PARSE_ERROR', { filePath: '<b>[crm](https://crm.example.com).yml', path: 'x_*_y' })]);
    const markdown = configDriftFindingsMarkdown(presentConfigDriftFindings(data));
    expect(markdown).toContain('&lt;b&gt;\\[crm\\]');
    expect(markdown).toContain('x\\_\\*\\_y');
    expect(markdown).not.toContain('<b>');
  });
});

describe('Config Drift report findings presentation', () => {
  it('uses the same messages in report and share without modifying the saved report or other sections', () => {
    const original = report();
    const views = presentConfigDriftFindings(referenceContext('UNRESOLVED_REFERENCE', 'RESOLVED', 'UNRESOLVED'));
    const presented = presentConfigDriftFindingsReport(original, views);
    const section = presented.sections[0];
    expect(section.title).toBe('Uwagi do porównania');
    expect(section.id).toBe('deterministic-findings');
    expect(section.markdown).toBe(configDriftFindingsMarkdown(views));
    expect(section.meta).toBe(original.sections[0].meta);
    expect(presented.sections[1]).toBe(original.sections[1]);
    expect(original.sections[0].markdown).toContain('UNRESOLVED_REFERENCE');
    const shared = buildReportShareDocument(presented, 'comparison.md');
    const rendered = document.createElement('div');
    rendered.innerHTML = marked.parse(shared.markdown, { async: false });
    expect(rendered.textContent).toContain(views[0].title);
    expect(rendered.textContent).toContain(views[0].description);
    expect(rendered.textContent).toContain(views[0].locations[0]);
    expect(shared.markdown).not.toContain('UNRESOLVED_REFERENCE');
    expect(shared.markdown).not.toContain('finding-test');
    expect(shared.markdownWithMeta).toContain('UNRESOLVED_REFERENCE');
  });

  it('omits empty notes in report/share and appends nonempty notes to a report without that section', () => {
    expect(configDriftFindingsMarkdown([])).toBe('');
    expect(presentConfigDriftFindingsReport(report(), []).sections.map(item => item.id)).toEqual(['ai-second-opinion']);
    const original = { ...report(), sections: [report().sections[1]] };
    const views = presentConfigDriftFindings(context([finding('INCOMPLETE_TARGET_COVERAGE')]));
    const presented = presentConfigDriftFindingsReport(original, views);
    expect(presented.sections.map(item => item.id)).toEqual(['ai-second-opinion', 'deterministic-findings']);
    expect(presented.sections[1].order).toBe(3);
  });
});

function finding(code: string, overrides: Partial<ConfigDriftViewerFinding> = {}): ConfigDriftViewerFinding {
  return { findingId: 'finding-test', code, severity: 'ERROR', path: 'timeouts.read', differenceIds: [], referenceIds: [], ...overrides };
}

function coverage(branch: string): ConfigDriftViewerBranchCoverage {
  return { branch, branchExists: true, files: [
    { role: 'GLOBAL_VAR', path: 'global.var' },
    { role: 'LOCAL_VAR', path: 'crm/customer-service/local.var' },
    { role: 'APPLICATION_YAML', path: 'crm/customer-service/application.yml.kv' }
  ].map(file => ({ ...file, status: 'AVAILABLE', commitId: 'crm-revision', lastCommitId: 'crm-revision', lastModifiedAt: null, sizeBytes: 128, errorCode: null })) };
}

function context(findings: ConfigDriftViewerFinding[]): ConfigDriftViewerDeterministicContext {
  const root: SanitizedConfigurationNode = {
    name: 'root', path: '', sourceType: 'MAP', targetType: 'MAP', relation: 'EQUAL', sensitivity: 'PUBLIC',
    sourceValueToken: null, targetValueToken: null, sourceCardinality: 0, targetCardinality: 0, children: []
  };
  return {
    repositoryId: 'crm-config', systemId: 'customer-service', systemLabel: 'Customer Service', configurationDirectory: 'crm/customer-service',
    sourceBranch: 'dev', targetBranch: 'test', status: 'INCOMPLETE',
    sourceCoverage: coverage('dev'), targetCoverage: coverage('test'),
    documents: [{ role: 'APPLICATION_YAML', sourcePath: 'crm/customer-service/application.yml.kv', targetPath: 'crm/customer-service/application.yml.kv',
      documentIndex: 0, sourcePresent: true, targetPresent: true, sourceProfileToken: null, targetProfileToken: null, root }],
    references: [], differences: [], findings
  };
}

function referenceContext(code: string, sourceStatus: string, targetStatus: string): ConfigDriftViewerDeterministicContext {
  return { ...context([finding(code, { referenceIds: ['reference-timeout'] })]), references: [{
    referenceId: 'reference-timeout', sourceRole: 'APPLICATION_YAML', documentIndex: 0,
    sourcePath: 'timeouts.read', targetPath: 'local.read_timeout', referenceKind: 'SPRING_PLACEHOLDER', sourceStatus, targetStatus
  }] };
}

function report(): AnalysisReport {
  const meta = { references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: '', warnings: [] };
  return { reportId: 'crm-report', header: 'Porównanie CRM', subHeader: 'dev → test', markdownSummary: 'Wynik porównania.', meta,
    sections: [
      { id: 'deterministic-findings', title: 'Deterministic findings', order: 1, markdown: 'finding-test · UNRESOLVED_REFERENCE',
        meta: { ...meta, references: [{ type: 'finding', label: 'finding-test', target: 'finding-test', description: 'UNRESOLVED_REFERENCE' }] } },
      { id: 'ai-second-opinion', title: 'Druga opinia AI', order: 2, markdown: 'Sprawdź konfigurację limitów czasu.', meta }
    ] };
}
