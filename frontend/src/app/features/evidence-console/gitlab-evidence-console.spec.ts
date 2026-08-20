import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  GitLabAngularRouteBranchSliceResponse,
  GitLabFrontendCatalogResponse,
  GitLabFrontendScreenContextResponse,
  GitLabFrontendScreenReachabilityResponse,
  GitLabEndpointUseCaseContextResponse,
  GitLabJavaMethodUseCaseContextResponse,
  GitLabJavaMethodSliceResponse,
  GitLabOpenApiEndpointSliceResponse,
  GitLabRepositoryEndpointsResponse,
  GitLabRepositoryFilesByPathResponse,
  GitLabTypeScriptSymbolSliceResponse
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

    fixture.componentInstance.selectedToolKey.set('endpoint-use-case-context');
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
    expect(compiled.textContent).toContain('Użyj plików do read');
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
    expect(compiled.querySelector('.flow-node-details')).toBeNull();

    const serviceNode = fixture.componentInstance.gitLabUseCaseTree()?.rows.find(
      (row) => row.node.label === 'CustomerService.java'
    )?.node;
    expect(serviceNode).toBeTruthy();
    fixture.componentInstance.selectGitLabUseCaseTreeNode(serviceNode!);
    fixture.detectChanges();

    const serviceTreeNode = Array.from(
      compiled.querySelectorAll<HTMLElement>('.flow-tree-node')
    ).find((node) => node.textContent?.includes('CustomerService.java'));
    const serviceTooltip = serviceTreeNode?.querySelector<HTMLElement>('.flow-node-tooltip');

    expect(serviceTreeNode?.className).toContain('flow-tree-node--selected');
    expect(serviceTooltip?.textContent).toContain('Selected node');
    expect(serviceTooltip?.textContent).toContain('getCustomer');
    expect(serviceTooltip?.textContent).toContain('L42-L50');
    expect(compiled.textContent).not.toContain('CustomerModel getCustomer(CustomerId customerId)');
  });

  it('should expose workbench actions only for selected GitLab result', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.gitlab-tool-button')).toHaveLength(15);
    expect(compiled.textContent).toContain('Repository Instructions');
    expect(compiled.textContent).toContain('Merge Request Search');
    expect(compiled.querySelector('.workbench-header')).toBeNull();
    expect(compiled.querySelector('.gitlab-result')).toBeFalsy();
    expect(compiled.textContent).not.toContain('Kopiuj JSON');
    expect(compiled.textContent).not.toContain('Załaduj JSON');

    const routeChip = compiled.querySelector<HTMLElement>('.route-chip');
    expect(routeChip?.getAttribute('title') || '').toContain('/api/gitlab/repository/search');

    fixture.componentInstance.gitLabRepositoryState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      endpoint: '/api/gitlab/repository/search',
      requestJson: '{"request":true}',
      response: { ok: true },
      responseJson: '{"ok":true}',
      durationMs: 12
    });
    fixture.detectChanges();

    const copyButtons = compiled.querySelectorAll('.json-icon-button[aria-label^="Kopiuj"]');
    const downloadButtons = compiled.querySelectorAll('.json-icon-button[aria-label^="Pobierz"]');
    const loadButtons = compiled.querySelectorAll('.json-icon-button[aria-label^="Załaduj"]');

    expect(compiled.querySelector('.gitlab-result')).toBeTruthy();
    expect(copyButtons).toHaveLength(2);
    expect(downloadButtons).toHaveLength(2);
    expect(loadButtons).toHaveLength(1);
    expect(copyButtons[0].textContent).toContain('content_copy');
    expect(downloadButtons[0].textContent).toContain('download');
    expect(loadButtons[0].textContent).toContain('upload_file');
    expect(compiled.querySelector('details.gitlab-json-panel--request')?.hasAttribute('open')).toBe(false);
    expect(compiled.querySelector('details.gitlab-json-panel:not(.gitlab-json-panel--request)')?.hasAttribute('open')).toBe(true);
  });

  it('should submit bounded frontend catalog and hand selected CRM screen to context', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.componentInstance.selectedToolKey.set('frontend-catalog');
    fixture.componentInstance.scopeForm.patchValue({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      branch: 'release/2026.08'
    });
    fixture.componentInstance.gitLabFrontendCatalogForm.controls.pathPrefixes.setValue(
      'apps/crm-agent'
    );

    fixture.componentInstance.submit(new Event('submit'));

    const request = httpTesting.expectOne('/api/gitlab/frontend/catalog');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      ref: 'release/2026.08',
      pathPrefixes: ['apps/crm-agent']
    });
    expect(request.request.body.maxInventoryFiles).toBeUndefined();
    request.flush(buildFrontendCatalogResponse());
    httpTesting.verify();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Angular/Nx route catalog');
    expect(compiled.textContent).toContain('/crm/customers/:customerId');
    expect(compiled.textContent).toContain('crm-ui-revision-20260815');

    fixture.componentInstance.useFrontendScreenForContext(
      buildFrontendCatalogResponse().nodes[0]
    );

    expect(fixture.componentInstance.selectedToolKey()).toBe('frontend-screen-context');
    expect(fixture.componentInstance.gitLabFrontendScreenContextForm.controls.screenId.value).toBe(
      'screen-crm-customer-profile'
    );
    expect(
      fixture.componentInstance.gitLabFrontendScreenContextForm.controls.pathPrefixes.value
    ).toBe('apps/crm-agent');
  });

  it('should render CRM frontend source manifest, signals and coverage', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.selectedToolKey.set('frontend-screen-context');
    fixture.componentInstance.gitLabFrontendScreenContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildFrontendScreenContextResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Screen source context');
    expect(compiled.textContent).toContain('Customer profile');
    expect(compiled.textContent).toContain('FORMS · READY');
    expect(compiled.textContent).toContain('REACTIVE_FORM · HIGH');
    expect(compiled.textContent).toContain('customer-profile.ts');
  });

  it('should submit and render a human-readable synthetic CRM screen BFS', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.componentInstance.selectedToolKey.set('frontend-screen-reachability');
    fixture.componentInstance.scopeForm.patchValue({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      branch: 'release/2026.08'
    });
    fixture.componentInstance.gitLabFrontendScreenReachabilityForm.patchValue({
      pathPrefixes: 'apps/crm-agent',
      screenId: 'screen-crm-customer-profile',
      expectedRevision: 'crm-ui-revision-20260815'
    });

    fixture.componentInstance.submit(new Event('submit'));

    const request = httpTesting.expectOne('/api/gitlab/frontend/screen-reachability');
    expect(request.request.body).toEqual({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      ref: 'release/2026.08',
      pathPrefixes: ['apps/crm-agent'],
      screenId: 'screen-crm-customer-profile',
      expectedRevision: 'crm-ui-revision-20260815'
    });
    request.flush(buildFrontendScreenReachabilityResponse());
    httpTesting.verify();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Screen reachability graph');
    expect(compiled.textContent).toContain('Human-readable BFS outline');
    expect(compiled.textContent).toContain('Component depth 0');
    expect(compiled.textContent).toContain('Functional and supporting dependencies');
    expect(compiled.textContent).toContain('CrmCustomerFacade');
  });

  it('should submit and render a reduced synthetic CRM Angular route branch slice', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.componentInstance.selectedToolKey.set('frontend-route-branch-slice');
    fixture.componentInstance.scopeForm.patchValue({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      branch: 'release/2026.08'
    });
    fixture.componentInstance.gitLabAngularRouteBranchSliceForm.patchValue({
      pathPrefixes: 'apps/crm-agent',
      screenId: 'screen-crm-customer-profile',
      expectedRevision: 'crm-ui-revision-20260815',
      includeDescendantRoutes: false,
      maxCharacters: '24000'
    });

    fixture.componentInstance.submit(new Event('submit'));

    const request = httpTesting.expectOne('/api/gitlab/frontend/route-branch-slice');
    expect(request.request.body).toEqual({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      ref: 'release/2026.08',
      pathPrefixes: ['apps/crm-agent'],
      screenId: 'screen-crm-customer-profile',
      expectedRevision: 'crm-ui-revision-20260815',
      includeDescendantRoutes: false,
      maxCharacters: 24000
    });
    request.flush(buildAngularRouteBranchSliceResponse());
    httpTesting.verify();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Angular route branch slice');
    expect(compiled.textContent).toContain('7200 saved');
    expect(compiled.textContent).toContain('3 routes omitted');
    expect(compiled.textContent).toContain('Child route frontier');
    expect(compiled.querySelector<HTMLTextAreaElement>('.source-output')?.value)
      .toContain('sibling route entry omitted');
  });

  it('should submit and render a reachable synthetic CRM TypeScript symbol slice', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const httpTesting = TestBed.inject(HttpTestingController);
    fixture.componentInstance.selectedToolKey.set('frontend-typescript-symbol-slice');
    fixture.componentInstance.scopeForm.patchValue({
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      branch: 'release/2026.08'
    });
    fixture.componentInstance.gitLabTypeScriptSymbolSliceForm.patchValue({
      pathPrefixes: 'apps/crm-agent',
      filePath: 'apps/crm-agent/src/app/customer/customer-profile.ts',
      declaringTypeName: 'CrmCustomerProfileComponent',
      templatePath: 'apps/crm-agent/src/app/customer/customer-profile.component.html',
      symbolSelectors: 'METHOD:saveCustomer@80\nPROPERTY:customerEffect',
      maxCharacters: '12000'
    });

    fixture.componentInstance.submit(new Event('submit'));

    const request = httpTesting.expectOne('/api/gitlab/frontend/typescript-symbol-slice');
    expect(request.request.body.symbolSelectors).toEqual([
      { name: 'saveCustomer', kind: 'METHOD', lineStart: 80 },
      { name: 'customerEffect', kind: 'PROPERTY', lineStart: undefined }
    ]);
    expect(request.request.body.includeTemplateBindings).toBe(true);
    expect(request.request.body.templatePath)
      .toBe('apps/crm-agent/src/app/customer/customer-profile.component.html');
    expect(request.request.body.maxCharacters).toBe(12000);
    request.flush(buildTypeScriptSymbolSliceResponse());
    httpTesting.verify();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('TypeScript symbol slice');
    expect(compiled.textContent).toContain('16200 saved');
    expect(compiled.textContent).toContain('Retained symbols');
    expect(compiled.textContent).toContain('Template bindings');
    expect(compiled.textContent).toContain('Entry symbols');
    expect(compiled.textContent).toContain('Downstream frontier');
    expect(compiled.textContent).toContain('BACKEND_OPERATION');
    expect(compiled.querySelector<HTMLTextAreaElement>('.source-output')?.value)
      .toContain('saveCustomer');
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

  it('should render Java method use-case context as flow tree', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);

    fixture.componentInstance.selectedToolKey.set('java-method-use-case-context');
    fixture.componentInstance.gitLabJavaMethodUseCaseContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildJavaMethodUseCaseContextResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Java Method Use Case Context');
    expect(compiled.textContent).toContain('CustomerService.java');
    expect(compiled.textContent).toContain('CustomerRepository.java');
    expect(compiled.textContent).toContain('ENTRY METHOD');
    expect(compiled.textContent).toContain('REPOSITORY CALL');
    expect(compiled.textContent).toContain('CustomerModel getCustomer(CustomerId customerId)');
  });

  it('should submit Java method use-case context payload', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    const httpTesting = TestBed.inject(HttpTestingController);

    fixture.componentInstance.selectedToolKey.set('java-method-use-case-context');
    fixture.componentInstance.scopeForm.patchValue({
      group: 'CRM',
      projectName: 'crm-customer-service',
      branch: 'main'
    });
    fixture.componentInstance.gitLabJavaMethodUseCaseContextForm.patchValue({
      filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
      className: 'com.example.crm.customer.application.CustomerService',
      methodName: 'getCustomer',
      lineNumber: '44',
      parameterCount: '1',
      parameterTypes: 'CustomerId',
      maxDepth: '4',
      maxResults: '12'
    });

    fixture.componentInstance.submit(new Event('submit'));

    const request = httpTesting.expectOne('/api/gitlab/repository/java-method-use-case-context');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      group: 'CRM',
      projectName: 'crm-customer-service',
      branch: 'main',
      filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
      className: 'com.example.crm.customer.application.CustomerService',
      methodName: 'getCustomer',
      lineNumber: 44,
      parameterCount: 1,
      parameterTypes: ['CustomerId'],
      maxDepth: 4,
      maxResults: 12
    });

    request.flush(buildJavaMethodUseCaseContextResponse());
    httpTesting.verify();

    expect(fixture.componentInstance.gitLabJavaMethodUseCaseContextState().status).toBe('success');
  });

  it('should render unresolved use-case references as collapsed details', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.selectedToolKey.set('endpoint-use-case-context');
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
    fixture.componentInstance.selectedToolKey.set('endpoint-inventory');
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

  it('should populate files-by-path form from endpoint use-case context', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.gitLabEndpointUseCaseContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildUseCaseContextResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    fixture.componentInstance.useUseCaseFilesForRead();
    fixture.detectChanges();

    const form = fixture.componentInstance.gitLabRepositoryFilesByPathForm;
    expect(form.controls.group.value).toBe('CRM');
    expect(form.controls.projectName.value).toBe('crm-customer-service');
    expect(form.controls.branch.value).toBe('main');
    expect(form.controls.filePaths.value).toContain(
      'src/main/java/com/example/crm/customer/api/CustomerController.java'
    );
    expect(form.controls.filePaths.value).toContain(
      'src/main/java/com/example/crm/customer/application/CustomerService.java'
    );
  });

  it('should render files-by-path response below endpoint use-case context', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.selectedToolKey.set('repository-files-by-path');
    fixture.componentInstance.gitLabRepositoryFilesByPathState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildFilesByPathResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Read Repository Files By Path');
    expect(compiled.textContent).toContain('1 pobranych');
    expect(compiled.textContent).toContain('CustomerController.java');
    expect(compiled.textContent).toContain('READ');
    expect(compiled.textContent).toContain('2026-06-14T10:20:00.000Z');
    expect(compiled.textContent).toContain('last-customer-controller');
  });

  it('should render Java method slice response with focused source content', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.selectedToolKey.set('java-method-slice');
    fixture.componentInstance.gitLabJavaMethodSliceState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildJavaMethodSliceResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Java Method Slice');
    expect(compiled.textContent).toContain('OK · 2 methods');
    expect(compiled.textContent).toContain('L42-L58');
    expect(compiled.textContent).toContain('CustomerService.java');
    expect(compiled.textContent).toContain('loadCustomer');

    const sourceOutput = compiled.querySelector<HTMLTextAreaElement>('.source-output--slice');
    expect(sourceOutput?.value).toContain('public CustomerModel getCustomer(CustomerId customerId)');
    expect(sourceOutput?.value).toContain('// ... omitted');
  });

  it('should render OpenAPI endpoint slice response with focused contract content', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.selectedToolKey.set('openapi-endpoint-slice');
    fixture.componentInstance.gitLabOpenApiEndpointSliceState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildOpenApiEndpointSliceResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('OpenAPI Endpoint Slice');
    expect(compiled.textContent).toContain('OK · POST /api/customers/{id}/cases');
    expect(compiled.textContent).toContain('openapi 3.0.3');
    expect(compiled.textContent).toContain('createCustomerCase');
    expect(compiled.textContent).toContain('Create CRM customer case');

    const sourceOutput = compiled.querySelector<HTMLTextAreaElement>('.source-output--slice');
    expect(sourceOutput?.value).toContain('"operationId" : "createCustomerCase"');
    expect(sourceOutput?.value).toContain('"CreateCustomerCaseRequest"');
  });

  it('should populate Java method slice form from selected use-case method', () => {
    const fixture = TestBed.createComponent(GitLabEvidenceConsoleComponent);
    fixture.componentInstance.gitLabEndpointUseCaseContextState.set({
      status: 'success',
      statusCode: 200,
      message: 'OK',
      response: buildUseCaseContextResponse(),
      responseJson: '{}'
    });
    fixture.detectChanges();

    const serviceNode = fixture.componentInstance.gitLabUseCaseTree()?.rows.find(
      (row) => row.node.label === 'CustomerService.java'
    )?.node;
    const method = serviceNode?.file?.methods?.[0];

    expect(serviceNode).toBeTruthy();
    expect(method).toBeTruthy();

    fixture.componentInstance.useUseCaseMethodForSlice(serviceNode!, method!);
    fixture.detectChanges();

    const form = fixture.componentInstance.gitLabJavaMethodSliceForm;
    expect(fixture.componentInstance.selectedToolKey()).toBe('java-method-slice');
    expect(form.controls.group.value).toBe('CRM');
    expect(form.controls.projectName.value).toBe('crm-customer-service');
    expect(form.controls.branch.value).toBe('main');
    expect(form.controls.filePath.value).toBe(
      'src/main/java/com/example/crm/customer/application/CustomerService.java'
    );
    expect(form.controls.methodSelectors.value).toBe('getCustomer');
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
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/api/CustomerController.java',
            methodName: 'getCustomer',
            lineStart: 10,
            lineEnd: 18
          }
        ],
        reason: 'Endpoint handler and local controller flow.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
        role: 'USE_CASE_SERVICE',
        priority: 2,
        symbols: ['getCustomer'],
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
            signature: 'CustomerModel getCustomer(CustomerId customerId)',
            methodName: 'getCustomer',
            lineStart: 42,
            lineEnd: 50
          }
        ],
        reason: 'Injected dependency used by traversed method.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/api/CustomerMapper.java',
        role: 'MAPPER',
        priority: 5,
        symbols: ['from'],
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/api/CustomerMapper.java',
            methodName: 'from',
            lineStart: 12,
            lineEnd: 14
          }
        ],
        reason: 'Mapper method used by service.',
        confidence: 'MEDIUM'
      },
      {
        path: 'src/main/java/com/example/crm/customer/adapter/out/CustomerRepository.java',
        role: 'REPOSITORY_IMPLEMENTATION',
        priority: 4,
        symbols: ['getCustomer'],
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/adapter/out/CustomerRepository.java',
            methodName: 'getCustomer',
            lineStart: 23,
            lineEnd: 31
          }
        ],
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

