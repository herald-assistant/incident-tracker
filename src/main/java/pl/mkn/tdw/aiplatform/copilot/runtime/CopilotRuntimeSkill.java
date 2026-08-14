package pl.mkn.tdw.aiplatform.copilot.runtime;

public record CopilotRuntimeSkill(
        String name,
        String description,
        int lineCount,
        String markdown,
        String rawMarkdown,
        CopilotRuntimeSkillState state,
        boolean restoreAvailable
) {
}
