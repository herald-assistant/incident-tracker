package pl.mkn.incidenttracker.analysis;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRepositoryProjectPathResolver;

import java.util.List;

public final class TestOperationalContextProjectPathResolver {

    private TestOperationalContextProjectPathResolver() {
    }

    public static OperationalContextRepositoryProjectPathResolver empty() {
        return new OperationalContextRepositoryProjectPathResolver(query -> new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        ));
    }
}
