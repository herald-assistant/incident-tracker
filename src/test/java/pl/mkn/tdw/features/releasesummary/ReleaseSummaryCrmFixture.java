package pl.mkn.tdw.features.releasesummary;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
final class ReleaseSummaryCrmFixture {
    static final String BEFORE="a".repeat(40),AFTER="b".repeat(40),PATH="db/customer-tags.xml",CODE_PATH="src/CustomerTagService.java";
    static final String XML="""
            <databaseChangeLog>
              <changeSet id="customer-tags" author="crm">
                <createTable tableName="customer_tag" remarks="Etykiety klienta używane w wyszukiwaniu">
                  <column name="id" type="bigint"><constraints primaryKey="true" nullable="false"/></column>
                  <column name="customer_id" type="bigint"><constraints nullable="false"/></column>
                  <column name="label" type="varchar(40)" defaultValue="general"><constraints nullable="false"/></column>
                </createTable>
                <createIndex tableName="customer_tag" indexName="ix_customer_tag_customer_label" unique="true">
                  <column name="customer_id"/><column name="label"/>
                </createIndex>
                <addForeignKeyConstraint baseTableName="customer_tag" baseColumnNames="customer_id"
                  referencedTableName="customer" referencedColumnNames="id" constraintName="fk_tag_customer" onDelete="CASCADE"/>
                <addUniqueConstraint tableName="customer_tag" columnNames="customer_id,label" constraintName="uk_customer_tag"/>
                <createSequence sequenceName="seq_customer_tag" startValue="1" incrementBy="1"/>
                <update tableName="customer"><column name="label_state" value="READY"/><where>label_state IS NULL</where></update>
                <rollback><dropTable tableName="customer_tag"/><dropSequence sequenceName="seq_customer_tag"/></rollback>
              </changeSet>
            </databaseChangeLog>
            """;
    static final String CODE="""
            package crm;
            import org.springframework.jdbc.core.JdbcTemplate;
            // CRM: labels group customers for search and operator filters.
            // A label is unique per customer, limited to 40 characters and defaults to general.
            // The sequence supplies tag ids. Deleting a customer removes its tags.
            // READY marks legacy customer records prepared for the label feature.
            public class CustomerTagService {
                private final JdbcTemplate jdbc;
                CustomerTagService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
                void addLabel(long customerId, String label) {
                    String effectiveLabel = label == null ? "general" : label;
                    if (effectiveLabel.length() > 40) throw new IllegalArgumentException("Label too long");
                    jdbc.update("INSERT INTO customer_tag(id, customer_id, label) VALUES(nextval('seq_customer_tag'), ?, ?)", customerId, effectiveLabel);
                }
                java.util.List<Long> findTaggedCustomer(long customerId, String label) {
                    return jdbc.queryForList("SELECT customer_id FROM customer_tag WHERE customer_id = ? AND label = ?", Long.class, customerId, label);
                }
                boolean labelsReady(long customerId) { return "READY".equals(jdbc.queryForObject("SELECT label_state FROM customer WHERE id = ?", String.class, customerId)); }
                void deleteCustomer(long customerId) { jdbc.update("DELETE FROM customer WHERE id = ?", customerId); }
            }
            """;
    static ReleaseSummaryScope scope() {
        return new ReleaseSummaryScope("crm","CRM Customers","CRM","digest",
                List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),"refs/heads/current",BEFORE,"refs/tags/next",AFTER)),
                List.of(Map.of("type","system","id","crm","payload",Map.of("name","CRM Customers",
                        "summary","Etykiety klienta wspierają wyszukiwanie i filtry operatora.","implementation","customer: "+CODE_PATH))));
    }
    static ReleaseSummaryEvidence evidence() {
        return evidence(PATH,"<databaseChangeLog/>",XML);
    }
    static ReleaseSummaryEvidence evidence(String path,String before,String after) {
      return new ReleaseSummaryEvidence(
        List.of(new ReleaseSummaryEvidence.ChangedFile("customer","customer",new GitLabRepositoryComparison.Change(before==null?null:path,after==null?null:path,
              before==null?null:"c".repeat(40),after==null?null:"d".repeat(40),before==null?null:"100644",after==null?null:"100644",
          "@@ -1 +1 @@\n-"+Objects.toString(before,"")+"\n+"+Objects.toString(after,""),true))),List.of(),true);
    }
}
