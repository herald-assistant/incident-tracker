import { AnalysisAiUsage } from '../models/analysis.models';

export interface AnalysisAiCostEstimate {
  totalTokens: number;
  newInputTokens: number;
  cachedInputTokens: number;
  cacheWriteTokens: number;
  outputTokens: number;
  dollars: number;
  credits: number;
  pricingModel: string;
  usedFallbackPricing: boolean;
  inputUsdPerMillion: number;
  cachedInputUsdPerMillion: number;
  cacheWriteUsdPerMillion: number | null;
  outputUsdPerMillion: number;
}

interface CopilotModelPricing {
  model: string;
  aliases: string[];
  inputUsdPerMillion: number;
  cachedInputUsdPerMillion: number;
  cacheWriteUsdPerMillion?: number;
  outputUsdPerMillion: number;
}

export const GITHUB_AI_CREDIT_USD = 0.01;

const DEFAULT_PRICING_MODEL = 'GPT-5.3-Codex';

// Default-tier USD rates per 1M tokens, checked on 2026-09-13:
// https://docs.github.com/en/copilot/reference/ai-models/supported-models
// https://docs.github.com/en/copilot/reference/copilot-billing/models-and-pricing
// Run-level token aggregates cannot reliably select per-call Long context rates.
const COPILOT_MODEL_PRICING: CopilotModelPricing[] = [
  modelPricing('GPT-6 Astra', 10, 1, 50, ['gpt-6-astra', 'gpt-6 astra'], 12.5),
  modelPricing('GPT-5.6 Luna', 0.2, 0.02, 1.2, ['gpt-5.6-luna', 'gpt-5.6 luna'], 0.25),
  modelPricing('GPT-5.6 Sol', 4, 0.4, 20, ['gpt-5.6-sol', 'gpt-5.6 sol'], 5),
  modelPricing('GPT-5.6 Terra', 2, 0.2, 12, ['gpt-5.6-terra', 'gpt-5.6 terra'], 2.5),
  modelPricing('GPT-5.5', 5, 0.5, 30, ['gpt-5.5']),
  modelPricing('GPT-5.4 mini', 0.75, 0.075, 4.5, ['gpt-5.4 mini']),
  modelPricing('GPT-5.4 nano', 0.2, 0.02, 1.25, ['gpt-5.4 nano']),
  modelPricing('GPT-5.4', 2.5, 0.25, 15, ['gpt-5.4']),
  modelPricing('GPT-5.3-Codex', 1.75, 0.175, 14, ['gpt-5.3-codex']),
  modelPricing('GPT-5 mini', 0.25, 0.025, 2, ['gpt-5 mini', 'gpt-5-mini']),
  modelPricing('Claude Fable 5.1', 10, 0.25, 50, ['claude fable 5.1', 'claude-fable-5.1'], 12.5),
  modelPricing('Claude Fable 5', 10, 1, 50, ['claude fable 5', 'claude-fable-5'], 12.5),
  modelPricing('Claude Opus 4.8 (fast mode) (preview)', 10, 1, 50, ['claude opus 4.8 fast', 'claude-opus-4.8-fast'], 12.5),
  modelPricing('Claude Opus 5', 5, 0.5, 25, ['claude opus 5', 'claude-opus-5'], 6.25),
  modelPricing('Claude Opus 4.8', 5, 0.5, 25, ['claude opus 4.8', 'claude-opus-4.8'], 6.25),
  modelPricing('Claude Opus 4.7', 5, 0.5, 25, ['claude opus 4.7', 'claude-opus-4.7'], 6.25),
  modelPricing('Claude Sonnet 5', 2, 0.2, 10, ['claude sonnet 5', 'claude-sonnet-5'], 2.5),
  modelPricing('Claude Sonnet 4.6', 3, 0.3, 15, ['claude sonnet 4.6', 'claude-sonnet-4.6'], 3.75),
  modelPricing('Claude Haiku 4.5', 1, 0.1, 5, ['claude haiku 4.5', 'claude-haiku-4.5'], 1.25),
  modelPricing('Gemini 3.5 Flash', 1.5, 0.15, 9, ['gemini 3.5 flash', 'gemini-3.5-flash']),
  // GitHub's Gemini 3.6-3.8 Flash rates are promotional through 2026-12-31.
  modelPricing('Gemini 3.6 Flash', 0.75, 0.075, 3.75, ['gemini 3.6 flash', 'gemini-3.6-flash']),
  modelPricing('Gemini 3.7 Flash', 0.75, 0.075, 3.75, ['gemini 3.7 flash', 'gemini-3.7-flash']),
  modelPricing('Gemini 3.8 Flash', 0.75, 0.075, 3.75, ['gemini 3.8 flash', 'gemini-3.8-flash']),
  modelPricing('MAI-Code-1.1-Flash', 0.2, 0.02, 1.2, ['mai-code-1.1-flash', 'mai code 1.1 flash']),
  modelPricing('Grok 4.5', 2, 0.5, 6, ['grok 4.5', 'grok-4.5']),
  modelPricing('Grok 4.6', 2, 0.5, 6, ['grok 4.6', 'grok-4.6']),
  modelPricing('Kimi K2.7 Code', 0.95, 0.19, 4, ['kimi k2.7 code', 'kimi-k2.7-code']),
  modelPricing('Kimi K3', 3, 0.3, 15, ['kimi k3', 'kimi-k3'])
];

