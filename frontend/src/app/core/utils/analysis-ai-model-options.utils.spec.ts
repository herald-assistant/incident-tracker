import { AnalysisAiModelOptionsResponse } from '../models/analysis.models';
import {
  defaultReasoningEffortForAiModel,
  listedDefaultAiModel,
  reasoningEffortsForAiModel
} from './analysis-ai-model-options.utils';

describe('analysis AI model options utils', () => {
  it('should prefer configured default reasoning effort when the selected model supports it', () => {
    const catalog = modelCatalog({
      defaultReasoningEffort: 'high',
      modelDefaultReasoningEffort: 'medium'
    });

    expect(defaultReasoningEffortForAiModel(catalog, 'gpt-5.4')).toBe('high');
  });

  it('should fall back to the model default when configured reasoning effort is unsupported', () => {
    const catalog = modelCatalog({
      defaultReasoningEffort: 'high',
      modelDefaultReasoningEffort: 'medium',
      reasoningEfforts: ['low', 'medium']
    });

    expect(defaultReasoningEffortForAiModel(catalog, 'gpt-5.4')).toBe('medium');
  });

  it('should expose catalog defaults when no concrete model is selected', () => {
    const catalog = modelCatalog({
      defaultReasoningEffort: 'high',
      modelDefaultReasoningEffort: 'medium'
    });

    expect(listedDefaultAiModel(catalog)).toBe('gpt-5.4');
    expect(reasoningEffortsForAiModel(catalog, '')).toEqual(['low', 'medium', 'high']);
    expect(defaultReasoningEffortForAiModel(catalog, '')).toBe('high');
  });
});

function modelCatalog(options: {
  defaultReasoningEffort: string;
  modelDefaultReasoningEffort: string;
  reasoningEfforts?: string[];
}): AnalysisAiModelOptionsResponse {
  const reasoningEfforts = options.reasoningEfforts ?? ['low', 'medium', 'high'];
  return {
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: options.defaultReasoningEffort,
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: [
      {
        id: 'gpt-5.4',
        name: 'GPT-5.4',
        supportsReasoningEffort: true,
        reasoningEfforts,
        defaultReasoningEffort: options.modelDefaultReasoningEffort
      }
    ]
  };
}