function buildJavaMethodUseCaseContextResponse(
  overrides: Partial<GitLabJavaMethodUseCaseContextResponse> = {}
): GitLabJavaMethodUseCaseContextResponse {
  return {
    repository: {
      group: 'CRM',
      projectName: 'crm-customer-service',
      branch: 'main'
    },
    entryMethod: {
      status: 'RESOLVED',
      requestedClassName: 'com.example.crm.customer.application.CustomerService',
      requestedMethodName: 'getCustomer',
      filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
      declaringTypeSimpleName: 'CustomerService',
      declaringTypeRelativeName: 'CustomerService',
      declaringTypeQualifiedName: 'com.example.crm.customer.application.CustomerService',
      declaringTypeKind: 'CLASS',
      methodName: 'getCustomer',
      signature: 'CustomerModel getCustomer(CustomerId customerId)',
      lineStart: 42,
      lineEnd: 50,
      parameterCount: 1,
      parameterTypes: ['CustomerId'],
      parameterNames: ['customerId'],
      returnType: 'CustomerModel',
      confidence: 'HIGH',
      candidates: [
        {
          filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
          declaringTypeSimpleName: 'CustomerService',
          declaringTypeRelativeName: 'CustomerService',
          declaringTypeQualifiedName: 'com.example.crm.customer.application.CustomerService',
          declaringTypeKind: 'CLASS',
          methodName: 'getCustomer',
          signature: 'CustomerModel getCustomer(CustomerId customerId)',
          lineStart: 42,
          lineEnd: 50,
          parameterCount: 1,
          parameterTypes: ['CustomerId'],
          parameterNames: ['customerId'],
          returnType: 'CustomerModel',
          confidence: 'HIGH',
          reason: 'Resolved entry method.'
        }
      ],
      limitations: []
    },
    files: [
      {
        path: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
        role: 'USE_CASE_SERVICE',
        priority: 1,
        symbols: ['getCustomer'],
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
            signature: 'CustomerModel getCustomer(CustomerId customerId)',
            methodName: 'getCustomer',
            lineStart: 42,
            lineEnd: 50
          }
        ],
        reason: 'Entry method selected by class and method.',
        confidence: 'HIGH'
      },
      {
        path: 'src/main/java/com/example/crm/customer/adapter/out/CustomerRepository.java',
        role: 'REPOSITORY_IMPLEMENTATION',
        priority: 3,
        symbols: ['findCustomer'],
        methods: [
          {
            filePath: 'src/main/java/com/example/crm/customer/adapter/out/CustomerRepository.java',
            methodName: 'findCustomer',
            lineStart: 18,
            lineEnd: 26
          }
        ],
        reason: 'Repository dependency used by entry method.',
        confidence: 'HIGH'
      }
    ],
    relations: [
      {
        from: 'com.example.crm.customer.application.CustomerService#getCustomer',
        to: 'com.example.crm.customer.adapter.out.CustomerRepository#findCustomer',
        kind: 'REPOSITORY_CALL',
        confidence: 'HIGH',
        reason: 'Entry method calls repository dependency.'
      }
    ],
    unresolved: [],
    limitations: [],
    suggestedNextReads: [],
    limits: {
      maxDepth: 4,
      maxResults: 12,
      maxReadFiles: 80,
      maxDepthReached: false,
      maxResultsReached: false,
      readFileCount: 2,
      readFileLimitReached: false
    },
    confidence: 'HIGH',
    ...overrides
  };
}

