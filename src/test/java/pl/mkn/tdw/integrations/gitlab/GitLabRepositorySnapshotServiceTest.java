package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositorySnapshotService;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryTreeService;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreeNode;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitLabRepositorySnapshotServiceTest {
    @Test void readsAllHttpPagesAndRejectsRepeatedCursor() {
        var settings=mock(GitLabSettingsPort.class); when(settings.getBaseUrl()).thenReturn("https://gitlab.example.com");
        var builder=org.springframework.web.client.RestClient.builder();
        var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var factory=mock(pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory.class); when(factory.create()).thenReturn(builder.build());
        var service=new GitLabRepositorySnapshotService(settings,new GitLabRepositoryTreeService(factory));
        var sha="a".repeat(40);
        var url="https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/tree?recursive=true&per_page=100&ref="+sha+"&page=";
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(url+"1"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("[{\"path\":\"one.xml\",\"type\":\"blob\",\"id\":\""+sha+"\"}]",org.springframework.http.MediaType.APPLICATION_JSON).header("X-Next-Page","2"));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(url+"2"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("[{\"path\":\"two.xml\",\"type\":\"blob\",\"id\":\""+sha+"\"}]",org.springframework.http.MediaType.APPLICATION_JSON));
        assertEquals(2,service.snapshot("CRM","customer",sha).blobs().size()); server.verify(); server.reset();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(url+"1"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("[]",org.springframework.http.MediaType.APPLICATION_JSON).header("X-Next-Page","1"));
        assertThrows(IllegalArgumentException.class,()->service.snapshot("CRM","customer",sha)); server.verify();
    }
    @Test void completeSnapshotRetainsBlobIdsAndRejectsMissingAndDuplicateIds() {
        var settings=mock(GitLabSettingsPort.class); when(settings.getBaseUrl()).thenReturn("https://gitlab.example.com");
        var tree=mock(GitLabRepositoryTreeService.class); var sha="a".repeat(40);
        var service=new GitLabRepositorySnapshotService(settings,tree);
        when(tree.fetchRepositoryBlobs(anyString(),eq("CRM/customer"),eq(sha),eq(""),any()))
                .thenReturn(List.of(new GitLabRepositoryTreeNode("db/root.xml","blob","b".repeat(40))));
        assertEquals("b".repeat(40),service.snapshot("CRM","customer",sha).blobs().get("db/root.xml"));
        when(tree.fetchRepositoryBlobs(anyString(),anyString(),anyString(),anyString(),any()))
                .thenReturn(List.of(new GitLabRepositoryTreeNode("db/root.xml","blob",null)));
        assertThrows(IllegalStateException.class,()->service.snapshot("CRM","customer",sha));
        assertThrows(IllegalArgumentException.class,()->service.snapshot("CRM","customer","main"));
    }
}

