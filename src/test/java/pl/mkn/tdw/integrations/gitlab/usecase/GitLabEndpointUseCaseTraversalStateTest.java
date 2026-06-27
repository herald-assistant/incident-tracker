package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitLabEndpointUseCaseTraversalStateTest {

    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main"
    );

    @Test
    void shouldPreferInjectedImplementationBeforeMapperNode() {
        var state = new GitLabEndpointUseCaseTraversalState(repository, null, 5, 20);

        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                "src/main/java/com/example/CustomerMapper.java",
                "CustomerMapper",
                "from",
                1,
                1,
                GitLabEndpointUseCaseFileRole.MAPPER,
                GitLabEndpointUseCaseConfidence.HIGH,
                "Mapper method called from traversed flow."
        ));
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                "src/main/java/com/example/UpdateCustomerService.java",
                "UpdateCustomerService",
                "update",
                1,
                1,
                GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                GitLabEndpointUseCaseConfidence.HIGH,
                "Implementation method for injected interface call."
        ));

        assertEquals("UpdateCustomerService", state.poll().typeName());
        assertEquals("CustomerMapper", state.poll().typeName());
    }

    @Test
    void shouldKeepInsertionOrderForNodesWithSamePriority() {
        var state = new GitLabEndpointUseCaseTraversalState(repository, null, 5, 20);

        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                "src/main/java/com/example/FirstHelper.java",
                "FirstHelper",
                "apply",
                0,
                1,
                GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                "Domain method called from traversed flow."
        ));
        state.enqueue(new GitLabEndpointUseCaseTraversalNode(
                "src/main/java/com/example/SecondHelper.java",
                "SecondHelper",
                "apply",
                0,
                1,
                GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                GitLabEndpointUseCaseConfidence.MEDIUM,
                "Domain method called from traversed flow."
        ));

        assertEquals("FirstHelper", state.poll().typeName());
        assertEquals("SecondHelper", state.poll().typeName());
    }
}