function buildFilesByPathResponse(): GitLabRepositoryFilesByPathResponse {
  return {
    group: 'CRM',
    projectName: 'crm-customer-service',
    branch: 'main',
    requestedFileCount: 1,
    processedFileCount: 1,
    returnedFileCount: 1,
    failedFileCount: 0,
    totalReturnedCharacters: 27,
    fileCountTruncated: false,
    totalCharacterLimitReached: false,
    files: [
      {
        group: 'CRM',
        projectName: 'crm-customer-service',
        branch: 'main',
        filePath: 'src/main/java/com/example/crm/customer/api/CustomerController.java',
        content: 'class CustomerController {}',
        truncated: false,
        inferredRole: 'entrypoint',
        returnedCharacters: 27,
        sizeBytes: 128,
        contentSha256: 'sha-customer-controller',
        blobId: 'blob-customer-controller',
        commitId: 'branch-tip-customer-controller',
        lastCommitId: 'last-customer-controller',
        lastModifiedAt: '2026-06-14T10:20:00.000Z',
        metadataStatus: 'RESOLVED',
        metadataError: null,
        error: null
      }
    ]
  };
}

function buildJavaMethodSliceResponse(): GitLabJavaMethodSliceResponse {
  return {
    group: 'CRM',
    projectName: 'crm-customer-service',
    branch: 'main',
    filePath: 'src/main/java/com/example/crm/customer/application/CustomerService.java',
    status: 'OK',
    declaringTypeName: 'CustomerService',
    requestedMethods: [{ methodName: 'getCustomer' }],
    returnedLineStart: 42,
    returnedLineEnd: 58,
    totalLines: 120,
    content: [
      'package com.example.crm.customer.application;',
      '',
      'import com.example.crm.customer.api.CustomerModel;',
      '',
      'class CustomerService {',
      '  private final CustomerRepository customerRepository;',
      '',
      '  public CustomerModel getCustomer(CustomerId customerId) {',
      '    return loadCustomer(customerId);',
      '  }',
      '',
      '  private CustomerModel loadCustomer(CustomerId customerId) {',
      '    return customerRepository.getCustomer(customerId);',
      '  }',
      '',
      '  // ... omitted 3 methods',
      '}'
    ].join('\n'),
    returnedCharacters: 391,
    truncated: false,
    includedImports: ['import com.example.crm.customer.api.CustomerModel;'],
    includedFields: ['customerRepository'],
    includedMethods: [
      {
        declaringTypeName: 'CustomerService',
        methodName: 'getCustomer',
        signature: 'public CustomerModel getCustomer(CustomerId customerId)',
        lineStart: 42,
        lineEnd: 46,
        parameterCount: 1,
        parameterTypes: ['CustomerId']
      },
      {
        declaringTypeName: 'CustomerService',
        methodName: 'loadCustomer',
        signature: 'private CustomerModel loadCustomer(CustomerId customerId)',
        lineStart: 48,
        lineEnd: 58,
        parameterCount: 1,
        parameterTypes: ['CustomerId']
      }
    ],
    omittedFieldCount: 0,
    omittedMethodCount: 3,
    candidates: [],
    limitations: []
  };
}

