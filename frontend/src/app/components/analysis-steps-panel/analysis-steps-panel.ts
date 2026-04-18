import { STEPPER_GLOBAL_OPTIONS } from '@angular/cdk/stepper';
import { Component, computed, effect, input, signal } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  AnalysisEvidenceAttribute,
  AnalysisEvidenceReference,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisResultResponse
} from '../../core/models/analysis.models';
import {
  ResizableColumnConfig,
  ResizableColumnDirective,
  ResizableColumnsHostDirective
} from '../../core/directives/resizable-columns.directive';
import {
  buildEvidenceItemKey,
  buildEvidenceSectionKey,
  buildStepMeta,
  formatDateTime,
  formatEvidenceSectionTitle,
  formatStatus,
  hasMeaningfulValue,
  isLargeAttribute
} from '../../core/utils/analysis-display.utils';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

interface StepEvidenceLink {
  provider: string;
  category: string;
}

interface StepEvidenceItemView {
  key: string;
  title: string;
  attributeCount: number;
  compactAttributes: AnalysisEvidenceAttribute[];
  blockAttributes: AnalysisEvidenceAttribute[];
}

interface DynatraceServiceMatchView {
  key: string;
  title: string;
  summary: string;
  displayName: string;
  logicalServiceKey: string;
  logicalServiceTitle: string;
  operationName: string;
  entityId: string;
  matchScore: string;
  incidentWindow: string;
  namespaces: string[];
  pods: string[];
  containers: string[];
  serviceNames: string[];
}

interface DynatraceProblemView {
  key: string;
  displayId: string;
  title: string;
  summary: string;
  severityLabel: string;
  statusLabel: string;
  impactLabel: string;
  severityLevel: string;
  status: string;
  impactLevel: string;
  timeWindow: string;
  rootCause: string;
  signalCategories: string[];
  correlationHighlights: string[];
  affectedEntities: string[];
  impactedEntities: string[];
  evidenceLines: string[];
}

interface DynatraceMetricView {
  key: string;
  title: string;
  summary: string;
  logicalServiceKey: string;
  logicalServiceTitle: string;
  operationName: string;
  metricLabel: string;
  entityDisplayName: string;
  entityId: string;
  metricId: string;
  unit: string;
  resolution: string;
  queryWindow: string;
  nonNullPoints: string;
  minValue: string;
  maxValue: string;
  averageValue: string;
  lastValue: string;
}

interface DynatraceServiceGroupView {
  key: string;
  title: string;
  summary: string;
  technicalMatchCount: number;
  topScore: string;
  incidentWindow: string;
  namespaces: string[];
  pods: string[];
  containers: string[];
  serviceNames: string[];
  operationCount: number;
  topOperations: string[];
}

interface DynatraceMetricAggregateView {
  key: string;
  title: string;
  summary: string;
  serviceTitle: string;
  technicalMetricCount: number;
  resolution: string;
  unit: string;
  worstValueLabel: string;
  worstValue: string;
  averageValueLabel: string;
  averageValue: string;
  latestValueLabel: string;
  latestValue: string;
  worstContext: string;
  exampleOperations: string[];
}

interface DynatraceRuntimeSectionView {
  takeaways: string[];
  serviceGroups: DynatraceServiceGroupView[];
  metricAggregates: DynatraceMetricAggregateView[];
  serviceMatches: DynatraceServiceMatchView[];
  problems: DynatraceProblemView[];
  metrics: DynatraceMetricView[];
}

interface ElasticsearchLogRowView {
  key: string;
  title: string;
  containerName: string;
  timestamp: string;
  level: string;
  className: string;
  message: string;
  exception: string;
  hasErrorStyling: boolean;
  detailsTooltip: string;
}

interface RepoCodeLineView {
  lineNumber: number | null;
  content: string;
  highlighted: boolean;
}

interface RepoCodePanelView {
  key: string;
  headerTitle: string;
  componentName: string;
  detailsTooltip: string;
  hasContent: boolean;
  highlightedLineNumber: number | null;
  codeLines: RepoCodeLineView[];
  compactAttributes: AnalysisEvidenceAttribute[];
  blockAttributes: AnalysisEvidenceAttribute[];
}

interface StepEvidenceSectionView {
  key: string;
  title: string;
  itemCount: number;
  variant:
    | 'default'
    | 'elasticsearch-log-table'
    | 'gitlab-code-panels'
    | 'dynatrace-runtime-panels';
  items: StepEvidenceItemView[];
  logRows: ElasticsearchLogRowView[];
  codePanels: RepoCodePanelView[];
  dynatraceRuntime: DynatraceRuntimeSectionView | null;
}

interface StepView {
  key: string;
  code: string;
  label: string;
  status: string;
  statusLabel: string;
  statusClass: string;
  indicatorState: StepIndicatorState;
  headerMeta: string;
  meta: string[];
  message: string;
  canOpen: boolean;
  detailSections: StepEvidenceSectionView[];
  showResultPreview: boolean;
  showPreparedPromptView: boolean;
  preparedPrompt: string;
  promptPanelTitle: string;
  promptPanelDescription: string;
  emptyStateMessage: string;
}

type StepIndicatorState = 'number' | 'running' | 'done' | 'error' | 'skipped';

const STEP_EVIDENCE_LINKS: Record<string, readonly StepEvidenceLink[]> = {
  ELASTICSEARCH_LOGS: [{ provider: 'elasticsearch', category: 'logs' }],
  DEPLOYMENT_CONTEXT: [{ provider: 'deployment-context', category: 'resolved-deployment' }],
  DYNATRACE_TRACES: [{ provider: 'dynatrace', category: 'traces' }],
  DYNATRACE_RUNTIME_SIGNALS: [{ provider: 'dynatrace', category: 'runtime-signals' }],
  GITLAB_RESOLVED_CODE: [{ provider: 'gitlab', category: 'resolved-code' }],
  OPERATIONAL_CONTEXT: [{ provider: 'operational-context', category: 'matched-context' }]
};

const LOG_TABLE_ATTRIBUTE_ORDER: Record<string, number> = {
  containerName: 10,
  timestamp: 20,
  level: 30,
  className: 40,
  message: 50,
  exception: 60,
  thread: 70,
  serviceName: 80,
  spanId: 90,
  namespace: 100,
  podName: 110,
  containerImage: 120
};

const GITLAB_ATTRIBUTE_ORDER: Record<string, number> = {
  projectName: 10,
  containerName: 20,
  environment: 30,
  branch: 40,
  group: 50,
  filePath: 60,
  lineNumber: 70,
  referenceType: 80,
  symbol: 90,
  rawReference: 100,
  resolveScore: 110,
  requestedStartLine: 120,
  requestedEndLine: 130,
  returnedStartLine: 140,
  returnedEndLine: 150,
  totalLines: 160,
  commitSha: 170,
  containerImage: 180,
  contentTruncated: 190,
  content: 999
};

const LOG_TABLE_COLUMNS: readonly ResizableColumnConfig[] = [
  {
    id: 'containerName',
    defaultTrack: 'minmax(110px, 0.65fr)',
    minWidth: 110
  },
  {
    id: 'timestamp',
    defaultTrack: 'minmax(130px, 0.75fr)',
    minWidth: 130
  },
  {
    id: 'level',
    defaultTrack: 'minmax(72px, 0.35fr)',
    minWidth: 72
  },
  {
    id: 'className',
    defaultTrack: 'minmax(140px, 0.8fr)',
    minWidth: 140
  },
  {
    id: 'message',
    defaultTrack: 'minmax(420px, 4fr)',
    minWidth: 420
  },
  {
    id: 'details',
    defaultTrack: '80px',
    minWidth: 64
  }
] as const;

