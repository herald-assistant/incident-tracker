package pl.mkn.tdw.api.githubauth;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

@Component
public class CurrentAnalysisAiAuthRefResolver implements AnalysisAiAuthRefResolver {

    @Override
    public AnalysisAiAuthRef resolveForCurrentRequest() {
        return AnalysisAiAuthRef.localToken("Workspace fine-grained PAT");
    }
}