function buildOpenApiEndpointSliceResponse(): GitLabOpenApiEndpointSliceResponse {
  return {
    group: 'CRM',
    projectName: 'crm-customer-service',
    branch: 'main',
    filePath: 'src/main/resources/openapi/customer-api.yml',
    status: 'OK',
    specType: 'openapi',
    specVersion: '3.0.3',
    httpMethod: 'POST',
    endpointPath: '/api/customers/{customerId}/cases',
    matchedPath: '/api/customers/{id}/cases',
    operationId: 'createCustomerCase',
    summary: 'Create CRM customer case',
    description: 'Creates a case for an existing CRM customer.',
    tags: ['Customer Cases'],
    sourceRef:
      'crm-customer-service:src/main/resources/openapi/customer-api.yml#POST /api/customers/{id}/cases',
    content: [
      '# OpenAPI endpoint contract',
      '',
      '```json',
      '{',
      '  "paths" : {',
      '    "/api/customers/{id}/cases" : {',
      '      "post" : {',
      '        "operationId" : "createCustomerCase"',
      '      }',
      '    }',
      '  },',
      '  "referencedComponents" : {',
      '    "schemas" : {',
      '      "CreateCustomerCaseRequest" : {',
      '        "type" : "object"',
      '      }',
      '    }',
      '  }',
      '}',
      '```'
    ].join('\n'),
    returnedCharacters: 420,
    truncated: false,
    limitations: []
  };
}

