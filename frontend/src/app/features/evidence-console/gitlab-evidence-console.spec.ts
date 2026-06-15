import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  GitLabEndpointUseCaseContextResponse,
  GitLabRepositoryEndpointsResponse
} from '../../core/services/evidence-api.service';
import { GitLabEvidenceConsoleComponent } from './gitlab-evidence-console';

describe('GitLabEvidenceConsoleComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GitLabEvidenceConsoleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideLocationMocks(),
        provideRouter([])
      ]
    }).compileComponents();
  });

  it('should render endpoint use-case context as flow tree', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);

    fixture.componentInstance.gitLabEndpointUseCaseContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildUseCaseContextResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Flow tree');
    expect(compiled.textContent).toContain('CustomerController.java');
    expect(compiled.textContent).toContain('CustomerService.java');
    expect(compiled.textContent).toContain('CustomerMapper.java');
    expect(compiled.textContent).toContain('INJECTED PORT CALL');
    expect(compiled.textContent).toContain('INTERFACE IMPL');
    expect(compiled.textContent).toContain('REPOSITORY IMPL');
    expect(compiled.textContent).not.toContain('INTERFACE IMPLEMENTATION');
    expect(compiled.textContent).not.toContain('REPOSITORY IMPLEMENTATION');
    expect(compiled.textContent).toContain('1 poza flow');

    const injectedEdge = Array.from(
      compiled.querySelectorAll<HTMLElement>('.flow-tree-node__edge')
    ).find((edge) => edge.textContent?.includes('INJECTED PORT CALL'));
    const repositoryRole = Array.from(compiled.querySelectorAll<HTMLElement>('.panel-chip')).find(
      (chip) => chip.textContent?.includes('REPOSITORY IMPL')
    );
    const confidencePill = compiled.querySelector<HTMLElement>('.confidence-pill--high');

    expect(injectedEdge?.getAttribute('title') || '').toContain('wstrzykniętą przez DI');
    expect(repositoryRole?.getAttribute('title') || '').toContain('adapter dostępu do danych');
    expect(confidencePill?.getAttribute('title') || '').toContain('Wysoka pewność');
  });

  it('should expose icon actions for every GitLab JSON response', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const copyButtons = compiled.querySelectorAll(
      '.json-icon-button[aria-label^="Kopiuj JSON"]'
    );
    const downloadButtons = compiled.querySelectorAll(
      '.json-icon-button[aria-label^="Pobierz JSON"]'
    );
    const loadButtons = compiled.querySelectorAll(
      '.json-icon-button[aria-label^="Załaduj JSON"]'
    );

    expect(copyButtons).toHaveLength(4);
    expect(downloadButtons).toHaveLength(4);
    expect(loadButtons).toHaveLength(4);
    expect(copyButtons[0].textContent).toContain('content_copy');
    expect(downloadButtons[0].textContent).toContain('download');
    expect(loadButtons[0].textContent).toContain('upload_file');
    expect(compiled.textContent).not.toContain('Kopiuj JSON');
    expect(compiled.textContent).not.toContain('Załaduj JSON');
    expect(copyButtons[1].getAttribute('aria-label')).toContain('endpoint inventory');
    expect(downloadButtons[2].getAttribute('aria-label')).toContain('endpoint use-case context');
    expect(loadButtons[2].getAttribute('aria-label')).toContain('endpoint use-case context');

    const routeChip = compiled.querySelector<HTMLElement>('.route-chip');
    const statusPill = compiled.querySelector<HTMLElement>('.status-pill');
    expect(routeChip?.getAttribute('title') || '').toContain('/api/gitlab/repository/search');
    expect(statusPill?.getAttribute('title') || '').toContain('gotowy do ręcznego testu');
  });

  it('should load a downloaded endpoint use-case JSON file into the same card', async () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const file = new File(
      [JSON.stringify(buildUseCaseContextResponse())],
      'gitlab-endpoint-use-case-context.json',
      { type: 'application/json' }
    );

    await fixture.componentInstance.loadJsonResponseFile(
      'endpoint-use-case-context',
      fileInputChangeEvent(file)
    );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.gitLabEndpointUseCaseContextState().status).toBe('success');
    expect(fixture.componentInstance.gitLabEndpointUseCaseContextState().message).toContain(
      'gitlab-endpoint-use-case-context.json'
    );
    expect(compiled.textContent).toContain('Flow tree');
    expect(compiled.textContent).toContain('CustomerService.java');
  });

  it('should render unresolved use-case references as collapsed details', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.gitLabEndpointUseCaseContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildUseCaseContextResponse({
        unresolved: [
          {
            symbol: 'CustomerRiskPolicy',
            ownerPath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
            reason: 'No matching implementation found.',
            searchedKeywords: ['CustomerRiskPolicy'],
            candidates: []
          }
        ]
      }),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const unresolvedDetails = [...compiled.querySelectorAll('details')].find((details) =>
      details.textContent?.includes('Unresolved')
    );

    expect(unresolvedDetails).toBeTruthy();
    expect(unresolvedDetails?.hasAttribute('open')).toBe(false);
    expect(unresolvedDetails?.querySelector('summary')?.textContent).toContain('1');
  });

  it('should render endpoint inventory cards as collapsed compact details', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.gitLabEndpointState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildEndpointInventoryResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const endpointCard = compiled.querySelector('details.endpoint-card');
    const main = endpointCard?.querySelector('.endpoint-card__main');
    const contextButton = main?.querySelector('.endpoint-card__context-button');

    expect(endpointCard).toBeTruthy();
    expect(endpointCard?.hasAttribute('open')).toBe(false);
    expect(main?.textContent).toContain('/crm/customers/{customerId}');
    expect(main?.textContent).toContain('Pobranie profilu klienta CRM');
    expect(main?.textContent).toContain('CustomerController#getCustomer');
    expect(contextButton?.textContent).toContain('Użyj do contextu');

    endpointCard?.setAttribute('open', '');
    fixture.detectChanges();

    expect(endpointCard?.textContent).toContain('OPENAPI YAML');
    expect(endpointCard?.textContent).toContain('getCustomer');
    expect(endpointCard?.textContent).toContain('customerId');
    expect(endpointCard?.textContent).toContain('Identyfikator klienta CRM');
  });
});

