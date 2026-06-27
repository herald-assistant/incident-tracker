package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CopilotRunPreparationService {

    private final CopilotPreparedSessionFactory preparedSessionFactory;

    public CopilotPreparedSession prepare(CopilotRunRequest request) {
        return preparedSessionFactory.prepare(request);
    }
}
