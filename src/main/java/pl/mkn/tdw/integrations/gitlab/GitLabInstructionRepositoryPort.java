package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryFileRequest;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventory;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionRepositoryInventoryRequest;

public interface GitLabInstructionRepositoryPort {

    InstructionRepositoryFile readFile(InstructionRepositoryFileRequest request);

    InstructionRepositoryInventory loadFileInventory(InstructionRepositoryInventoryRequest request);
}
