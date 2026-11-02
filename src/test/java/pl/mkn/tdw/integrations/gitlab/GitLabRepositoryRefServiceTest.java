package pl.mkn.tdw.integrations.gitlab;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryRefService;
import pl.mkn.tdw.integrations.gitlab.config.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
class GitLabRepositoryRefServiceTest {
 @Test void readsHeadCommitDatesWithoutExtraRequestsAndToleratesMissingOrInvalidDates() {
  var props=new GitLabProperties();props.setBaseUrl("https://gitlab.example.com");
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var service=new GitLabRepositoryRefService(props,new GitLabRestClientFactory(props,IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
  server.expect(requestTo("https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/branches?per_page=100&page=1"))
   .andRespond(withSuccess("""
    [{"name":"main","default":true,"commit":{"committed_date":"2026-11-02T14:35:00+01:00"}},
     {"name":"release/next","commit":{"committed_date":"invalid"}},
     {"name":"feature/customer-labels"}]
    """,MediaType.APPLICATION_JSON));
  var page=service.listRefs("CRM/customer",GitLabRef.Kind.BRANCH,"",1);
  assertEquals("2026-11-02T13:35:00Z",page.refs().get(0).committedAt());
  assertTrue(page.refs().get(0).isDefault());
  assertNull(page.refs().get(1).committedAt());assertNull(page.refs().get(2).committedAt());
  server.verify();
 }
 @Test void readsAnotherPageWithEncodedProjectAndTags() {
  var props=new GitLabProperties(); props.setBaseUrl("https://gitlab.example.com");
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var service=new GitLabRepositoryRefService(props,new GitLabRestClientFactory(props,IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
  server.expect(requestTo("https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/tags?per_page=100&page=2&search=release%2Fnext"))
   .andRespond(withSuccess("[{\"name\":\"release/next\"}]",MediaType.APPLICATION_JSON).header("X-Next-Page","3"));
  var page=service.listRefs("CRM/customer",GitLabRef.Kind.TAG,"release/next",2);
  assertEquals(3,page.nextPage());assertEquals("refs/tags/release/next",page.refs().get(0).ref().canonical());server.verify();
 }
}

