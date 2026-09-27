package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

public interface OperationalContextRepositoryPathPort {

    List<String> resolveProjectPaths(String configuredGroup, List<String> projectHints);
}