function buildFrontendCatalogResponse(): GitLabFrontendCatalogResponse {
  return {
    scope: {
      group: 'CRM/apps',
      projectName: 'crm-agent-portal',
      ref: 'release/2026.08',
      pathPrefixes: ['apps/crm-agent']
    },
    sourceRevision: {
      ref: 'release/2026.08',
      commitId: 'crm-ui-revision-20260815'
    },
    workspaceSignals: [
      { kind: 'FRAMEWORK', value: 'Angular 20', sourcePath: 'apps/crm-agent/project.json' }
    ],
    nodes: [
      {
        nodeId: 'route-crm-customer-profile',
        parentNodeId: null,
        screen: {
          screenId: 'screen-crm-customer-profile',
          routeNodeId: 'route-crm-customer-profile',
          routePattern: '/crm/customers/:customerId',
          outlet: 'primary',
          viewTarget: {
            symbol: 'CrmCustomerProfileComponent',
            sourcePath: 'apps/crm-agent/src/app/customer/customer-profile.ts'
          }
        },
        label: 'Customer profile',
        pathSegment: 'crm/customers/:customerId',
        routePattern: '/crm/customers/:customerId',
        outlet: 'primary',
        kind: 'SCREEN',
        status: 'RESOLVED',
        lazyBoundary: true,
        routeParameters: ['customerId'],
        redirectTarget: null,
        viewTarget: {
          symbol: 'CrmCustomerProfileComponent',
          sourcePath: 'apps/crm-agent/src/app/customer/customer-profile.ts'
        },
        lazyTarget: null,
        configuration: [],
        routeSource: {
          path: 'apps/crm-agent/src/app/app.routes.ts',
          symbol: 'crmCustomerRoutes',
          startLine: 12,
          endLine: 22
        },
        limitations: []
      }
    ],
    diagnostics: [],
    coverage: {
      status: 'READY',
      visitedRouteNodeCount: 1,
      visitedRouteFileCount: 1,
      sourceReadCount: 7,
      aliasResolutionCount: 2,
      unresolvedEdgeCount: 0,
      limitReached: false,
      limitations: []
    }
  };
}

