package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CopilotEffectiveContextTierReader {

    private final CopilotSdkProperties properties;

    public CopilotEffectiveContextTier read(CopilotSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Copilot session must not be null");
        }
        var timeout = properties.getContextTier().getVerificationTimeout();
        var timeoutMs = timeout != null ? timeout.toMillis() : 20_000L;
        var current = session.getRpc().model.getCurrent()
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .join();
        if (current == null) {
            throw new IllegalStateException("Copilot SDK returned no current session model state");
        }
        return new CopilotEffectiveContextTier(
                current.modelId(),
                current.reasoningEffort(),
                current.contextTier() != null ? current.contextTier().getValue() : null
        );
    }
}
