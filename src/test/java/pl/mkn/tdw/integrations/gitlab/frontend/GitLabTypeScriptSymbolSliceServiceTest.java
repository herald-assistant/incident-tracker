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
                    assertThat(reference.ownerSymbol()).isEqualTo("customerApi");
                    assertThat(reference.memberSymbol()).isEqualTo("updateCustomer");
                    assertThat(reference.targetSymbol()).isEqualTo("CrmCustomerApi");
                    assertThat(reference.moduleSpecifier()).isEqualTo("./crm-customer.api");
                });
        assertThat(response.returnedCharacters()).isLessThan(response.sourceCharacters());
        assertThat(response.savedCharacters()).isPositive();
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
                        List.of(new GitLabTypeScriptSymbolSelector("missingAction", null, null)),
                        true, true, true, null
                )
        );

        assertThat(response.status()).isEqualTo("NOT_FOUND");
        assertThat(response.candidates()).extracting(GitLabTypeScriptSymbolCandidate::symbolName)
                .containsExactly("refreshHistory", "downloadHistory");
    }
}
