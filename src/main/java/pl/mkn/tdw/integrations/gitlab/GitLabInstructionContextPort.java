package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;

public interface GitLabInstructionContextPort {

    InstructionContextResult discover(InstructionContextRequest request);
}
