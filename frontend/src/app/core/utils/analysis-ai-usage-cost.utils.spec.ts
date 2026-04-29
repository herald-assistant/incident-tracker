import { estimateAnalysisAiCost } from './analysis-ai-usage-cost.utils';

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
      model: 'gpt-5.2',
      outputTokens: 10921,
      totalTokens: 513962
    });

    expect(estimate).not.toBeNull();
    expect(estimate?.newInputTokens).toBe(78977);
    expect(estimate?.cachedInputTokens).toBe(424064);
    expect(estimate?.dollars).toBeCloseTo(0.3653, 4);
    expect(estimate?.credits).toBeCloseTo(36.53, 2);
  });
});
