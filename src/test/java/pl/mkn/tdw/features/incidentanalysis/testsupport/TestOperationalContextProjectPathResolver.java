package pl.mkn.tdw.features.incidentanalysis.testsupport;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRepositoryProjectPathResolver;

public final class TestOperationalContextProjectPathResolver {

    private TestOperationalContextProjectPathResolver() {
    }

    public static OperationalContextRepositoryProjectPathResolver empty() {
        return new OperationalContextRepositoryProjectPathResolver(query -> OperationalContextCatalog.empty());
    }
}