function fileInputChangeEvent(file: File): Event {
  const input = document.createElement('input');
  input.type = 'file';
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file]
  });
  return { target: input } as unknown as Event;
}

function buildEndpointInventoryResponse(): GitLabRepositoryEndpointsResponse {
  return {
    group: 'CRM',
    projectName: 'crm-customer-service',
    branch: 'main',
    endpointPathPrefix: null,
    httpMethod: null,
    candidateFileCount: 1,
    scannedFileCount: 1,
    scannedFileLimitReached: false,
    endpoints: [
      {
        endpointId: 'GET /crm/customers/{customerId} -> CustomerController#getCustomer',
        httpMethods: ['GET'],
        path: '/crm/customers/{customerId}',
        pathExpression: null,
        controllerClass: 'com.example.crm.customer.api.CustomerController',
        handlerMethod: 'getCustomer',
        filePath: 'src/main/java/com/example/crm/customer/api/CustomerController.java',
        lineStart: 10,
        lineEnd: 18,
        requestTypes: [],
        responseTypes: ['CustomerModel'],
        annotations: ['GetMapping'],
        documentation: {
          source: 'OPENAPI_YAML',
          summary: 'Pobranie profilu klienta CRM',
          description: 'Zwraca podstawowe dane klienta oraz status relacji z firmą.',
          operationId: 'getCustomer',
          tags: ['Customer'],
          parameters: [
            {
              name: 'customerId',
              in: 'path',
              required: true,
              type: 'string(uuid)',
              description: 'Identyfikator klienta CRM'
            },
            {
              name: 'includeInactive',
              in: 'query',
              required: false,
              type: 'boolean',
              description: 'Czy dołączyć nieaktywne relacje klienta.'
            }
          ]
        },
        confidence: 'HIGH',
        limitations: [],
        suggestedNextReads: []
      }
    ],
    limitations: []
  };
}