function buildFrontendScreenContextResponse(): GitLabFrontendScreenContextResponse {
  return {
    scope: buildFrontendCatalogResponse().scope,
    sourceRevision: buildFrontendCatalogResponse().sourceRevision,
    screenNode: buildFrontendCatalogResponse().nodes[0],
    graphCoverage: buildFrontendCatalogResponse().coverage,
    sourceFiles: [
      {
        path: 'apps/crm-agent/src/app/customer/customer-profile.ts',
        roles: ['VIEW_COMPONENT', 'FORM_LOGIC'],
        content: 'export class CrmCustomerProfileComponent {}',
        returnedCharacters: 48,
        truncated: false
      }
    ],
    technicalSignals: [
      {
        kind: 'REACTIVE_FORM',
        description: 'CRM customer profile form is declared in the component.',
        confidence: 'HIGH',
        source: {
          path: 'apps/crm-agent/src/app/customer/customer-profile.ts',
          symbol: 'CrmCustomerProfileComponent',
          startLine: 10,
          endLine: 80
        }
      }
    ],
    coverage: [
      { category: 'FORMS', status: 'READY', detail: 'Reactive form source included.' }
    ],
    diagnostics: [],
    totalReturnedCharacters: 48,
    contextLimitReached: false
  };
}

function buildFrontendScreenReachabilityResponse(): GitLabFrontendScreenReachabilityResponse {
  const screenNode = buildFrontendCatalogResponse().nodes[0];
  return {
    scope: buildFrontendCatalogResponse().scope,
    sourceRevision: buildFrontendCatalogResponse().sourceRevision,
    status: 'OK',
    screenNode,
    effectiveRouteChain: {
      screen: screenNode.screen!,
      segments: [
        {
          nodeId: screenNode.nodeId,
          pathSegment: screenNode.pathSegment,
          routePattern: screenNode.routePattern,
          outlet: screenNode.outlet,
          configuration: screenNode.configuration,
          source: screenNode.routeSource
        }
      ],
      routeParameters: screenNode.routeParameters
    },
    componentLevels: [
      {
        depth: 0,
        components: [
          {
            componentId: 'component:CrmCustomerProfileComponent',
            breadthFirstOrder: 0,
            depth: 0,
            connectedToSelectedScreen: true,
            discoveryKind: 'ROUTE_VIEW',
            symbol: 'CrmCustomerProfileComponent',
            selector: 'crm-customer-profile',
            sourcePath: 'apps/crm-agent/src/app/customer/customer-profile.ts',
            templatePath: 'apps/crm-agent/src/app/customer/customer-profile.html',
            status: 'OK',
            templateBindings: [],
            entrySymbols: [],
            includedSymbols: [],
            dependencyIds: ['dependency:CrmCustomerFacade'],
            childComponentIds: [],
            sliceContent: 'export class CrmCustomerProfileComponent {}',
            sourceCharacters: 1200,
            returnedCharacters: 48,
            truncated: false,
            limitations: []
          }
        ]
      }
    ],
    dependencies: [
      {
        dependencyId: 'dependency:CrmCustomerFacade',
        discoveryOrder: 0,
        kind: 'FACADE',
        category: 'FUNCTIONAL',
        symbol: 'CrmCustomerFacade',
        sourcePath: 'apps/crm-agent/src/app/customer/customer.facade.ts',
        moduleSpecifier: './customer.facade',
        status: 'OK',
        methods: ['saveCustomer'],
        usedBy: ['component:CrmCustomerProfileComponent'],
        downstreamDependencyIds: [],
        sliceContent: 'saveCustomer(): void {}',
        sourceCharacters: 900,
        returnedCharacters: 24,
        truncated: false,
        limitations: []
      }
    ],
    edges: [
      {
        fromId: 'component:CrmCustomerProfileComponent',
        toId: 'dependency:CrmCustomerFacade',
        kind: 'USES_DEPENDENCY',
        label: 'saveCustomer'
      }
    ],
    technicalSignals: [],
    diagnostics: [],
    sourceFileCount: 3,
    sourceCharacters: 2100,
    sliceCharacters: 72,
    outlineCharacters: 240,
    contextLimitReached: false,
    limitations: [],
    readableOutline: [
      '## Effective route chain',
      '/crm/customers/:customerId',
      '### Depth 0',
      'CrmCustomerProfileComponent',
      '## Functional and supporting dependencies',
      'CrmCustomerFacade.saveCustomer'
    ].join('\n')
  };
}

