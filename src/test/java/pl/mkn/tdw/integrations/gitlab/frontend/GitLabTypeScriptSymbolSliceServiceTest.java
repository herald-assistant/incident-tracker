package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabTypeScriptSymbolSliceServiceTest {

    @Mock
    private GitLabRepositoryPort repositoryPort;

    @Test
    void shouldReturnReachableSyntheticCrmSymbolsWithoutUnrelatedComponentNoise() {
        var source = """
                import { Component, inject } from '@angular/core';
                import { FormBuilder } from '@angular/forms';
                import { CrmCustomerApi } from './crm-customer.api';
                import { CrmTelemetry } from './crm-telemetry';
                import { UnrelatedDashboardWidget } from '../dashboard/unrelated-dashboard-widget';

                @Component({ selector: 'crm-customer-editor', template: '' })
                export class CrmCustomerEditorComponent {
                  private readonly customerApi = inject(CrmCustomerApi);
                  private readonly formBuilder = inject(FormBuilder);
                  private readonly telemetry = inject(CrmTelemetry);
                  private readonly unrelatedWidget = inject(UnrelatedDashboardWidget);
                  readonly customerForm = this.formBuilder.group({ displayName: [''] });

                  saveCustomer(): void {
                    if (this.customerForm.invalid) {
                      return;
                    }
                    this.persistCustomer();
                    this.customerApi.updateCustomer(this.customerForm.getRawValue());
                  }

                  private persistCustomer(): void {
                    this.telemetry.recordBusinessAction('synthetic-crm-customer-save');
                  }

                  private calculateUnrelatedDashboardNoise(): number {
                    this.unrelatedWidget.refresh();
                    return 42;
                  }
                }
                """;
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var filePath = "apps/crm-agent/src/app/customer/crm-customer-editor.component.ts";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmCustomerEditorComponent",
                        null, false,
                        List.of(new GitLabTypeScriptSymbolSelector(
                                "saveCustomer", GitLabTypeScriptSymbolKind.METHOD, null
                        )),
                        true, true, true, 12_000
                )
        );

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.content())
                .contains("saveCustomer", "persistCustomer", "customerApi", "customerForm")
                .contains("unrelated imports omitted", "unrelated symbols omitted")
                .doesNotContain("calculateUnrelatedDashboardNoise", "UnrelatedDashboardWidget");
        assertThat(response.includedSymbols()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .containsExactly("saveCustomer", "persistCustomer");
        assertThat(response.downstreamReferences())
                .anySatisfy(reference -> {
                    assertThat(reference.kind()).isEqualTo(GitLabTypeScriptDownstreamReferenceKind.BACKEND_OPERATION);
                    assertThat(reference.ownerSymbol()).isEqualTo("customerApi");
                    assertThat(reference.memberSymbol()).isEqualTo("updateCustomer");
                    assertThat(reference.targetSymbol()).isEqualTo("CrmCustomerApi");
                    assertThat(reference.moduleSpecifier()).isEqualTo("./crm-customer.api");
                });
        assertThat(response.returnedCharacters()).isLessThan(response.sourceCharacters());
        assertThat(response.savedCharacters()).isPositive();
    }

    @Test
    void shouldBuildTemplateDrivenSyntheticCrmReachabilityForFormsRxjsNgrxAndBackendOperations() {
        var source = """
                import { Component, Input, inject } from '@angular/core';
                import { FormBuilder, Validators } from '@angular/forms';
                import { Store } from '@ngrx/store';
                import { combineLatest, map } from 'rxjs';
                import { CrmCustomerApi } from './crm-customer.api';
                import { CrmCustomerFacade } from './crm-customer.facade';
                import * as CrmCustomerActions from './state/crm-customer.actions';
                import { CrmUnrelatedAuditApi } from './crm-unrelated-audit.api';

                function normalizeCrmName(value: unknown): string {
                  return String(value ?? '').trim();
                }

                @Component({
                  selector: 'crm-customer-editor',
                  templateUrl: './crm-customer-editor.component.html'
                })
                export class CrmCustomerEditorComponent {
                  private readonly customerApi = inject(CrmCustomerApi);
                  private readonly store = inject(Store);
                  private readonly formBuilder = inject(FormBuilder);
                  private readonly customerFacade = inject(CrmCustomerFacade);
                  private readonly unrelatedAuditApi = inject(CrmUnrelatedAuditApi);
                  readonly customerForm = this.formBuilder.group({
                    displayName: ['', [Validators.required]]
                  });
                  readonly customerView$ = combineLatest([
                    this.customerFacade.customer$,
                    this.customerForm.valueChanges
                  ]).pipe(map(([customer, form]) => this.mapCustomer(customer, form)));
                  @Input() readonly saving = false;

                  ngOnInit(): void {
                    this.customerForm.controls.displayName.valueChanges.subscribe(() => this.refreshEligibility());
                  }

                  saveCustomer(): void {
                    this.store.dispatch(CrmCustomerActions.saveRequested({
                      customer: this.customerForm.getRawValue()
                    }));
                    this.customerApi.updateCustomer(this.customerForm.getRawValue());
                  }

                  private refreshEligibility(): void {
                    this.customerForm.controls.displayName.setValidators([Validators.required]);
                  }

                  private mapCustomer(customer: unknown, form: unknown): unknown {
                    return { customer, form, label: normalizeCrmName(customer) };
                  }

                  private runUnrelatedAudit(): void {
                    this.unrelatedAuditApi.publishAudit();
                  }
                }
                """;
        var template = """
                <form [formGroup]="customerForm" (ngSubmit)="saveCustomer()">
                  <input formControlName="displayName" />
                  @if (customerView$ | async; as view) {
                    <span>{{ view.displayName }}</span>
                  }
                  <button [disabled]="customerForm.invalid || saving">Save</button>
                </form>
                """;
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var filePath = "apps/crm-agent/src/app/customer/crm-customer-editor.component.ts";
        var templatePath = "apps/crm-agent/src/app/customer/crm-customer-editor.component.html";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), templatePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), templatePath, template, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmCustomerEditorComponent", null, true, List.of(),
                        true, true, true, 20_000
                )
        );

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.templatePath()).isEqualTo(templatePath);
        assertThat(response.templateBindings())
                .extracting(GitLabTypeScriptTemplateBinding::kind)
                .contains(
                        GitLabTypeScriptTemplateBindingKind.PROPERTY,
                        GitLabTypeScriptTemplateBindingKind.EVENT,
                        GitLabTypeScriptTemplateBindingKind.FORM_CONTROL,
                        GitLabTypeScriptTemplateBindingKind.CONTROL_FLOW,
                        GitLabTypeScriptTemplateBindingKind.INTERPOLATION
                );
        assertThat(response.templateBindings().stream()
                .flatMap(binding -> binding.referencedSymbols().stream()).toList())
                .contains("customerForm", "customerView$", "saving", "saveCustomer");
        assertThat(response.candidates()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .contains("customerForm", "customerView$", "saving");
        assertThat(response.entrySymbols()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .contains("customerForm", "customerView$", "saving", "saveCustomer", "ngOnInit");
        assertThat(response.includedSymbols()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .contains("refreshEligibility", "mapCustomer", "normalizeCrmName")
                .doesNotContain("runUnrelatedAudit");
        assertThat(response.includedFields())
                .contains("customerApi", "store", "formBuilder", "customerFacade", "customerForm", "customerView$")
                .doesNotContain("unrelatedAuditApi");
        assertThat(response.content())
                .contains("Validators.required", "valueChanges", "CrmCustomerActions.saveRequested", "updateCustomer",
                        "function normalizeCrmName")
                .doesNotContain("runUnrelatedAudit", "publishAudit", "CrmUnrelatedAuditApi");
        assertThat(response.downstreamReferences()).extracting(GitLabTypeScriptDownstreamReference::kind)
                .contains(
                        GitLabTypeScriptDownstreamReferenceKind.BACKEND_OPERATION,
                        GitLabTypeScriptDownstreamReferenceKind.NGRX_DISPATCH,
                        GitLabTypeScriptDownstreamReferenceKind.NGRX_ACTION,
                        GitLabTypeScriptDownstreamReferenceKind.RXJS_PIPELINE,
                        GitLabTypeScriptDownstreamReferenceKind.PROPERTY_ACCESS
                );
        assertThat(response.returnedCharacters()).isLessThan(response.sourceCharacters());
    }

    @Test
    void shouldExposeSyntheticCrmCandidatesWhenSelectorDoesNotMatch() {
        var source = """
                export class CrmContactHistoryFacade {
                  refreshHistory(): void {}
                  downloadHistory(): void {}
                }
                """;
        var scope = new GitLabFrontendRepositoryScope("synthetic-crm", "crm-agent-portal", "main", List.of());
        var filePath = "libs/crm-contact/history/crm-contact-history.facade.ts";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmContactHistoryFacade",
                        null, false,
                        List.of(new GitLabTypeScriptSymbolSelector("missingAction", null, null)),
                        true, true, true, null
                )
        );

        assertThat(response.status()).isEqualTo("NOT_FOUND");
        assertThat(response.candidates()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .containsExactly("refreshHistory", "downloadHistory");
    }

    @Test
    void shouldReportPartialSyntheticCrmSliceWhenOneRequestedMethodIsMissing() {
        var source = """
                export class CrmContactHistoryFacade {
                  refreshHistory(): void {}
                  downloadHistory(): void {}
                }
                """;
        var scope = new GitLabFrontendRepositoryScope("synthetic-crm", "crm-agent-portal", "main", List.of());
        var filePath = "libs/crm-contact/history/crm-contact-history.facade.ts";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmContactHistoryFacade", null, false,
                        List.of(
                                new GitLabTypeScriptSymbolSelector("refreshHistory", null, null),
                                new GitLabTypeScriptSymbolSelector("missingBusinessAction", null, null)
                        ),
                        true, true, true, null
                )
        );

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.content()).contains("refreshHistory");
        assertThat(response.limitations()).contains(
                "No symbol matched selector AUTO:missingBusinessAction."
        );
    }

    @Test
    void shouldUseSyntheticCrmInlineTemplateAsAReachabilityRoot() {
        var source = """
                @Component({
                  selector: 'crm-contact-summary',
                  template: `<button (click)="refreshContact()">Refresh</button>`
                })
                export class CrmContactSummaryComponent {
                  refreshContact(): void {
                    this.loadContact();
                  }

                  private loadContact(): void {}
                  private unrelatedContactExport(): void {}
                }
                """;
        var scope = new GitLabFrontendRepositoryScope("synthetic-crm", "crm-agent-portal", "main", List.of());
        var filePath = "libs/crm-contact/summary/crm-contact-summary.component.ts";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmContactSummaryComponent", null, true, List.of(),
                        true, true, true, null
                )
        );

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.templatePath()).isNull();
        assertThat(response.templateCharacters()).isPositive();
        assertThat(response.entrySymbols()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .containsExactly("refreshContact");
        assertThat(response.includedSymbols()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .containsExactly("refreshContact", "loadContact");
        assertThat(response.content()).doesNotContain("unrelatedContactExport");
    }

    @Test
    void shouldTreatStaticSyntheticCrmPresentationAndTemplateLocalsAsComplete() {
        var source = """
                @Component({
                  selector: 'crm-contact-status',
                  templateUrl: 'crm-contact-status.component.html'
                })
                export class CrmContactStatusComponent {}
                """;
        var template = """
                <ng-template let-data>
                  <span>{{ data.label | crmDisplayValue }}</span>
                </ng-template>
                """;
        var scope = new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
        var filePath = "apps/crm-agent/src/app/contact/crm-contact-status.component.ts";
        var templatePath = "apps/crm-agent/src/app/contact/crm-contact-status.component.html";
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), filePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), filePath, source, false
                ));
        when(repositoryPort.readFile(scope.group(), scope.projectName(), scope.ref(), templatePath, 200_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        scope.group(), scope.projectName(), scope.ref(), templatePath, template, false
                ));

        var response = new GitLabTypeScriptSymbolSliceService(repositoryPort).readSymbolSlice(
                new GitLabTypeScriptSymbolSliceRequest(
                        scope, filePath, "CrmContactStatusComponent", null, true, List.of(),
                        true, true, true, GitLabTypeScriptSymbolSliceService.MAX_OUTPUT_CHARACTERS
                )
        );

        assertThat(response.status()).isEqualTo("STATIC_PRESENTATIONAL");
        assertThat(response.templatePath()).isEqualTo(templatePath);
        assertThat(response.templateBindings().stream()
                .flatMap(binding -> binding.referencedSymbols().stream()).toList())
                .doesNotContain("data", "crmDisplayValue");
        assertThat(response.limitations()).isEmpty();
    }
}
