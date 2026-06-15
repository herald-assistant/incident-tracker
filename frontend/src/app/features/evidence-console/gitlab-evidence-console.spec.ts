import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GitLabEndpointUseCaseContextResponse } from '../../core/services/evidence-api.service';
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
    expect(compiled.textContent).toContain('1 poza flow');
  });
});

function buildUseCaseContextResponse(): GitLabEndpointUseCaseContextResponse {
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
    confidence: 'HIGH'
  };
}

