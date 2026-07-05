package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.List;

public record OperationalContextOwnershipResolution(
        String situationType,
        List<Owner> primaryOwners,
        List<Owner> partnerOwners,
        List<String> resolutionPath,
        String handoffReason,
        List<String> visibilityLimits
) {

    public static final String INSIDE_BOUNDED_CONTEXT = "inside-bounded-context";
    public static final String INSIDE_SYSTEM = "inside-system";
    public static final String BOUNDED_CONTEXT_BOUNDARY = "bounded-context-boundary";
    public static final String SYSTEM_BOUNDARY = "system-boundary";
    public static final String SYSTEM_INFRASTRUCTURE = "system-infrastructure";
    public static final String EXTERNAL_SYSTEM_BOUNDARY = "external-system-boundary";
    public static final String AMBIGUOUS = "ambiguous";
    public static final String UNKNOWN = "unknown";

    public static final String SOURCE_EXPLICIT_OWNERSHIP = "explicit-ownership";
    public static final String SOURCE_PARENT_SYSTEM_OWNERSHIP = "parent-system-ownership";
    public static final String SOURCE_INFERRED_FROM_TARGET_NAME = "inferred-from-target-name";

    public OperationalContextOwnershipResolution {
        situationType = textOrDefault(situationType, UNKNOWN);
        primaryOwners = copyList(primaryOwners);
        partnerOwners = copyList(partnerOwners);
        resolutionPath = copyTextList(resolutionPath);
        handoffReason = text(handoffReason);
        visibilityLimits = copyTextList(visibilityLimits);
    }

    public static OperationalContextOwnershipResolution unknown(List<String> resolutionPath, List<String> visibilityLimits) {
        return new OperationalContextOwnershipResolution(
                UNKNOWN,
                List.of(),
                List.of(),
                resolutionPath,
                "Nie ustalono systemu ani bounded contextu dla ownership/handoff.",
                visibilityLimits
        );
    }

    public record Owner(
            String targetType,
            String targetId,
            String targetLabel,
            List<String> ownerTeamIds,
            String ownerLabel,
            String source,
            String confidence
    ) {

        public Owner {
            targetType = text(targetType);
            targetId = text(targetId);
            targetLabel = text(targetLabel);
            ownerTeamIds = copyTextList(ownerTeamIds);
            ownerLabel = text(ownerLabel);
            source = text(source);
            confidence = textOrDefault(confidence, "low");
        }
    }

    private static <T> List<T> copyList(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(OperationalContextOwnershipResolution::text)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String textOrDefault(String value, String defaultValue) {
        var normalized = text(value);
        return normalized != null ? normalized : defaultValue;
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
