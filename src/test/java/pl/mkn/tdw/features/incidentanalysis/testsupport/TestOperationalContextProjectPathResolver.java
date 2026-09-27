package pl.mkn.tdw.features.incidentanalysis.testsupport;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextRepositoryProjectPathResolver;

public final class TestOperationalContextProjectPathResolver {

    private TestOperationalContextProjectPathResolver() {
    }

    public static OperationalContextRepositoryProjectPathResolver empty() {
        return new OperationalContextRepositoryProjectPathResolver(query -> OperationalContextCatalog.empty());
    }
}
