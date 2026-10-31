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
 @Test void readsAnotherPageWithEncodedProjectAndTags() {
  var props=new GitLabProperties(); props.setBaseUrl("https://gitlab.example.com");
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  var service=new GitLabRepositoryRefService(props,new GitLabRestClientFactory(props,IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
  server.expect(requestTo("https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/tags?per_page=100&page=2&search=release%2Fnext"))
   .andRespond(withSuccess("[{\"name\":\"release/next\"}]",MediaType.APPLICATION_JSON).header("X-Next-Page","3"));
  var page=service.listRefs("CRM/customer",GitLabRef.Kind.TAG,"release/next",2);
  assertEquals(3,page.nextPage());assertEquals("refs/tags/release/next",page.refs().get(0).canonical());server.verify();
 }
}

