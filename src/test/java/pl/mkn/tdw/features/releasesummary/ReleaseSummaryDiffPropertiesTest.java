package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryDiffProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseSummaryDiffPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReleaseSummaryDiffProperties.class)
    static class PropertiesConfiguration {}

    @Test void defaultsAndCommaSeparatedOverrideBind() {
        runner.run(context -> assertThat(context.getBean(ReleaseSummaryDiffProperties.class).getPathContainsAny())
                .isEqualTo(List.of("sql", "changelog")));
        runner.withPropertyValues("release-summary.diff.path-contains-any=migrations,schema")
                .run(context -> assertThat(context.getBean(ReleaseSummaryDiffProperties.class).getPathContainsAny())
                        .isEqualTo(List.of("migrations", "schema")));
    }

    @Test void emptyListAndBlankElementsFailStartup() {
        for (var value : List.of("", "sql,,changelog", "sql, ,changelog")) {
            runner.withPropertyValues("release-summary.diff.path-contains-any=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
