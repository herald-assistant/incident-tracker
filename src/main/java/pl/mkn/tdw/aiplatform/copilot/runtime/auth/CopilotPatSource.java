package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

/** Supplies the operator's fine-grained PAT from workspace settings. */
public interface CopilotPatSource {

    String currentPat();
}
