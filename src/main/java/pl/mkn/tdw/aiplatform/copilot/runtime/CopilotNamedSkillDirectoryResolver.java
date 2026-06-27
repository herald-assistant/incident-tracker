package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CopilotNamedSkillDirectoryResolver {

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public CopilotNamedSkillDirectoryResolver(CopilotSkillRuntimeLoader skillRuntimeLoader) {
        this.skillRuntimeLoader = skillRuntimeLoader;
    }

    public List<String> resolveSkillDirectories(List<String> skillNames) {
        return skillRuntimeLoader.resolveSelectedSkillRootDirectories(skillNames);
    }
}
