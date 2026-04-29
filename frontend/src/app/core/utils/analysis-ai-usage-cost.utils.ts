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

const DEFAULT_PRICING_MODEL = 'GPT-5.2';

const COPILOT_MODEL_PRICING: CopilotModelPricing[] = [
  modelPricing('GPT-5.5', 5, 0.5, 30, ['gpt-5.5']),
  modelPricing('GPT-5.4 mini', 0.75, 0.075, 4.5, ['gpt-5.4 mini']),
  modelPricing('GPT-5.4 nano', 0.2, 0.02, 1.25, ['gpt-5.4 nano']),
  modelPricing('GPT-5.4', 2.5, 0.25, 15, ['gpt-5.4']),
  modelPricing('GPT-5.3-Codex', 1.75, 0.175, 14, ['gpt-5.3-codex']),
  modelPricing('GPT-5.2-Codex', 1.75, 0.175, 14, ['gpt-5.2-codex']),
  modelPricing('GPT-5.2', 1.75, 0.175, 14, ['gpt-5.2']),
  modelPricing('GPT-5 mini', 0.25, 0.025, 2, ['gpt-5 mini', 'gpt-5-mini']),
  modelPricing('GPT-4.1', 2, 0.5, 8, ['gpt-4.1']),
  modelPricing('Claude Opus 4.7', 5, 0.5, 25, ['claude opus 4.7', 'claude-opus-4.7'], 6.25),
  modelPricing('Claude Opus 4.6', 5, 0.5, 25, ['claude opus 4.6', 'claude-opus-4.6'], 6.25),
  modelPricing('Claude Opus 4.5', 5, 0.5, 25, ['claude opus 4.5', 'claude-opus-4.5'], 6.25),
  modelPricing('Claude Sonnet 4.6', 3, 0.3, 15, ['claude sonnet 4.6', 'claude-sonnet-4.6'], 3.75),
  modelPricing('Claude Sonnet 4.5', 3, 0.3, 15, ['claude sonnet 4.5', 'claude-sonnet-4.5'], 3.75),
  modelPricing('Claude Sonnet 4', 3, 0.3, 15, ['claude sonnet 4', 'claude-sonnet-4'], 3.75),
  modelPricing('Claude Haiku 4.5', 1, 0.1, 5, ['claude haiku 4.5', 'claude-haiku-4.5'], 1.25),
  modelPricing('Gemini 3.1 Pro', 2, 0.2, 12, ['gemini 3.1 pro', 'gemini-3.1-pro']),
  modelPricing('Gemini 3 Flash', 0.5, 0.05, 3, ['gemini 3 flash', 'gemini-3-flash']),
  modelPricing('Gemini 2.5 Pro', 1.25, 0.125, 10, ['gemini 2.5 pro', 'gemini-2.5-pro']),
  modelPricing('Grok Code Fast 1', 0.2, 0.02, 1.5, ['grok code fast 1', 'grok-code-fast-1']),
  modelPricing('Goldeneye', 1.25, 0.125, 10, ['goldeneye']),
  modelPricing('Raptor mini', 0.25, 0.025, 2, ['raptor mini', 'raptor-mini'])
];

export function estimateAnalysisAiCost(usage: AnalysisAiUsage | null): AnalysisAiCostEstimate | null {
  if (!usage || usage.totalTokens <= 0) {
    return null;
  }

  const pricing = findPricing(usage.model);
  const inputTokens = safeTokenCount(usage.inputTokens);
  const cachedInputTokens = Math.min(safeTokenCount(usage.cacheReadTokens), inputTokens);
  const newInputTokens = Math.max(inputTokens - cachedInputTokens, 0);
  const cacheWriteTokens = safeTokenCount(usage.cacheWriteTokens);
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