@Component({
  selector: 'app-analysis-steps-panel',
  imports: [
    MatExpansionModule,
    MatIconModule,
    MatStepperModule,
    MatTooltipModule,
    ResizableColumnDirective,
    ResizableColumnsHostDirective,
    MarkdownContentComponent
  ],
  templateUrl: './analysis-steps-panel.html',
  styleUrl: './analysis-steps-panel.scss',
  providers: [
    {
      provide: STEPPER_GLOBAL_OPTIONS,
      useValue: {
        showError: true,
        displayDefaultIndicatorType: false
      }
    }
  ]
})
export class AnalysisStepsPanelComponent {
  readonly steps = input<AnalysisJobStepResponse[]>([]);
  readonly evidenceSections = input<AnalysisEvidenceSection[]>([]);
  readonly preparedPrompt = input<string>('');
  readonly result = input<AnalysisResultResponse | null>(null);

  private readonly selectedStepKey = signal<string | null>(null);
  private readonly copiedLogRowKey = signal<string | null>(null);
  private readonly copiedPromptStepKey = signal<string | null>(null);
  private logCopyFeedbackHandle: number | null = null;
  private promptCopyFeedbackHandle: number | null = null;

  protected readonly preparedSteps = computed<StepView[]>(() =>
    this.steps().map((step, index) => {
      const detailSections = prepareEvidenceSections(resolveStepSections(step, this.evidenceSections()));
      const showResultPreview = step.code === 'AI_ANALYSIS' && this.result() !== null;
      const stepPreparedPrompt = resolvePreparedPrompt(
        step.code,
        this.preparedPrompt(),
        this.result()?.prompt ?? ''
      );
      const showPreparedPromptView = Boolean(stepPreparedPrompt);
      const key = buildStepKey(step, index);

      return {
        key,
        code: step.code || key,
        label: step.label || `Krok ${index + 1}`,
        status: step.status,
        statusLabel: formatStatus(step.status),
        statusClass: stepStatusClass(step.status),
        indicatorState: buildStepIndicatorState(step.status),
        headerMeta: buildHeaderMeta(step),
        meta: [...buildStepMeta(step), ...buildEvidenceFlowMeta(step)],
        message: step.message || buildFallbackStepMessage(step.status),
        canOpen: step.status === 'COMPLETED',
        detailSections,
        showResultPreview,
        showPreparedPromptView,
        preparedPrompt: stepPreparedPrompt,
        promptPanelTitle: buildPreparedPromptTitle(step.code),
        promptPanelDescription: buildPreparedPromptDescription(step.code),
        emptyStateMessage: buildEmptyStateMessage(
          step,
          detailSections.length,
          showResultPreview,
          showPreparedPromptView
        )
      };
    })
  );

  protected readonly selectedIndex = computed(() => {
    const steps = this.preparedSteps();
    const selectedStepKey = this.selectedStepKey();

    if (steps.length === 0) {
      return 0;
    }

    const index = steps.findIndex((step) => step.key === selectedStepKey);
    return index >= 0 ? index : 0;
  });

  protected readonly selectedStep = computed(
    () => this.preparedSteps()[this.selectedIndex()] ?? null
  );

  protected readonly hasMeaningfulValue = hasMeaningfulValue;
  protected readonly copiedRowKey = this.copiedLogRowKey.asReadonly();
  protected readonly copiedPromptKey = this.copiedPromptStepKey.asReadonly();
  protected readonly logTableColumns = LOG_TABLE_COLUMNS;
  protected readonly formatDynatraceSignalCategoryLabel = formatDynatraceSignalCategoryLabel;

  constructor() {
    effect(
      () => {
        const steps = this.preparedSteps();
        const selectedStepKey = this.selectedStepKey();

        if (steps.length === 0) {
          if (selectedStepKey !== null) {
            this.selectedStepKey.set(null);
          }
          return;
        }

        if (selectedStepKey && steps.some((step) => step.key === selectedStepKey)) {
          return;
        }

        this.selectedStepKey.set(selectDefaultStepKey(steps));
      },
      { allowSignalWrites: true }
    );
  }

  protected onSelectedIndexChange(index: number): void {
    const nextStep = this.preparedSteps()[index];
    if (!nextStep) {
      return;
    }

    const currentStep = this.selectedStep();
    if (nextStep.canOpen || !currentStep) {
      this.selectedStepKey.set(nextStep.key);
    }
  }

  protected async copyLogDetails(row: ElasticsearchLogRowView): Promise<void> {
    const copied = await copyTextToClipboard(row.detailsTooltip);
    if (!copied) {
      return;
    }

    this.copiedLogRowKey.set(row.key);

    if (this.logCopyFeedbackHandle !== null) {
      window.clearTimeout(this.logCopyFeedbackHandle);
    }

    this.logCopyFeedbackHandle = window.setTimeout(() => {
      if (this.copiedLogRowKey() === row.key) {
        this.copiedLogRowKey.set(null);
      }
      this.logCopyFeedbackHandle = null;
    }, 1600);
  }

  protected async copyPreparedPrompt(step: StepView): Promise<void> {
    if (!step.preparedPrompt) {
      return;
    }

    const copied = await copyTextToClipboard(step.preparedPrompt);
    if (!copied) {
      return;
    }

    this.copiedPromptStepKey.set(step.key);

    if (this.promptCopyFeedbackHandle !== null) {
      window.clearTimeout(this.promptCopyFeedbackHandle);
    }

    this.promptCopyFeedbackHandle = window.setTimeout(() => {
      if (this.copiedPromptStepKey() === step.key) {
        this.copiedPromptStepKey.set(null);
      }
      this.promptCopyFeedbackHandle = null;
    }, 1600);
  }
}

function resolveStepSections(
  step: AnalysisJobStepResponse,
  sections: AnalysisEvidenceSection[]
): AnalysisEvidenceSection[] {
  const links = resolveProducedEvidenceLinks(step);
  if (links.length === 0) {
    return [];
  }

  return sections.filter((section) =>
    links.some(
      (link) => section.provider === link.provider && section.category === link.category
    )
  );
}

function buildEvidenceFlowMeta(step: AnalysisJobStepResponse): string[] {
  const meta: string[] = [];
  const consumes = resolveConsumesEvidenceLinks(step);
  const produces = resolveProducedEvidenceLinks(step);

  if (consumes.length > 0) {
    meta.push(`Czyta: ${consumes.map(formatEvidenceLinkLabel).join(', ')}`);
  }

  if (produces.length > 0) {
    meta.push(`Publikuje: ${produces.map(formatEvidenceLinkLabel).join(', ')}`);
  }

  return meta;
}

function resolveProducedEvidenceLinks(step: AnalysisJobStepResponse): StepEvidenceLink[] {
  const producedEvidence = normalizeEvidenceLinks(step.producesEvidence);
  if (producedEvidence.length > 0) {
    return producedEvidence;
  }

  return [...(STEP_EVIDENCE_LINKS[step.code || ''] ?? inferStepEvidenceLinks(step.code))];
}

function resolveConsumesEvidenceLinks(step: AnalysisJobStepResponse): StepEvidenceLink[] {
  return normalizeEvidenceLinks(step.consumesEvidence);
}

function normalizeEvidenceLinks(
  references: AnalysisEvidenceReference[] | null | undefined
): StepEvidenceLink[] {
  if (!references || references.length === 0) {
    return [];
  }

  return references
    .filter((reference) => Boolean(reference?.provider && reference?.category))
    .map((reference) => ({
      provider: reference.provider,
      category: reference.category
    }));
}

function formatEvidenceLinkLabel(link: StepEvidenceLink): string {
  return formatEvidenceSectionTitle({
    provider: link.provider,
    category: link.category,
    items: []
  });
}

function inferStepEvidenceLinks(stepCode: string | null | undefined): StepEvidenceLink[] {
  const normalizedStepCode = String(stepCode || '').toUpperCase();

  if (normalizedStepCode.includes('ELASTIC')) {
    return [{ provider: 'elasticsearch', category: 'logs' }];
  }

  if (normalizedStepCode.includes('DEPLOYMENT')) {
    return [{ provider: 'deployment-context', category: 'resolved-deployment' }];
  }

  if (normalizedStepCode.includes('DYNATRACE')) {
    return [{ provider: 'dynatrace', category: 'runtime-signals' }];
  }

  if (normalizedStepCode.includes('GITLAB')) {
    return [{ provider: 'gitlab', category: 'resolved-code' }];
  }

  if (normalizedStepCode.includes('OPERATIONAL')) {
    return [{ provider: 'operational-context', category: 'matched-context' }];
  }

  return [];
}

