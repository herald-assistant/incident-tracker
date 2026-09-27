package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WorkspaceCopilotAccessTokenResolver implements CopilotAccessTokenResolver {

    private final CopilotPatSource patSource;

    @Override
    public CopilotAccessToken resolve(CopilotRunAuth auth) {
        var value = patSource.currentPat();
        if (!StringUtils.hasText(value)) {
            throw new CopilotLocalTokenMissingException();
        }
        if (!CopilotFineGrainedPat.valid(value)) {
            throw new CopilotPatInvalidException();
        }

        return new CopilotAccessToken(value.trim(), null, null, true);
    }
}