function buildAngularRouteBranchSliceResponse(): GitLabAngularRouteBranchSliceResponse {
  return {
    scope: buildFrontendCatalogResponse().scope,
    sourceRevision: buildFrontendCatalogResponse().sourceRevision,
    status: 'OK',
    screenNode: buildFrontendCatalogResponse().nodes[0],
    files: [
      {
        path: 'apps/crm-agent/src/app/app.routes.ts',
        content: [
          "import { CrmCustomerProfileComponent } from './customer/customer-profile';",
          '// ... 4 unrelated imports omitted ...',
          "{ path: 'crm/customers/:customerId', component: CrmCustomerProfileComponent,",
          '  children: [{ /* ... 1 sibling route entry omitted ... */ }]',
          '}'
        ].join('\n'),
        sourceCharacters: 8000,
        returnedCharacters: 800,
        includedRouteNodeIds: ['route-crm-customer-profile'],
        includedImports: [
          "import { CrmCustomerProfileComponent } from './customer/customer-profile';"
        ],
        includedLocalDeclarations: [],
        unresolvedSymbols: [],
        omittedImportCount: 4,
        omittedSiblingRouteCount: 3,
        truncated: false
      }
    ],
    childRoutes: [
      {
        sliceRef: 'angular-route:screen-crm-customer-history',
        nodeId: 'route-crm-customer-history',
        screenId: 'screen-crm-customer-history',
        routePattern: '/crm/customers/:customerId/history',
        label: 'Customer history',
        kind: 'SCREEN',
        status: 'RESOLVED',
        viewTarget: {
          symbol: 'CrmCustomerHistoryComponent',
          sourcePath: 'apps/crm-agent/src/app/customer/customer-history.ts'
        },
        redirectTarget: null,
        structural: false,
        samePathAsParent: false,
        hasChildren: false
      }
    ],
    sourceCharacters: 8000,
    returnedCharacters: 800,
    savedCharacters: 7200,
    omittedImportCount: 4,
    omittedSiblingRouteCount: 3,
    truncated: false,
    limitations: [],
    diagnostics: []
  };
}