function resolvePreparedPrompt(
  stepCode: string | null | undefined,
  preparedPrompt: string,
  resultPrompt: string
): string {
  if (!shouldShowPreparedPrompt(stepCode)) {
    return '';
  }

  const normalizedPreparedPrompt = firstDefinedText(preparedPrompt, resultPrompt);
  return normalizedPreparedPrompt || '';
}

function shouldShowPreparedPrompt(stepCode: string | null | undefined): boolean {
  const normalizedStepCode = String(stepCode || '').toUpperCase();
  return normalizedStepCode === 'OPERATIONAL_CONTEXT' || normalizedStepCode === 'AI_ANALYSIS';
}

function buildPreparedPromptTitle(stepCode: string | null | undefined): string {
  if (String(stepCode || '').toUpperCase() === 'OPERATIONAL_CONTEXT') {
    return 'Prompt po dopasowaniu kontekstu operacyjnego';
  }

  return 'Prompt przygotowany do wysłania do Copilota';
}

function buildPreparedPromptDescription(stepCode: string | null | undefined): string {
  if (String(stepCode || '').toUpperCase() === 'OPERATIONAL_CONTEXT') {
    return 'To jest finalny prompt złożony z evidence i lokalnego kontekstu operacyjnego jeszcze przed wywołaniem AI. Jeśli Copilot nie odpowie, możesz skopiować go stąd i uruchomić we własnym narzędziu.';
  }

  return 'To jest dokładny input, który zasila końcową analizę AI. Gdy sesja Copilota nie zadziała, ten prompt zostaje dostępny do ręcznego użycia poza aplikacją.';
}

function prepareEvidenceSections(sections: AnalysisEvidenceSection[]): StepEvidenceSectionView[] {
  return sections.map((section, sectionIndex) => {
    const sectionKey = buildEvidenceSectionKey(section, sectionIndex);

    if (isElasticsearchLogSection(section)) {
      const logRows = (section.items || []).map((item, itemIndex) =>
        prepareElasticsearchLogRow(section, item, itemIndex)
      );

      return {
        key: sectionKey,
        title: formatEvidenceSectionTitle(section),
        itemCount: logRows.length,
        variant: 'elasticsearch-log-table',
        items: [],
        logRows,
        codePanels: [],
        dynatraceRuntime: null
      };
    }

    if (isGitLabCodeSection(section)) {
      const codePanels = (section.items || [])
        .map((item, itemIndex) => prepareRepoCodePanel(section, item, itemIndex))
        .sort(compareRepoCodePanels);

      return {
        key: sectionKey,
        title: formatEvidenceSectionTitle(section),
        itemCount: codePanels.length,
        variant: 'gitlab-code-panels',
        items: [],
        logRows: [],
        codePanels,
        dynatraceRuntime: null
      };
    }

    if (isDynatraceRuntimeSection(section)) {
      const runtimeSection = prepareDynatraceRuntimeSection(section);
      return {
        key: sectionKey,
        title: formatEvidenceSectionTitle(section),
        itemCount: section.items.length,
        variant: 'dynatrace-runtime-panels',
        items: runtimeSection.uncategorizedItems,
        logRows: [],
        codePanels: [],
        dynatraceRuntime: runtimeSection.runtime
      };
    }

    const items = (section.items || []).map((item, itemIndex) =>
      prepareDefaultEvidenceItem(section, item, itemIndex)
    );

    return {
      key: sectionKey,
      title: formatEvidenceSectionTitle(section),
      itemCount: items.length,
      variant: 'default',
      items,
      logRows: [],
      codePanels: [],
      dynatraceRuntime: null
    };
  });
}

function isElasticsearchLogSection(section: AnalysisEvidenceSection): boolean {
  return section.provider === 'elasticsearch' && section.category === 'logs';
}

function isGitLabCodeSection(section: AnalysisEvidenceSection): boolean {
  return section.provider === 'gitlab' && section.category === 'resolved-code';
}

function isDynatraceRuntimeSection(section: AnalysisEvidenceSection): boolean {
  return section.provider === 'dynatrace' && section.category === 'runtime-signals';
}

function prepareDynatraceRuntimeSection(section: AnalysisEvidenceSection): {
  runtime: DynatraceRuntimeSectionView;
  uncategorizedItems: StepEvidenceItemView[];
} {
  const serviceMatches: DynatraceServiceMatchView[] = [];
  const problems: DynatraceProblemView[] = [];
  const metrics: DynatraceMetricView[] = [];
  const uncategorizedItems: StepEvidenceItemView[] = [];

  for (const [itemIndex, item] of (section.items || []).entries()) {
    const attributesByName = mapAttributesByName(item.attributes || []);

    if (looksLikeDynatraceServiceMatch(attributesByName)) {
      serviceMatches.push(prepareDynatraceServiceMatch(section, item, itemIndex, attributesByName));
      continue;
    }

    if (looksLikeDynatraceProblem(attributesByName)) {
      problems.push(prepareDynatraceProblem(section, item, itemIndex, attributesByName));
      continue;
    }

    if (looksLikeDynatraceMetric(attributesByName)) {
      metrics.push(prepareDynatraceMetric(section, item, itemIndex, attributesByName));
      continue;
    }

    uncategorizedItems.push(prepareDefaultEvidenceItem(section, item, itemIndex));
  }

  const serviceGroups = aggregateDynatraceServiceGroups(serviceMatches);
  const metricAggregates = aggregateDynatraceMetricGroups(metrics);

  return {
    runtime: {
      takeaways: buildDynatraceTakeaways(serviceGroups, problems, metricAggregates),
      serviceGroups,
      metricAggregates,
      serviceMatches,
      problems,
      metrics
    },
    uncategorizedItems
  };
}

function looksLikeDynatraceServiceMatch(attributesByName: ReadonlyMap<string, string>): boolean {
  return attributesByName.has('matchScore') || attributesByName.has('matchedContainers');
}

function looksLikeDynatraceProblem(attributesByName: ReadonlyMap<string, string>): boolean {
  return attributesByName.has('problemId') || attributesByName.has('severityLevel');
}

function looksLikeDynatraceMetric(attributesByName: ReadonlyMap<string, string>): boolean {
  return attributesByName.has('metricId') || attributesByName.has('metricLabel');
}

function prepareDefaultEvidenceItem(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number
): StepEvidenceItemView {
  const itemKey = buildEvidenceItemKey(section, item, itemIndex);
  const compactAttributes = (item.attributes || []).filter((attribute) => !isLargeAttribute(attribute));
  const blockAttributes = (item.attributes || []).filter(isLargeAttribute);

  return {
    key: itemKey,
    title: item.title || 'Untitled item',
    attributeCount: (item.attributes || []).length,
    compactAttributes,
    blockAttributes
  };
}

function prepareElasticsearchLogRow(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number
): ElasticsearchLogRowView {
  const itemKey = buildEvidenceItemKey(section, item, itemIndex);
  const attributes = item.attributes || [];
  const attributesByName = new Map<string, string>();

  for (const attribute of attributes) {
    attributesByName.set(attribute.name, attribute.value);
  }

  return {
    key: itemKey,
    title: item.title || 'Log entry',
    containerName: attributeValue(attributesByName, 'containerName'),
    timestamp: formatTimestamp(attributesByName.get('timestamp')),
    level: attributeValue(attributesByName, 'level'),
    className: attributeValue(attributesByName, 'className'),
    message: nonEmptyValue(attributesByName.get('message')),
    exception: nonEmptyValue(attributesByName.get('exception')),
    hasErrorStyling: hasErrorLogStyling(attributesByName),
    detailsTooltip: buildLogTooltipContent(item.title || 'Log entry', attributes)
  };
}

