package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotNamedSkillDirectoryResolver {

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public List<String> resolveSkillDirectories(List<String> skillNames) {
        return skillRuntimeLoader.resolveSelectedSkillRootDirectories(skillNames);
    }
}