export function estimateAnalysisAiCost(usage: AnalysisAiUsage | null): AnalysisAiCostEstimate | null {
  if (!usage || usage.totalTokens <= 0) {
    return null;
  }

  const pricing = findPricing(usage.model);
  const inputTokens = safeTokenCount(usage.inputTokens);
  const cachedInputTokens = Math.min(safeTokenCount(usage.cacheReadTokens), inputTokens);
  const cacheWriteTokens = Math.min(
    safeTokenCount(usage.cacheWriteTokens),
    Math.max(inputTokens - cachedInputTokens, 0)
  );
  const newInputTokens = Math.max(inputTokens - cachedInputTokens - cacheWriteTokens, 0);
  const outputTokens = safeTokenCount(usage.outputTokens);
  const cacheWriteUsdPerMillion = pricing.pricing.cacheWriteUsdPerMillion ?? null;

  const dollars =
    tokensToDollars(newInputTokens, pricing.pricing.inputUsdPerMillion) +
    tokensToDollars(cachedInputTokens, pricing.pricing.cachedInputUsdPerMillion) +
    tokensToDollars(outputTokens, pricing.pricing.outputUsdPerMillion) +
    tokensToDollars(cacheWriteTokens, cacheWriteUsdPerMillion ?? 0);

  return {
    totalTokens: safeTokenCount(usage.totalTokens),
    newInputTokens,
    cachedInputTokens,
    cacheWriteTokens,
    outputTokens,
    dollars,
    credits: dollars / GITHUB_AI_CREDIT_USD,
    pricingModel: pricing.pricing.model,
    usedFallbackPricing: pricing.usedFallbackPricing,
    inputUsdPerMillion: pricing.pricing.inputUsdPerMillion,
    cachedInputUsdPerMillion: pricing.pricing.cachedInputUsdPerMillion,
    cacheWriteUsdPerMillion,
    outputUsdPerMillion: pricing.pricing.outputUsdPerMillion
  };
}

function findPricing(model: string | null | undefined): {
  pricing: CopilotModelPricing;
  usedFallbackPricing: boolean;
} {
  const normalizedModel = normalizeModelName(model || '');
  const pricing = COPILOT_MODEL_PRICING.find((candidate) =>
    candidate.aliases.some((alias) => normalizedModel.includes(normalizeModelName(alias)))
  );

  if (pricing) {
    return { pricing, usedFallbackPricing: false };
  }

  return {
    pricing: COPILOT_MODEL_PRICING.find((candidate) => candidate.model === DEFAULT_PRICING_MODEL)!,
    usedFallbackPricing: true
  };
}

function modelPricing(
  model: string,
  inputUsdPerMillion: number,
  cachedInputUsdPerMillion: number,
  outputUsdPerMillion: number,
  aliases: string[],
  cacheWriteUsdPerMillion?: number
): CopilotModelPricing {
  return {
    model,
    aliases,
    inputUsdPerMillion,
    cachedInputUsdPerMillion,
    outputUsdPerMillion,
    cacheWriteUsdPerMillion
  };
}

function normalizeModelName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9.]+/g, ' ').trim();
}

function safeTokenCount(value: number | null | undefined): number {
  const numericValue = Number(value ?? 0);
  return Number.isFinite(numericValue) ? Math.max(0, Math.round(numericValue)) : 0;
}

function tokensToDollars(tokens: number, usdPerMillion: number): number {
  return (tokens * usdPerMillion) / 1_000_000;
}