function prepareDynatraceServiceMatch(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number,
  attributesByName: ReadonlyMap<string, string>
): DynatraceServiceMatchView {
  const matchedServiceNames = splitAttributeValues(attributesByName.get('matchedServiceNames'));
  const displayName =
    firstDefinedText(attributesByName.get('displayName'), item.title) || 'Matched service';
  const logicalServiceTitle = resolveDynatraceLogicalServiceTitle(
    matchedServiceNames,
    displayName
  );

  return {
    key: buildEvidenceItemKey(section, item, itemIndex),
    title: summarizeDynatraceServiceDisplayName(displayName),
    summary: buildDynatraceServiceSummary(attributesByName),
    displayName,
    logicalServiceKey: buildDynatraceLogicalServiceKey(logicalServiceTitle),
    logicalServiceTitle,
    operationName: resolveDynatraceOperationName(displayName),
    entityId: attributeValue(attributesByName, 'entityId'),
    matchScore: attributeValue(attributesByName, 'matchScore'),
    incidentWindow: formatTimeRange(
      attributesByName.get('incidentStart'),
      attributesByName.get('incidentEnd')
    ),
    namespaces: splitAttributeValues(attributesByName.get('matchedNamespaces')),
    pods: splitAttributeValues(attributesByName.get('matchedPods')),
    containers: splitAttributeValues(attributesByName.get('matchedContainers')),
    serviceNames: matchedServiceNames
  };
}

function prepareDynatraceProblem(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number,
  attributesByName: ReadonlyMap<string, string>
): DynatraceProblemView {
  const displayId = nonEmptyValue(attributesByName.get('displayId'));
  const title = nonEmptyValue(attributesByName.get('title'));

  return {
    key: buildEvidenceItemKey(section, item, itemIndex),
    displayId,
    title:
      firstDefinedText(
        displayId && title ? `${displayId} · ${title}` : null,
        title,
        item.title
      ) || 'Problem',
    summary: buildDynatraceProblemSummary(attributesByName),
    severityLabel: formatDynatraceSeverityLabel(attributeValue(attributesByName, 'severityLevel')),
    statusLabel: formatDynatraceProblemStatus(attributeValue(attributesByName, 'status')),
    impactLabel: formatDynatraceImpact(attributeValue(attributesByName, 'impactLevel')),
    severityLevel: attributeValue(attributesByName, 'severityLevel'),
    status: attributeValue(attributesByName, 'status'),
    impactLevel: attributeValue(attributesByName, 'impactLevel'),
    timeWindow: formatTimeRange(attributesByName.get('startTime'), attributesByName.get('endTime')),
    rootCause:
      firstDefinedText(
        attributesByName.get('rootCauseEntityName'),
        attributesByName.get('rootCauseEntityId')
      ) || 'n/a',
    signalCategories: splitAttributeValues(attributesByName.get('signalCategories')),
    correlationHighlights: splitEvidenceSummary(attributesByName.get('correlationHighlights')),
    affectedEntities: splitAttributeValues(attributesByName.get('affectedEntities')),
    impactedEntities: splitAttributeValues(attributesByName.get('impactedEntities')),
    evidenceLines: splitEvidenceSummary(attributesByName.get('evidenceSummary'))
  };
}

function prepareDynatraceMetric(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number,
  attributesByName: ReadonlyMap<string, string>
): DynatraceMetricView {
  const entityDisplayName = attributeValue(attributesByName, 'entityDisplayName');
  const logicalServiceTitle = resolveDynatraceLogicalServiceTitle([], entityDisplayName);

  return {
    key: buildEvidenceItemKey(section, item, itemIndex),
    title: friendlyDynatraceMetricLabel(
      attributeValue(attributesByName, 'metricLabel'),
      attributeValue(attributesByName, 'metricId')
    ),
    summary: buildDynatraceMetricSummary(attributesByName),
    logicalServiceKey: buildDynatraceLogicalServiceKey(logicalServiceTitle),
    logicalServiceTitle,
    operationName: resolveDynatraceOperationName(entityDisplayName),
    metricLabel: firstDefinedText(attributesByName.get('metricLabel'), item.title) || 'Metric',
    entityDisplayName,
    entityId: attributeValue(attributesByName, 'entityId'),
    metricId: attributeValue(attributesByName, 'metricId'),
    unit: attributeValue(attributesByName, 'unit'),
    resolution: attributeValue(attributesByName, 'resolution'),
    queryWindow: formatTimeRange(attributesByName.get('queryFrom'), attributesByName.get('queryTo')),
    nonNullPoints: attributeValue(attributesByName, 'nonNullPoints'),
    minValue: formatMetricStat(attributesByName, 'minValue'),
    maxValue: formatMetricStat(attributesByName, 'maxValue'),
    averageValue: formatMetricStat(attributesByName, 'averageValue'),
    lastValue: formatMetricStat(attributesByName, 'lastValue')
  };
}

function prepareRepoCodePanel(
  section: AnalysisEvidenceSection,
  item: { title: string; attributes: AnalysisEvidenceAttribute[] },
  itemIndex: number
): RepoCodePanelView {
  const itemKey = buildEvidenceItemKey(section, item, itemIndex);
  const attributes = [...(item.attributes || [])].sort(compareGitLabAttributes);
  const attributesByName = new Map<string, string>();

  for (const attribute of attributes) {
    attributesByName.set(attribute.name, attribute.value);
  }

  const content = attributesByName.get('content') || '';
  const highlightedLineNumber = integerValue(attributesByName, 'lineNumber');
  const contentStartLine =
    integerValue(attributesByName, 'returnedStartLine') ??
    integerValue(attributesByName, 'requestedStartLine') ??
    (content ? 1 : null);

  const headerTitle = buildRepoPanelTitle(attributesByName, item.title);
  const componentName = buildRepoPanelComponentName(attributesByName);
  const metaAttributes = attributes.filter((attribute) => attribute.name !== 'content');

  return {
    key: itemKey,
    headerTitle,
    componentName,
    detailsTooltip: buildRepoTooltipContent(item.title || headerTitle, attributes),
    hasContent: Boolean(content),
    highlightedLineNumber,
    codeLines: buildRepoCodeLines(content, contentStartLine, highlightedLineNumber),
    compactAttributes: metaAttributes.filter((attribute) => !isLargeAttribute(attribute)),
    blockAttributes: metaAttributes.filter(isLargeAttribute)
  };
}

function buildStepKey(step: AnalysisJobStepResponse, index: number): string {
  return step.code || `${step.label || 'step'}-${index}`;
}

function selectDefaultStepKey(steps: StepView[]): string {
  const currentStep = steps.find((step) => step.status === 'IN_PROGRESS' || step.status === 'FAILED');
  if (currentStep) {
    return currentStep.key;
  }

  const lastResolvedStep = [...steps]
    .reverse()
    .find((step) => step.status !== 'PENDING');

  return lastResolvedStep?.key || steps[0].key;
}

function buildHeaderMeta(step: AnalysisJobStepResponse): string {
  const meta = [formatStatus(step.status)];

  if (typeof step.itemCount === 'number') {
    meta.push(`${step.itemCount} poz.`);
  }

  return meta.join(' · ');
}

function buildFallbackStepMessage(status: string): string {
  if (status === 'COMPLETED') {
    return 'Krok zakończył się powodzeniem.';
  }

  if (status === 'FAILED') {
    return 'Krok zakończył się błędem.';
  }

  if (status === 'IN_PROGRESS') {
    return 'Krok jest aktualnie wykonywany.';
  }

  if (status === 'SKIPPED') {
    return 'Krok został pominięty.';
  }

  return 'Krok oczekuje na uruchomienie.';
}

function buildStepIndicatorState(status: string): StepIndicatorState {
  if (status === 'COMPLETED') {
    return 'done';
  }

  if (status === 'FAILED') {
    return 'error';
  }

  if (status === 'IN_PROGRESS') {
    return 'running';
  }

  if (status === 'SKIPPED') {
    return 'skipped';
  }

  return 'number';
}

