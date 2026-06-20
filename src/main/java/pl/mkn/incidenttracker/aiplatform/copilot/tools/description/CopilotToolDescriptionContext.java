package pl.mkn.incidenttracker.aiplatform.copilot.tools.description;

import org.springframework.util.StringUtils;

public record CopilotToolDescriptionContext(String profileId) {

    public static CopilotToolDescriptionContext empty() {
        return new CopilotToolDescriptionContext(null);
    }

    public static CopilotToolDescriptionContext profile(String profileId) {
        return new CopilotToolDescriptionContext(profileId);
    }

    public CopilotToolDescriptionContext {
        profileId = StringUtils.hasText(profileId) ? profileId.trim() : null;
    }

    public boolean matchesProfile(String expectedProfileId) {
        return StringUtils.hasText(expectedProfileId)
                && expectedProfileId.trim().equals(profileId);
    }
}
