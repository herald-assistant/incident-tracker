import { AnalysisAiInvocation } from '../models/analysis.models';

export function invocationFixture(preparedPrompt = 'CRM assessment prompt', rawResponse: string | null = null): AnalysisAiInvocation {
  return {
    invocationId: 'call-1', role: 'ASSESSMENT', status: 'COMPLETED', partNumber: 1, partCount: 1,
    evidenceScope: ['CRM/customers!1#file:0:src/Customer.java'], estimatedInputTokens: 100,
    promptTokenLimit: 1000, reservedTokens: 10, preparedPrompt, rawResponse, findings: [], visibilityLimits: [],
    sessionId: 'crm-session', usage: null, errorMessage: null, startedAt: '2026-07-01T10:00:00Z', completedAt: '2026-07-01T10:01:00Z'
  };
}