function buildEmptyStateMessage(
  step: AnalysisJobStepResponse,
  detailSectionCount: number,
  showResultPreview: boolean,
  showPreparedPromptView: boolean
): string {
  if (detailSectionCount > 0 || showResultPreview || showPreparedPromptView) {
    return '';
  }

  if (step.status === 'COMPLETED') {
    if (step.code === 'DYNATRACE_RUNTIME_SIGNALS') {
      return 'Dynatrace nie zwrócił danych runtime do dołączenia do promptu Copilota dla tego incydentu.';
    }

    return 'Ten krok zakończył się bez dodatkowych danych do wyświetlenia.';
  }

  if (step.status === 'IN_PROGRESS') {
    return 'Szczegóły tego kroku pojawią się po zakończeniu pobierania danych.';
  }

  if (step.status === 'FAILED') {
    return 'Krok zakończył się błędem, więc nie udało się zebrać szczegółów.';
  }

  if (step.status === 'SKIPPED') {
    return 'Krok został pominięty i nie wygenerował dodatkowych danych.';
  }

  return 'Ten krok jeszcze nie został uruchomiony.';
}

function stepStatusClass(status: string): string {
  if (status === 'COMPLETED') {
    return 'status-pill--done';
  }

  if (status === 'FAILED') {
    return 'status-pill--error';
  }

  if (status === 'IN_PROGRESS') {
    return 'status-pill--running';
  }

  return 'status-pill--queued';
}

function compareLogAttributes(
  left: AnalysisEvidenceAttribute,
  right: AnalysisEvidenceAttribute
): number {
  const leftOrder = LOG_TABLE_ATTRIBUTE_ORDER[left.name] ?? 999;
  const rightOrder = LOG_TABLE_ATTRIBUTE_ORDER[right.name] ?? 999;

  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  return left.name.localeCompare(right.name);
}

function compareGitLabAttributes(
  left: AnalysisEvidenceAttribute,
  right: AnalysisEvidenceAttribute
): number {
  const leftOrder = GITLAB_ATTRIBUTE_ORDER[left.name] ?? 500;
  const rightOrder = GITLAB_ATTRIBUTE_ORDER[right.name] ?? 500;

  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  return left.name.localeCompare(right.name);
}

function attributeValue(
  attributesByName: ReadonlyMap<string, string>,
  name: string,
  fallback = 'n/a'
): string {
  const value = attributesByName.get(name);
  return value && value.trim() ? value : fallback;
}

function mapAttributesByName(
  attributes: AnalysisEvidenceAttribute[]
): ReadonlyMap<string, string> {
  const attributesByName = new Map<string, string>();

  for (const attribute of attributes) {
    attributesByName.set(attribute.name, attribute.value);
  }

  return attributesByName;
}

function formatTimestamp(value: string | undefined): string {
  if (!value || !value.trim()) {
    return 'n/a';
  }

  return formatDateTime(value);
}

function nonEmptyValue(value: string | undefined): string {
  return value && value.trim() ? value : '';
}

function formatTimeRange(
  startTime: string | undefined,
  endTime: string | undefined
): string {
  const formattedStart = formatTimestamp(startTime);
  const formattedEnd = formatTimestamp(endTime);

  if (formattedStart === 'n/a' && formattedEnd === 'n/a') {
    return 'n/a';
  }

  if (formattedStart === 'n/a') {
    return `do ${formattedEnd}`;
  }

  if (formattedEnd === 'n/a') {
    return `od ${formattedStart}`;
  }

  return `${formattedStart} -> ${formattedEnd}`;
}

function splitAttributeValues(value: string | undefined): string[] {
  return String(value || '')
    .split(',')
    .map((token) => token.trim())
    .filter(Boolean);
}

function splitEvidenceSummary(value: string | undefined): string[] {
  return String(value || '')
    .split('||')
    .map((line) => line.trim())
    .filter(Boolean);
}

function joinAsNaturalList(values: string[]): string {
  if (values.length === 0) {
    return '';
  }

  if (values.length === 1) {
    return values[0] ?? '';
  }

  if (values.length === 2) {
    return `${values[0]} i ${values[1]}`;
  }

  return `${values.slice(0, -1).join(', ')} i ${values[values.length - 1]}`;
}

function formatMetricStat(
  attributesByName: ReadonlyMap<string, string>,
  metricKey: string
): string {
  const value = attributeValue(attributesByName, metricKey);
  if (value === 'n/a') {
    return value;
  }

  const unit = nonEmptyValue(attributesByName.get('unit'));
  return unit ? `${value} ${unit}` : value;
}

function aggregateDynatraceServiceGroups(
  serviceMatches: DynatraceServiceMatchView[]
): DynatraceServiceGroupView[] {
  const groups = new Map<
    string,
    {
      title: string;
      matches: DynatraceServiceMatchView[];
      namespaces: Set<string>;
      pods: Set<string>;
      containers: Set<string>;
      serviceNames: Set<string>;
      operations: Map<string, number>;
    }
  >();

  for (const match of serviceMatches) {
    const existingGroup = groups.get(match.logicalServiceKey) ?? {
      title: match.logicalServiceTitle,
      matches: [],
      namespaces: new Set<string>(),
      pods: new Set<string>(),
      containers: new Set<string>(),
      serviceNames: new Set<string>(),
      operations: new Map<string, number>()
    };

    existingGroup.matches.push(match);
    match.namespaces.forEach((value) => existingGroup.namespaces.add(value));
    match.pods.forEach((value) => existingGroup.pods.add(value));
    match.containers.forEach((value) => existingGroup.containers.add(value));
    match.serviceNames.forEach((value) => existingGroup.serviceNames.add(value));

    if (match.operationName && !isGenericDynatraceOperation(match.operationName)) {
      existingGroup.operations.set(
        match.operationName,
        (existingGroup.operations.get(match.operationName) ?? 0) + 1
      );
    }

    groups.set(match.logicalServiceKey, existingGroup);
  }

  return [...groups.entries()]
    .map(([key, group]) => {
      const topScore = group.matches.reduce((maxValue, match) => {
        const numericScore = Number.parseInt(match.matchScore, 10);
        return Number.isFinite(numericScore) && numericScore > maxValue ? numericScore : maxValue;
      }, 0);
      const topOperations = [...group.operations.entries()]
        .sort((left, right) => {
          if (right[1] !== left[1]) {
            return right[1] - left[1];
          }

          return left[0].localeCompare(right[0]);
        })
        .map(([operation]) => operation)
        .slice(0, 8);

      return {
        key,
        title: group.title,
        summary: buildDynatraceServiceGroupSummary(group.title, group.matches.length, topScore),
        technicalMatchCount: group.matches.length,
        topScore: topScore > 0 ? String(topScore) : 'n/a',
        incidentWindow: formatIncidentWindows(group.matches.map((match) => match.incidentWindow)),
        namespaces: [...group.namespaces],
        pods: [...group.pods],
        containers: [...group.containers],
        serviceNames: [...group.serviceNames],
        operationCount: group.operations.size,
        topOperations
      };
    })
    .sort((left, right) => {
      if (right.technicalMatchCount !== left.technicalMatchCount) {
        return right.technicalMatchCount - left.technicalMatchCount;
      }

      return left.title.localeCompare(right.title);
    });
}

function aggregateDynatraceMetricGroups(
  metrics: DynatraceMetricView[]
): DynatraceMetricAggregateView[] {
  const groups = new Map<
    string,
    {
      title: string;
      serviceTitle: string;
      resolution: string;
      unit: string;
      metrics: DynatraceMetricView[];
      operations: Set<string>;
    }
  >();

  for (const metric of metrics) {
    const key = `${metric.logicalServiceKey}|${metric.title}`;
    const existingGroup = groups.get(key) ?? {
      title: metric.title,
      serviceTitle: metric.logicalServiceTitle,
      resolution: metric.resolution,
      unit: metric.unit,
      metrics: [],
      operations: new Set<string>()
    };

    existingGroup.metrics.push(metric);
    if (metric.operationName && !isGenericDynatraceOperation(metric.operationName)) {
      existingGroup.operations.add(metric.operationName);
    }

    groups.set(key, existingGroup);
  }

  return [...groups.entries()]
    .map(([key, group]) => buildDynatraceMetricAggregate(key, group))
    .sort(compareDynatraceMetricAggregates);
}