function buildTypeScriptSymbolSliceResponse(): GitLabTypeScriptSymbolSliceResponse {
  return {
    scope: buildFrontendCatalogResponse().scope,
    filePath: 'apps/crm-agent/src/app/customer/customer-profile.ts',
    status: 'OK',
    declaringTypeName: 'CrmCustomerProfileComponent',
    lineStart: 80,
    lineEnd: 112,
    totalLines: 320,
    sourceCharacters: 18000,
    templatePath: 'apps/crm-agent/src/app/customer/customer-profile.component.html',
    templateCharacters: 420,
    templateBindings: [
      {
        kind: 'EVENT',
        target: 'click',
        expression: 'saveCustomer()',
        referencedSymbols: ['saveCustomer'],
        lineStart: 12
      }
    ],
    content: [
      "import { CrmCustomerApi } from './crm-customer.api';",
      '// ... 8 unrelated imports omitted ...',
      'export class CrmCustomerProfileComponent {',
      '  saveCustomer(): void {',
      '    this.customerApi.updateCustomer(this.customerForm.getRawValue());',
      '  }',
      '}'
    ].join('\n'),
    returnedCharacters: 1800,
    savedCharacters: 16200,
    truncated: false,
    includedImports: ["import { CrmCustomerApi } from './crm-customer.api';"],
    includedFields: ['customerApi', 'customerForm'],
    entrySymbols: [
      {
        declaringTypeName: 'CrmCustomerProfileComponent',
        symbolName: 'saveCustomer',
        kind: 'METHOD',
        signature: 'saveCustomer(): void',
        lineStart: 80,
        lineEnd: 88
      }
    ],
    includedSymbols: [
      {
        declaringTypeName: 'CrmCustomerProfileComponent',
        symbolName: 'saveCustomer',
        kind: 'METHOD',
        signature: 'saveCustomer(): void',
        lineStart: 80,
        lineEnd: 88
      },
      {
        declaringTypeName: 'CrmCustomerProfileComponent',
        symbolName: 'customerEffect',
        kind: 'PROPERTY',
        signature: 'readonly customerEffect = effect(...)',
        lineStart: 104,
        lineEnd: 112
      }
    ],
    omittedImportCount: 8,
    omittedFieldCount: 11,
    omittedSymbolCount: 22,
    downstreamReferences: [
      {
        kind: 'BACKEND_OPERATION',
        sourceSymbol: 'saveCustomer',
        ownerSymbol: 'customerApi',
        memberSymbol: 'updateCustomer',
        targetSymbol: 'CrmCustomerApi',
        moduleSpecifier: './crm-customer.api'
      }
    ],
    candidates: [],
    limitations: []
  };
}

