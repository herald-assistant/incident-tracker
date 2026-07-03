import {
  AnalysisAiModelOption,
  AnalysisAiModelOptionsResponse
} from '../models/analysis.models';

export const EMPTY_ANALYSIS_AI_MODEL_OPTIONS: AnalysisAiModelOptionsResponse = {
  defaultModel: '',
  defaultReasoningEffort: '',
  defaultReasoningEfforts: [],
  models: []
};

export function normalizeAnalysisAiModelOptions(
  options: AnalysisAiModelOptionsResponse | null
): AnalysisAiModelOptionsResponse {
  if (!options) {
    return EMPTY_ANALYSIS_AI_MODEL_OPTIONS;
  }

  return {
    defaultModel: normalizeText(options.defaultModel),
    defaultReasoningEffort: normalizeText(options.defaultReasoningEffort),
    defaultReasoningEfforts: uniqueTexts(options.defaultReasoningEfforts),
    models: normalizeModels(options.models)
  };
}

export function listedDefaultAiModel(catalog: AnalysisAiModelOptionsResponse): string {
  const defaultModel = normalizeText(catalog.defaultModel);
  return defaultModel && catalog.models.some((model) => model.id === defaultModel)
    ? defaultModel
    : '';
}

export function reasoningEffortsForAiModel(
  catalog: AnalysisAiModelOptionsResponse,
  modelId: string
): string[] {
  const normalizedModelId = normalizeText(modelId);
  if (!normalizedModelId) {
    return catalog.defaultReasoningEfforts;
  }

  const model = catalog.models.find((candidate) => candidate.id === normalizedModelId);
  return model?.supportsReasoningEffort ? model.reasoningEfforts : [];
}

export function defaultReasoningEffortForAiModel(
  catalog: AnalysisAiModelOptionsResponse,
  modelId: string
): string {
  const availableEfforts = reasoningEffortsForAiModel(catalog, modelId);
  const modelDefault = modelDefaultReasoningEffort(catalog, modelId);
  const defaultEffort = modelDefault || normalizeText(catalog.defaultReasoningEffort);

  return defaultEffort && availableEfforts.includes(defaultEffort) ? defaultEffort : '';
}

function normalizeModels(models: AnalysisAiModelOption[] | null | undefined): AnalysisAiModelOption[] {
  if (!Array.isArray(models)) {
    return [];
  }

  const seenModelIds = new Set<string>();
  const normalizedModels: AnalysisAiModelOption[] = [];

  for (const model of models) {
    if (!model || typeof model.id !== 'string') {
      continue;
    }

    const id = normalizeText(model.id);
    if (!id || seenModelIds.has(id)) {
      continue;
    }

    seenModelIds.add(id);
    normalizedModels.push({
      id,
      name: normalizeText(model.name) || id,
      supportsReasoningEffort: Boolean(model.supportsReasoningEffort),
      reasoningEfforts: uniqueTexts(model.reasoningEfforts),
      defaultReasoningEffort: normalizeText(model.defaultReasoningEffort)
    });
  }

  return normalizedModels;
}

function modelDefaultReasoningEffort(
  catalog: AnalysisAiModelOptionsResponse,
  modelId: string
): string {
  const normalizedModelId = normalizeText(modelId);
  if (!normalizedModelId) {
    return '';
  }

  const model = catalog.models.find((candidate) => candidate.id === normalizedModelId);
  return normalizeText(model?.defaultReasoningEffort);
}

function uniqueTexts(values: string[] | null | undefined): string[] {
  if (!Array.isArray(values)) {
    return [];
  }

  const seenValues = new Set<string>();
  const normalizedValues: string[] = [];

  for (const value of values) {
    if (typeof value !== 'string') {
      continue;
    }

    const normalizedValue = normalizeText(value);
    if (!normalizedValue || seenValues.has(normalizedValue)) {
      continue;
    }

    seenValues.add(normalizedValue);
    normalizedValues.push(normalizedValue);
  }

  return normalizedValues;
}

function normalizeText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}