function buildDynatraceMetricAggregate(
  key: string,
  group: {
    title: string;
    serviceTitle: string;
    resolution: string;
    unit: string;
    metrics: DynatraceMetricView[];
    operations: Set<string>;
  }
): DynatraceMetricAggregateView {
  const comparator = isLowerValueWorseMetric(group.title)
    ? (left: number, right: number) => left < right
    : (left: number, right: number) => left > right;
  const worstCandidate = group.metrics.reduce<DynatraceMetricView | null>((selected, metric) => {
    if (!selected) {
      return metric;
    }

    const selectedValue = preferredMetricSeverityValue(selected, group.title);
    const currentValue = preferredMetricSeverityValue(metric, group.title);

    if (selectedValue === null) {
      return currentValue === null ? selected : metric;
    }

    if (currentValue === null) {
      return selected;
    }

    return comparator(currentValue, selectedValue) ? metric : selected;
  }, null);
  const technicalMetricCount = group.metrics.length;
  const averageOfAverage = aggregateMetricNumber(group.metrics, 'averageValue', group.title);
  const latestValue = aggregateMetricNumber(group.metrics, 'lastValue', group.title);
  const worstValue = worstCandidate
    ? preferredMetricSeverityValue(worstCandidate, group.title)
    : null;
  const exampleOperations = [...group.operations].slice(0, 6);

  return {
    key,
    title: group.title,
    summary: buildDynatraceMetricAggregateSummary(
      group.title,
      group.serviceTitle,
      technicalMetricCount,
      worstValue,
      averageOfAverage,
      latestValue,
      group.unit,
      worstCandidate?.operationName || ''
    ),
    serviceTitle: group.serviceTitle,
    technicalMetricCount,
    resolution: group.resolution,
    unit: group.unit,
    worstValueLabel: dynatraceMetricWorstValueLabel(group.title),
    worstValue: renderDynatraceMetricValue(worstValue, group.unit, group.title),
    averageValueLabel: dynatraceMetricAverageValueLabel(group.title),
    averageValue: renderDynatraceMetricValue(averageOfAverage, group.unit, group.title),
    latestValueLabel: dynatraceMetricLatestValueLabel(group.title),
    latestValue: renderDynatraceMetricValue(latestValue, group.unit, group.title),
    worstContext: worstCandidate?.operationName || 'n/a',
    exampleOperations
  };
}

function buildDynatraceServiceGroupSummary(
  title: string,
  technicalMatchCount: number,
  topScore: number
): string {
  if (technicalMatchCount <= 1) {
    return `Dynatrace wskazał usługę ${title} jako głównego kandydata dla tego incydentu.`;
  }

  return `Dynatrace zgrupował ${technicalMatchCount} technicznych dopasowań do jednej usługi logicznej: ${title}. Najwyższy score dopasowania wyniósł ${topScore}.`;
}

function buildDynatraceMetricAggregateSummary(
  title: string,
  serviceTitle: string,
  technicalMetricCount: number,
  worstValue: number | null,
  averageValue: number | null,
  latestValue: number | null,
  unit: string,
  worstContext: string
): string {
  const scope = technicalMetricCount === 1
    ? 'na podstawie 1 technicznej serii'
    : `na podstawie ${technicalMetricCount} technicznych serii`;

  if (title === 'Czas odpowiedzi' && worstValue !== null) {
    const typicalText = averageValue !== null
      ? ` Typowy poziom tych serii był bliżej ${renderDynatraceMetricValue(averageValue, unit, title)}.`
      : '';
    const operationText = worstContext ? ` Najmocniej odstawał ${worstContext}.` : '';
    return `${title} dla ${serviceTitle} został zagregowany ${scope}. To p95 z wielu endpointów, nie jeden czas całej usługi. Skrajny pik w jednej serii sięgnął ${renderDynatraceMetricValue(worstValue, unit, title)}.${typicalText}${operationText}`;
  }

  if (title === 'Skuteczność odpowiedzi') {
    const referenceValue = worstValue ?? latestValue ?? averageValue;
    if (referenceValue !== null) {
      return `${title} dla ${serviceTitle} został zagregowany ${scope}. Najniższa wartość wyniosła ${renderNumberWithUnit(referenceValue, unit)}.`;
    }
  }

  if ((title === 'Liczba błędów' || title === 'Błędy 5xx' || title === 'Błędy 4xx') && worstValue !== null) {
    const operationText = worstContext ? ` Najbardziej wyróżniał się ${worstContext}.` : '';
    return `${title} dla ${serviceTitle} został zagregowany ${scope}. Najgorszy pik to ${renderDynatraceMetricValue(worstValue, unit, title)}.${operationText}`;
  }

  if (worstValue !== null) {
    return `${title} dla ${serviceTitle} został zagregowany ${scope}. Najważniejsza wartość to ${renderDynatraceMetricValue(worstValue, unit, title)}.`;
  }

  if (latestValue !== null) {
    return `${title} dla ${serviceTitle} został zagregowany ${scope}. Ostatnia wartość wynosiła ${renderDynatraceMetricValue(latestValue, unit, title)}.`;
  }

  return `${title} dla ${serviceTitle} został zagregowany ${scope}.`;
}

function dynatraceMetricWorstValueLabel(title: string): string {
  if (title === 'Czas odpowiedzi') {
    return 'Skrajny pik p95';
  }

  return isLowerValueWorseMetric(title) ? 'Najniższa wartość' : 'Najgorszy pik';
}

function dynatraceMetricAverageValueLabel(title: string): string {
  return title === 'Czas odpowiedzi' ? 'Typowy poziom' : 'Średnia z agregacji';
}

function dynatraceMetricLatestValueLabel(title: string): string {
  return title === 'Czas odpowiedzi' ? 'Ostatni poziom' : 'Ostatnia wartość';
}

function compareDynatraceMetricAggregates(
  left: DynatraceMetricAggregateView,
  right: DynatraceMetricAggregateView
): number {
  const leftRank = metricAggregatePriority(left.title);
  const rightRank = metricAggregatePriority(right.title);

  if (leftRank !== rightRank) {
    return leftRank - rightRank;
  }

  if (right.technicalMetricCount !== left.technicalMetricCount) {
    return right.technicalMetricCount - left.technicalMetricCount;
  }

  return left.title.localeCompare(right.title);
}

function metricAggregatePriority(title: string): number {
  if (title === 'Czas odpowiedzi') {
    return 10;
  }

  if (title === 'Błędy 5xx') {
    return 20;
  }

  if (title === 'Liczba błędów') {
    return 30;
  }

  if (title === 'Błędy 4xx') {
    return 40;
  }

  if (title === 'Skuteczność odpowiedzi') {
    return 50;
  }

  return 100;
}

function preferredMetricSeverityValue(metric: DynatraceMetricView, title: string): number | null {
  if (isLowerValueWorseMetric(title)) {
    return parseMetricNumber(metric.minValue) ?? parseMetricNumber(metric.lastValue);
  }

  return parseMetricNumber(metric.maxValue) ?? parseMetricNumber(metric.lastValue);
}

function aggregateMetricNumber(
  metrics: DynatraceMetricView[],
  field: keyof Pick<DynatraceMetricView, 'averageValue' | 'lastValue'>,
  title: string
): number | null {
  const numbers = metrics
    .map((metric) => {
      if (field === 'lastValue' && isLowerValueWorseMetric(title)) {
        return parseMetricNumber(metric.minValue) ?? parseMetricNumber(metric.lastValue);
      }

      return parseMetricNumber(metric[field]);
    })
    .filter((value): value is number => value !== null);

  if (numbers.length === 0) {
    return null;
  }

  return numbers.reduce((sum, value) => sum + value, 0) / numbers.length;
}

