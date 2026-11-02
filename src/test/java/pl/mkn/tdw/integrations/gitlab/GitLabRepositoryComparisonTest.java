package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.gitlab.adapter.rest.GitLabRestRepositoryComparisonAdapter;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.service.GitLabRepositoryTreeService;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitLabRepositoryComparisonTest {
    private static final String BEFORE = "a".repeat(40);
    private static final String AFTER = "b".repeat(40);

    @Test
    void verifiesAllPathsFromTreesEvenWhenCompareOmitsFiles() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        var clients = new GitLabRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(builder));
        var adapter = new GitLabRestRepositoryComparisonAdapter(properties, clients, new GitLabRepositoryTreeService(clients));
        server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(tree(AFTER))).andRespond(withSuccess("""
                [{"id":"cccccccccccccccccccccccccccccccccccccccc","path":"data/statuses.csv","type":"blob","mode":"100644"},
                 {"id":"dddddddddddddddddddddddddddddddddddddddd","path":"db/customer.sql","type":"blob","mode":"100644"}]
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(compare())).andRespond(withSuccess("""
                {"compare_timeout":false,"diffs":[{"old_path":"db/customer.sql","new_path":"db/customer.sql",
                "new_file":true,"diff":"@@ -0,0 +1 @@\\n+create table customer (id bigint);"}]}
                """, MediaType.APPLICATION_JSON));
        var result = adapter.compare("CRM", "customer", BEFORE, AFTER);
        assertTrue(result.fileListComplete());
        assertEquals(2, result.changes().size());
        assertFalse(result.changes().get(0).patchComplete());
        assertTrue(result.changes().get(1).patchComplete());
        assertEquals("ADDED", result.changes().get(0).changeType());
        server.verify();
    }

    @Test
    void identicalPinnedCommitsRequireNoHttp() {
        var properties = new GitLabProperties();
        var adapter = new GitLabRestRepositoryComparisonAdapter(properties, null, null);
        assertTrue(adapter.compare("CRM", "customer", BEFORE, BEFORE).changes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> adapter.compare("CRM", "customer", "main", AFTER));
    }

    private String tree(String ref) {
        return "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/tree?recursive=true&per_page=100&ref=" + ref + "&page=1";
    }
    private String compare() {
        return "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/compare?from=" + BEFORE + "&to=" + AFTER + "&straight=true";
    }

    @Test void followsTreePaginationAndNeverNeedsBlobHttpEvenForUnchangedLargeInventories() {
        var fixture=new Fixture();
        fixture.server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess("["+node("db/customer.xml","c","100644")+"]",MediaType.APPLICATION_JSON).header("X-Next-Page","2"));
        fixture.server.expect(requestTo(tree(BEFORE).replace("&page=1","&page=2"))).andRespond(withSuccess("["+node("db/labels.sql","d","100644")+"]",MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(tree(AFTER))).andRespond(withSuccess("["+node("db/customer.xml","c","100644")+","+node("db/labels.sql","d","100644")+"]",MediaType.APPLICATION_JSON));
        var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);
        assertTrue(result.fileListComplete());assertTrue(result.changes().isEmpty());fixture.server.verify();
    }
    @Test void timeoutOrUnverifiableCompareKeepsTheVerifiedChangedFile() {
        for(var response:java.util.List.of("{\"compare_timeout\":true,\"diffs\":[]}","{\"diffs\":[]}","{\"compare_timeout\":false,\"diffs\":null}")) {
            var fixture=new Fixture();fixture.addedTree("db/customer.xml","100644");
            fixture.server.expect(requestTo(compare())).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
            var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);
            assertTrue(result.fileListComplete());assertEquals(1,result.changes().size());assertFalse(result.changes().get(0).patchComplete());assertFalse(result.limitations().isEmpty());fixture.server.verify();
        }
    }
    @Test void collapsedLargeBinaryLfsSymlinkAndSubmoduleChangesDoNotVanish() {
        for(var flag:java.util.List.of("\"collapsed\":true","\"too_large\":true","\"diff\":\"\"")) {
            var fixture=new Fixture();fixture.addedTree("assets/customer.bin","100644");
            fixture.server.expect(requestTo(compare())).andRespond(withSuccess("{\"compare_timeout\":false,\"diffs\":[{\"old_path\":\"assets/customer.bin\",\"new_path\":\"assets/customer.bin\",\"new_file\":true,"+flag+"}]}",MediaType.APPLICATION_JSON));
            var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);assertEquals(1,result.changes().size());assertFalse(result.changes().get(0).patchComplete());fixture.server.verify();
        }
        for(var mode:java.util.List.of("100644","100755","120000","160000")) {
            var fixture=new Fixture();fixture.addedTree("data/customer",""+mode);
            fixture.server.expect(requestTo(compare())).andRespond(withSuccess("{\"compare_timeout\":false,\"diffs\":[]}",MediaType.APPLICATION_JSON));
            var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);assertEquals(mode,result.changes().get(0).newMode());assertFalse(result.changes().get(0).patchComplete());fixture.server.verify();
        }
    }
    @Test void recordsRenameDeletionAndModeOnlyChangeAgainstBothPinnedTrees() {
        var fixture=new Fixture();
        fixture.server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess("["+node("db/old.xml","c","100644")+","+node("db/removed.sql","d","100644")+","+node("bin/customer.sh","e","100644")+"]",MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(tree(AFTER))).andRespond(withSuccess("["+node("db/new.xml","c","100644")+","+node("bin/customer.sh","e","100755")+"]",MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(compare())).andRespond(withSuccess("{\"compare_timeout\":false,\"diffs\":[{\"old_path\":\"db/old.xml\",\"new_path\":\"db/new.xml\",\"renamed_file\":true,\"diff\":\"\"}]}",MediaType.APPLICATION_JSON));
        var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);assertEquals(3,result.changes().size());
        assertEquals(java.util.Set.of("MODIFIED","RENAMED","REMOVED"),result.changes().stream().map(change->change.changeType()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("db/old.xml",result.changes().get(1).oldPath());fixture.server.verify();
    }
    @Test void treeFailureDuplicateInvalidPathAndInvalidPaginationCannotProduceCompleteManifest() {
        for(var body:java.util.List.of("null","["+node("../customer.xml","c","100644")+"]","["+node("db/customer.xml","c","100644")+","+node("db/customer.xml","c","100644")+"]")) {
            var fixture=new Fixture();fixture.server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThrows(RuntimeException.class,()->fixture.adapter.compare("CRM","customer",BEFORE,AFTER));fixture.server.verify();
        }
        var fixture=new Fixture();fixture.server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON).header("X-Next-Page","1"));
        assertThrows(RuntimeException.class,()->fixture.adapter.compare("CRM","customer",BEFORE,AFTER));fixture.server.verify();
    }
    @Test void compareHttpFailureIsNonBlockingButTreeHttpFailureIsNot() {
        var fixture=new Fixture();fixture.addedTree("db/customer.xml","100644");
        fixture.server.expect(requestTo(compare())).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.FORBIDDEN));
        assertFalse(fixture.adapter.compare("CRM","customer",BEFORE,AFTER).changes().get(0).patchComplete());fixture.server.verify();
        var denied=new Fixture();denied.server.expect(requestTo(tree(BEFORE))).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(RuntimeException.class,()->denied.adapter.compare("CRM","customer",BEFORE,AFTER));denied.server.verify();
    }
    private static String node(String path,String id,String mode) {
        return "{\"id\":\""+id.repeat(40)+"\",\"path\":\""+path+"\",\"type\":\""+(mode.equals("160000")?"commit":"blob")+"\",\"mode\":\""+mode+"\"}";
    }
    @Test void measuresMetadataPreparationForEightThousandFilesWithoutContentReads() {
        var fixture=new Fixture();
        for(var ref:java.util.List.of(BEFORE,AFTER))for(int page=1;page<=80;page++) {
            var entries=new java.util.ArrayList<String>();
            for(int offset=0;offset<100;offset++) {
                int index=(page-1)*100+offset;
                entries.add(node("crm/customer-"+index+".xml",ref.equals(AFTER)&&index==7999?"d":"c","100644"));
            }
            var response=withSuccess("["+String.join(",",entries)+"]",MediaType.APPLICATION_JSON);
            if(page<80)response.header("X-Next-Page",Integer.toString(page+1));
            fixture.server.expect(requestTo(tree(ref).replace("&page=1","&page="+page))).andRespond(response);
        }
        fixture.server.expect(requestTo(compare())).andRespond(withSuccess("{\"compare_timeout\":false,\"diffs\":[]}",MediaType.APPLICATION_JSON));
        long start=System.nanoTime();var result=fixture.adapter.compare("CRM","customer",BEFORE,AFTER);
        long elapsed=(System.nanoTime()-start)/1_000_000;
        assertTrue(result.fileListComplete());assertEquals(1,result.changes().size());fixture.server.verify();
        System.out.printf("Diff preparation synthetic benchmark: filesPerRef=8000 metadataRequests=160 compareRequests=1 contentReads=0 aiRuns=0 elapsedMs=%d%n",elapsed);
    }
    private class Fixture {
        final MockRestServiceServer server;
        final GitLabRestRepositoryComparisonAdapter adapter;
        Fixture() {
            var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();var properties=new GitLabProperties();properties.setBaseUrl("https://gitlab.example.com");
            var clients=new GitLabRestClientFactory(properties,IntegrationRestClientBuilderFactoryTestCreator.create(builder));
            adapter=new GitLabRestRepositoryComparisonAdapter(properties,clients,new GitLabRepositoryTreeService(clients));
        }
        void addedTree(String path,String mode) {
            server.expect(requestTo(tree(BEFORE))).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
            server.expect(requestTo(tree(AFTER))).andRespond(withSuccess("["+node(path,"c",mode)+"]",MediaType.APPLICATION_JSON));
        }
    }
}