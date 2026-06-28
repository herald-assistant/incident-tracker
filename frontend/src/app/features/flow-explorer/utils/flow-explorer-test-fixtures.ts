import { FlowExplorerAiResponse } from '../models/flow-explorer.models';

export function flowExplorerAiResponseFixture(
  overrides: Partial<FlowExplorerAiResponse> = {}
): FlowExplorerAiResponse {
  return {
    goal: 'DEEP_DISCOVERY',
    audience: 'business_or_system_analyst_tester',
    overview: {
      markdown: 'The endpoint reads the requested customer.',
      confidence: 'high',
      sourceRefs: ['CustomerController.getCustomer L12-L24']
    },
    sections: [
      {
        id: 'FUNCTIONAL_FLOW',
        title: 'Functional flow',
        mode: 'deep',
        markdown: 'The controller delegates to CustomerService.',
        sourceRefs: ['CustomerService.getCustomer L30-L44'],
        visibilityLimits: [],
        openQuestions: []
      },
      {
        id: 'VALIDATIONS',
        title: 'Validations',
        mode: 'compact',
        markdown: 'Customer id is required.',
        sourceRefs: [],
        visibilityLimits: [],
        openQuestions: []
      },
      {
        id: 'PERSISTENCE',
        title: 'Persistence',
        mode: 'compact',
        markdown: 'CustomerRepository.findById loads the aggregate.',
        sourceRefs: [],
        visibilityLimits: [],
        openQuestions: []
      },
      {
        id: 'INTEGRATIONS',
        title: 'Integrations',
        mode: 'compact',
        markdown: 'No external integration is visible in initial evidence.',
        sourceRefs: [],
        visibilityLimits: [],
        openQuestions: []
      }
    ],
    globalVisibilityLimits: [],
    globalOpenQuestions: [],
    sourceReferences: [],
    confidence: 'high',
    followUpPrompts: ['Sprawdz, czy nieaktywny klient powinien blokowac ten flow.'],
    ...overrides
  };
}