function parseMetricNumber(value: string): number | null {
  if (!value || value === 'n/a') {
    return null;
  }

  const parsed = Number.parseFloat(value.replace(/\s+/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

function renderNumberWithUnit(value: number | null, unit: string): string {
  if (value === null) {
    return 'n/a';
  }

  const rendered = formatMetricNumber(value);

  return unit && unit !== 'n/a' ? `${rendered} ${unit}` : rendered;
}

function renderDynatraceMetricValue(
  value: number | null,
  unit: string,
  title: string
): string {
  if (value === null) {
    return 'n/a';
  }

  if (isDurationMetric(title, unit)) {
    return renderDurationFromMilliseconds(value);
  }

  return renderNumberWithUnit(value, unit);
}

function isLowerValueWorseMetric(title: string): boolean {
  return title === 'Skuteczność odpowiedzi';
}

function isDurationMetric(title: string, unit: string): boolean {
  return title === 'Czas odpowiedzi' && unit.trim().toLowerCase() === 'ms';
}

function formatIncidentWindows(windows: string[]): string {
  const distinct = [...new Set(windows.filter((windowValue) => windowValue && windowValue !== 'n/a'))];
  return distinct[0] || 'n/a';
}

function buildDynatraceTakeaways(
  serviceGroups: DynatraceServiceGroupView[],
  problems: DynatraceProblemView[],
  metricAggregates: DynatraceMetricAggregateView[]
): string[] {
  const takeaways: string[] = [];

  if (serviceGroups.length > 0) {
    takeaways.push(serviceGroups[0].summary);
  } else {
    takeaways.push('Dynatrace nie wskazał jednoznacznie usługi powiązanej z tym incydentem.');
  }

  if (problems.length > 0) {
    takeaways.push(
      problems.length === 1
        ? `W czasie incydentu Dynatrace pokazał 1 problem: ${problems[0].title}.`
        : `W czasie incydentu Dynatrace pokazał ${problems.length} problemy. Najważniejszy: ${problems[0].title}.`
    );
  } else {
    takeaways.push('W oknie incydentu Dynatrace nie zgłosił osobnego problemu.');
  }

  const metricTakeaways = metricAggregates
    .map((metric) => metric.summary)
    .filter(Boolean)
    .slice(0, 2);

  if (metricTakeaways.length > 0) {
    takeaways.push(...metricTakeaways);
  }

  return takeaways;
}

function summarizeDynatraceServiceDisplayName(displayName: string): string {
  const simplified = displayName
    .split('||')
    .map((token) => token.trim())
    .filter(Boolean);

  if (simplified.length === 0) {
    return displayName;
  }

  return simplified[0];
}

function resolveDynatraceLogicalServiceTitle(
  matchedServiceNames: string[],
  displayName: string
): string {
  const firstMatchedService = matchedServiceNames[0];
  if (firstMatchedService) {
    return firstMatchedService;
  }

  const tokens = splitDynatraceDisplayName(displayName);
  const springBootName = extractSpringBootServiceName(tokens.secondary);
  if (springBootName) {
    return springBootName;
  }

  if (tokens.tertiary && !isGenericDynatraceOperation(tokens.tertiary)) {
    return tokens.tertiary;
  }

  return summarizeDynatraceServiceDisplayName(displayName);
}

function buildDynatraceLogicalServiceKey(logicalServiceTitle: string): string {
  return logicalServiceTitle.trim().toLowerCase();
}

function resolveDynatraceOperationName(displayName: string): string {
  const tokens = splitDynatraceDisplayName(displayName);
  if (tokens.tertiary) {
    return tokens.tertiary;
  }

  return '';
}

function splitDynatraceDisplayName(displayName: string): {
  primary: string;
  secondary: string;
  tertiary: string;
} {
  const tokens = displayName
    .split('||')
    .map((token) => token.trim())
    .filter(Boolean);

  return {
    primary: tokens[0] || '',
    secondary: tokens[1] || '',
    tertiary: tokens[2] || ''
  };
}

function extractSpringBootServiceName(value: string): string {
  const normalizedValue = value.trim();
  const springBootMatch = normalizedValue.match(/^springboot\s+([^\s]+)/i);
  if (springBootMatch?.[1]) {
    return springBootMatch[1];
  }

  return '';
}

function isGenericDynatraceOperation(value: string): boolean {
  const normalizedValue = value.trim().toLowerCase();
  return (
    !normalizedValue ||
    normalizedValue === 'n/a' ||
    normalizedValue.includes('requests executed in background threads')
  );
}

function buildDynatraceServiceSummary(
  attributesByName: ReadonlyMap<string, string>
): string {
  const serviceName = summarizeDynatraceServiceDisplayName(
    firstDefinedText(attributesByName.get('displayName'), 'ta usługa') || 'ta usługa'
  );
  const score = attributeValue(attributesByName, 'matchScore');

  if (score !== 'n/a') {
    return `Dynatrace powiązał incydent głównie z usługą ${serviceName}. To było najsilniejsze dopasowanie w zebranych danych runtime.`;
  }

  return `Dynatrace powiązał incydent z usługą ${serviceName}.`;
}

function buildDynatraceProblemSummary(
  attributesByName: ReadonlyMap<string, string>
): string {
  const rootCause =
    firstDefinedText(
      attributesByName.get('rootCauseEntityName'),
      attributesByName.get('rootCauseEntityId')
    ) || 'nieustalonym elemencie';
  const severity = formatDynatraceSeverityNarrative(attributeValue(attributesByName, 'severityLevel'));
  const status = formatDynatraceProblemStatus(attributeValue(attributesByName, 'status')).toLowerCase();
  const signalLabels = splitAttributeValues(attributesByName.get('signalCategories')).map(
    formatDynatraceSignalCategoryLabel
  );
  const signalSummary =
    signalLabels.length > 0
      ? ` Copilot dostał tu sygnały do korelacji: ${joinAsNaturalList(signalLabels)}.`
      : '';

  return `Dynatrace widział ${status} problem o ${severity.toLowerCase()} ważności. Najmocniej wskazany obszar to ${rootCause}.${signalSummary}`;
}

function buildDynatraceMetricSummary(
  attributesByName: ReadonlyMap<string, string>
): string {
  const friendlyLabel = friendlyDynatraceMetricLabel(
    attributeValue(attributesByName, 'metricLabel'),
    attributeValue(attributesByName, 'metricId')
  );
  const unit = nonEmptyValue(attributesByName.get('unit'));
  const maxValue = nonEmptyValue(attributesByName.get('maxValue'));
  const averageValue = nonEmptyValue(attributesByName.get('averageValue'));
  const lastValue = nonEmptyValue(attributesByName.get('lastValue'));

  if (friendlyLabel === 'Czas odpowiedzi') {
    if (maxValue && averageValue) {
      return `Czas odpowiedzi wzrósł maksymalnie do ${renderMetricAttributeValue(maxValue, unit, friendlyLabel)}, a średnio wynosił ${renderMetricAttributeValue(averageValue, unit, friendlyLabel)}.`;
    }
  }

  if (friendlyLabel === 'Błędy 5xx') {
    if (maxValue) {
      return `Błędy serwera 5xx dochodziły do ${renderValueWithUnit(maxValue, unit)} w analizowanym oknie.`;
    }
  }

  if (friendlyLabel === 'Liczba błędów') {
    if (maxValue) {
      return `Łączna liczba błędów rosła do ${renderValueWithUnit(maxValue, unit)}.`;
    }
  }

  if (friendlyLabel === 'Skuteczność odpowiedzi') {
    if (lastValue) {
      return `Skuteczność odpowiedzi na końcu okna wynosiła ${renderValueWithUnit(lastValue, unit)}.`;
    }
  }

  if (lastValue) {
    return `${friendlyLabel} na końcu okna miało wartość ${renderMetricAttributeValue(lastValue, unit, friendlyLabel)}.`;
  }

  if (maxValue) {
    return `${friendlyLabel} osiągnęło maksymalnie ${renderMetricAttributeValue(maxValue, unit, friendlyLabel)}.`;
  }

  return `${friendlyLabel} zostało dołączone do danych runtime dla AI.`;
}

function friendlyDynatraceMetricLabel(metricLabel: string, metricId: string): string {
  const normalized = `${metricLabel} ${metricId}`.toLowerCase();

  if (normalized.includes('response.time')) {
    return 'Czas odpowiedzi';
  }

  if (normalized.includes('errors.total')) {
    return 'Liczba błędów';
  }

  if (normalized.includes('errors.fivexx')) {
    return 'Błędy 5xx';
  }

  if (normalized.includes('errors.fourxx')) {
    return 'Błędy 4xx';
  }

  if (normalized.includes('successes.server.rate')) {
    return 'Skuteczność odpowiedzi';
  }

  return metricLabel && metricLabel !== 'n/a' ? metricLabel : metricId;
}

function formatDynatraceSeverityLabel(value: string): string {
  const normalized = value.trim().toUpperCase();

  if (normalized === 'ERROR' || normalized === 'AVAILABILITY') {
    return 'Wysoka ważność';
  }

  if (normalized === 'PERFORMANCE') {
    return 'Problem wydajności';
  }

  if (normalized === 'RESOURCE_CONTENTION') {
    return 'Problem zasobów';
  }

  return value === 'n/a' ? 'Nieznana ważność' : value;
}

function formatDynatraceSeverityNarrative(value: string): string {
  const normalized = value.trim().toUpperCase();

  if (normalized === 'ERROR' || normalized === 'AVAILABILITY') {
    return 'wysokiej';
  }

  if (normalized === 'PERFORMANCE') {
    return 'podwyższonej';
  }

  if (normalized === 'RESOURCE_CONTENTION') {
    return 'istotnej';
  }

  return value === 'n/a' ? 'nieznanej' : value.toLowerCase();
}

function formatDynatraceProblemStatus(value: string): string {
  const normalized = value.trim().toUpperCase();

  if (normalized === 'OPEN' || normalized === 'ACTIVE') {
    return 'aktywny';
  }

  if (normalized === 'RESOLVED' || normalized === 'CLOSED') {
    return 'zamknięty';
  }

  return value === 'n/a' ? 'nieznany' : value.toLowerCase();
}

function formatDynatraceSignalCategoryLabel(value: string): string {
  const normalized = value.trim().toLowerCase();

  if (normalized === 'database-connectivity') {
    return 'łączność z bazą danych';
  }

  if (normalized === 'availability') {
    return 'dostępność procesu lub usługi';
  }

  if (normalized === 'messaging') {
    return 'kolejki lub messaging';
  }

  if (normalized === 'latency') {
    return 'opóźnienia odpowiedzi';
  }

  if (normalized === 'failure-rate') {
    return 'wzrost błędów';
  }

  return value === 'n/a' ? 'nieznany sygnał' : value;
}

function formatDynatraceImpact(value: string): string {
  const normalized = value.trim().toUpperCase();

  if (normalized === 'SERVICE') {
    return 'wpływ na usługę';
  }

  if (normalized === 'APPLICATION') {
    return 'wpływ na aplikację';
  }

  if (normalized === 'INFRASTRUCTURE') {
    return 'wpływ na infrastrukturę';
  }

  return value === 'n/a' ? 'nieznany wpływ' : value.toLowerCase();
}

function renderValueWithUnit(value: string, unit: string): string {
  return unit ? `${value} ${unit}` : value;
}

function renderMetricAttributeValue(value: string, unit: string, title: string): string {
  const numericValue = parseMetricNumber(value);
  if (numericValue === null) {
    return renderValueWithUnit(value, unit);
  }

  return renderDynatraceMetricValue(numericValue, unit, title);
}

function renderDurationFromMilliseconds(value: number): string {
  const absoluteValue = Math.abs(value);

  if (absoluteValue >= 60_000) {
    return `${formatMetricNumber(value / 60_000)} min`;
  }

  if (absoluteValue >= 1_000) {
    return `${formatMetricNumber(value / 1_000)} s`;
  }

  return `${formatMetricNumber(value)} ms`;
}

function formatMetricNumber(value: number): string {
  const absoluteValue = Math.abs(value);

  return new Intl.NumberFormat('pl-PL', {
    maximumFractionDigits: absoluteValue >= 100 ? 0 : 2
  }).format(value);
}

function hasErrorLogStyling(attributesByName: ReadonlyMap<string, string>): boolean {
  const level = String(attributesByName.get('level') || '').trim().toUpperCase();
  const exception = nonEmptyValue(attributesByName.get('exception'));
  return level === 'ERROR' || Boolean(exception);
}

function buildLogTooltipContent(title: string, attributes: AnalysisEvidenceAttribute[]): string {
  const lines = [title];

  for (const attribute of [...attributes].sort(compareLogAttributes)) {
    const value = attribute.value && attribute.value.trim() ? attribute.value : 'n/a';
    lines.push('');
    lines.push(`${attribute.name}:`);
    lines.push(value);
  }

  return lines.join('\n');
}

function buildRepoTooltipContent(title: string, attributes: AnalysisEvidenceAttribute[]): string {
  const lines = [title];

  for (const attribute of [...attributes].sort(compareGitLabAttributes)) {
    const value = attribute.value && attribute.value.trim() ? attribute.value : 'n/a';
    lines.push('');
    lines.push(`${attribute.name}:`);
    lines.push(value);
  }

  return lines.join('\n');
}

function buildRepoPanelTitle(
  attributesByName: ReadonlyMap<string, string>,
  fallbackTitle: string
): string {
  const symbol = attributesByName.get('symbol');
  if (symbol && symbol.trim()) {
    return simpleSymbolName(symbol);
  }

  const filePath = attributesByName.get('filePath');
  if (filePath && filePath.trim()) {
    return stripExtension(lastPathSegment(filePath));
  }

  if (fallbackTitle.startsWith('GitLab deployment context')) {
    return 'Deployment context';
  }

  return fallbackTitle || 'Repo data';
}

function buildRepoPanelComponentName(attributesByName: ReadonlyMap<string, string>): string {
  return (
    firstDefinedText(
      attributesByName.get('projectName'),
      attributesByName.get('containerName'),
      attributesByName.get('environment')
    ) || 'Nieznany komponent'
  );
}

function buildRepoCodeLines(
  content: string,
  startLine: number | null,
  highlightedLineNumber: number | null
): RepoCodeLineView[] {
  if (!content) {
    return [];
  }

  const firstLineNumber = startLine ?? 1;
  return content.split('\n').map((line, index) => {
    const lineNumber = firstLineNumber + index;
    return {
      lineNumber,
      content: line,
      highlighted: highlightedLineNumber === lineNumber
    };
  });
}

function compareRepoCodePanels(left: RepoCodePanelView, right: RepoCodePanelView): number {
  if (left.hasContent !== right.hasContent) {
    return left.hasContent ? -1 : 1;
  }

  if (left.highlightedLineNumber !== null && right.highlightedLineNumber === null) {
    return -1;
  }

  if (left.highlightedLineNumber === null && right.highlightedLineNumber !== null) {
    return 1;
  }

  return left.headerTitle.localeCompare(right.headerTitle);
}

function integerValue(
  attributesByName: ReadonlyMap<string, string>,
  name: string
): number | null {
  const rawValue = attributesByName.get(name);
  if (!rawValue || !rawValue.trim()) {
    return null;
  }

  const parsed = Number.parseInt(rawValue, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function simpleSymbolName(value: string): string {
  const normalizedValue = value.trim();
  const lastDotIndex = normalizedValue.lastIndexOf('.');
  const simpleName = lastDotIndex >= 0 ? normalizedValue.slice(lastDotIndex + 1) : normalizedValue;
  return simpleName.split('$')[0];
}

function lastPathSegment(value: string): string {
  const normalizedValue = value.trim().replace(/\\/g, '/');
  const tokens = normalizedValue.split('/');
  return tokens[tokens.length - 1] || normalizedValue;
}

function stripExtension(value: string): string {
  const lastDotIndex = value.lastIndexOf('.');
  return lastDotIndex > 0 ? value.slice(0, lastDotIndex) : value;
}

function firstDefinedText(...values: Array<string | null | undefined>): string | null {
  for (const value of values) {
    if (value && value.trim()) {
      return value;
    }
  }

  return null;
}

async function copyTextToClipboard(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return true;
    }
  } catch {
    // Fallback below.
  }

  try {
    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.setAttribute('readonly', 'true');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    const copied = document.execCommand('copy');
    textarea.remove();
    return copied;
  } catch {
    return false;
  }
}
