package pl.mkn.incidenttracker.features.incidentanalysis.testsupport;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRepositoryProjectPathResolver;

public final class TestOperationalContextProjectPathResolver {

    private TestOperationalContextProjectPathResolver() {
    }

    public static OperationalContextRepositoryProjectPathResolver empty() {
        return new OperationalContextRepositoryProjectPathResolver(query -> OperationalContextCatalog.empty());
    }
}
