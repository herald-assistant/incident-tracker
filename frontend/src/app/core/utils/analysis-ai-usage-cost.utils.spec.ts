import { estimateAnalysisAiCost } from './analysis-ai-usage-cost.utils';
import type { AnalysisAiUsage } from '../models/analysis.models';

describe('estimateAnalysisAiCost', () => {
  it('should estimate credits and dollars from token usage with cached input discount', () => {
    const estimate = estimateAnalysisAiCost({
      apiCallCount: 7,
      apiDurationMs: 193211,
      cacheReadTokens: 424064,
      cacheWriteTokens: 0,
      contextCurrentTokens: 83926,
      contextMessages: 29,
      contextTokenLimit: 272000,
      cost: 7,
      inputTokens: 503041,
      model: 'gpt-5.3-codex',
      outputTokens: 10921,
      totalTokens: 513962
    });

    expect(estimate).not.toBeNull();
    expect(estimate?.newInputTokens).toBe(78977);
    expect(estimate?.cachedInputTokens).toBe(424064);
    expect(estimate?.dollars).toBeCloseTo(0.3653, 4);
    expect(estimate?.credits).toBeCloseTo(36.53, 2);
  });

  it('should charge cache writes for current OpenAI models', () => {
    const estimate = estimateAnalysisAiCost(usageForModel('gpt-5.6-sol', {
      inputTokens: 100_000,
      cacheReadTokens: 25_000,
      cacheWriteTokens: 10_000,
      outputTokens: 50_000,
      totalTokens: 150_000
    }));

    expect(estimate?.pricingModel).toBe('GPT-5.6 Sol');
    expect(estimate?.newInputTokens).toBe(65_000);
    expect(estimate?.cacheWriteUsdPerMillion).toBe(5);
    expect(estimate?.dollars).toBeCloseTo(1.32, 6);
    expect(estimate?.credits).toBeCloseTo(132, 6);
  });

  it('should count cache writes once in the UX Inspector run estimate', () => {
    const estimate = estimateAnalysisAiCost(usageForModel('claude-sonnet-5', {
      apiCallCount: 9,
      inputTokens: 203_559,
      cacheReadTokens: 170_861,
      cacheWriteTokens: 32_680,
      outputTokens: 4_227,
      totalTokens: 207_786
    }));

    expect(estimate?.newInputTokens).toBe(18);
    expect(estimate?.dollars).toBeCloseTo(0.1581782, 7);
    expect(estimate?.credits).toBeCloseTo(15.81782, 5);
  });

  [
    { model: 'gpt-5.4-nano', pricingModel: 'GPT-5.4 nano', input: 0.2 },
    { model: 'gpt-5.4-mini', pricingModel: 'GPT-5.4 mini', input: 0.75 },
    { model: 'gpt-6-astra', pricingModel: 'GPT-6 Astra', input: 10 },
    { model: 'Claude Opus 4.8 (fast mode) (preview)', pricingModel: 'Claude Opus 4.8 (fast mode) (preview)', input: 10 },
    { model: 'claude-opus-4.8', pricingModel: 'Claude Opus 4.8', input: 5 },
    { model: 'claude-fable-5.1', pricingModel: 'Claude Fable 5.1', input: 10 },
    { model: 'claude-fable-5', pricingModel: 'Claude Fable 5', input: 10 },
    { model: 'gemini-3.8-flash', pricingModel: 'Gemini 3.8 Flash', input: 0.75 },
    { model: 'mai-code-1.1-flash', pricingModel: 'MAI-Code-1.1-Flash', input: 0.2 },
    { model: 'grok-4.6', pricingModel: 'Grok 4.6', input: 2 },
    { model: 'kimi-k2.7-code', pricingModel: 'Kimi K2.7 Code', input: 0.95 }
  ].forEach(({ model, pricingModel, input }) => {
    it(`should use the current pricing for ${model}`, () => {
      const estimate = estimateAnalysisAiCost(usageForModel(model));

      expect(estimate?.pricingModel).toBe(pricingModel);
      expect(estimate?.inputUsdPerMillion).toBe(input);
      expect(estimate?.usedFallbackPricing).toBe(false);
    });
  });

  it('should use the active fallback for retired or unknown models', () => {
    for (const model of ['gpt-5.2', 'claude-opus-4.6', 'unknown-model']) {
      const estimate = estimateAnalysisAiCost(usageForModel(model));

      expect(estimate?.pricingModel).toBe('GPT-5.3-Codex');
      expect(estimate?.usedFallbackPricing).toBe(true);
    }
  });
});

function usageForModel(model: string, overrides: Partial<AnalysisAiUsage> = {}): AnalysisAiUsage {
  return {
    apiCallCount: 1,
    apiDurationMs: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
    contextCurrentTokens: null,
    contextMessages: null,
    contextTokenLimit: null,
    cost: 0,
    inputTokens: 100_000,
    model,
    outputTokens: 0,
    totalTokens: 100_000,
    ...overrides
  };
}