function buildUseCaseContextResponse(
  overrides: Partial<GitLabEndpointUseCaseContextResponse> = {}
): GitLabEndpointUseCaseContextResponse {
  return {
    repository: {
      group: 'CRM',
      projectName: 'crm-customer-service',
      branch: 'main'
    },
    endpoint: {
      endpointId: 'GET /crm/customers/{customerId} -> CustomerController#getCustomer',
      httpMethods: ['GET'],
      path: '/crm/customers/{customerId}',
      pathExpression: null,
      controllerClass: 'com.example.crm.customer.api.CustomerController',
      handlerMethod: 'getCustomer',
      filePath: 'src/main/java/com/example/crm/customer/api/CustomerController.java',
      lineStart: 10,
      lineEnd: 18,
      requestTypes: [],
      responseTypes: ['CustomerModel'],
      annotations: ['GetMapping'],
      confidence: 'HIGH',
      limitations: [],
      suggestedNextReads: []
    },
    files: [
      {
        path: 'src/main/java/com/example/crm/customer/api/CustomerController.java',
        role: 'CONTROLLER',
        priority: 1,
        symbols: ['getCustomer'],
        reason: 'Endpoint handler and local controller flow.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
        role: 'USE_CASE_SERVICE',
        priority: 2,
        symbols: ['getCustomer'],
        reason: 'Injected dependency used by traversed method.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/api/CustomerMapper.java',
        role: 'MAPPER',
        priority: 5,
        symbols: ['from'],
        reason: 'Mapper method used by service.',
        confidence: 'MEDIUM'
      },
      {
        path: 'src/main/java/com/example/crm/customer/adapter/out/CustomerRepository.java',
        role: 'REPOSITORY_IMPLEMENTATION',
        priority: 4,
        symbols: ['getCustomer'],
        reason: 'Implementation candidate for injected interface.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/api/CustomerModel.java',
        role: 'WEB_MODEL',
        priority: 7,
        symbols: ['CustomerModel'],
        reason: 'Response model returned by endpoint.',
        confidence: 'MEDIUM'
      }
    ],
    relations: [
      {
        from: 'GET /crm/customers/{customerId} -> CustomerController#getCustomer',
        to: 'com.example.crm.customer.api.CustomerController#getCustomer',
        kind: 'ENDPOINT_HANDLER',
        confidence: 'HIGH',
        reason: 'Endpoint inventory resolved this handler method.'
      },
      {
        from: 'com.example.crm.customer.api.CustomerController#getCustomer',
        to: 'com.example.crm.customer.application.CustomerService#getCustomer',
        kind: 'INJECTED_PORT_CALL',
        confidence: 'HIGH',
        reason: 'Method call on injected dependency customerService.'
      },
      {
        from: 'com.example.crm.customer.application.CustomerService#getCustomer',
        to: 'com.example.crm.customer.api.CustomerMapper#from',
        kind: 'MAPPER_CALL',
        confidence: 'MEDIUM',
        reason: 'Mapper converts domain object to web model.'
      },
      {
        from: 'com.example.crm.customer.application.CustomerService#getCustomer',
        to: 'com.example.crm.customer.adapter.out.CustomerRepository#getCustomer',
        kind: 'INTERFACE_IMPLEMENTATION',
        confidence: 'HIGH',
        reason: 'Implementation candidate for injected interface.'
      }
    ],
    unresolved: [],
    limitations: [],
    suggestedNextReads: [],
    limits: {
      maxDepth: 5,
      maxFiles: 25,
      maxReadFiles: 80,
      maxDepthReached: false,
      maxFilesReached: false,
      readFileCount: 3,
      readFileLimitReached: false
    },
    confidence: 'HIGH',
    ...overrides
  };
}

