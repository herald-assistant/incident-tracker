package pl.mkn.tdw.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class FlowExplorerFollowUpPromptPreparationService {

    public FlowExplorerPromptPreparation prepare(String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("Flow Explorer follow-up message is required.");
        }
        return new FlowExplorerPromptPreparation(
                "Uzyj skilla `flow-explorer-follow-up-chat` przed odpowiedzia.\n\n" + message.trim(),
                List.of(),
                Map.of()
        );
    }
}
