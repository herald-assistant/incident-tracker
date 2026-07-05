package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.List;

public record OperationalContextOwnershipRequest(
        String situationType,
        List<String> systemIds,
        List<String> boundedContextIds,
        List<String> repositoryIds,
        List<String> codeSearchScopeIds,
        TechnicalTarget technicalTarget
) {

    public OperationalContextOwnershipRequest {
        situationType = text(situationType);
        systemIds = copyTextList(systemIds);
        boundedContextIds = copyTextList(boundedContextIds);
        repositoryIds = copyTextList(repositoryIds);
        codeSearchScopeIds = copyTextList(codeSearchScopeIds);
        technicalTarget = technicalTarget != null ? technicalTarget : TechnicalTarget.empty();
    }

    public static OperationalContextOwnershipRequest empty() {
        return new OperationalContextOwnershipRequest(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                TechnicalTarget.empty()
        );
    }

    public record TechnicalTarget(
            String repositoryId,
            String gitProjectPath,
            List<String> codeSearchScopeIds,
            List<String> systemIds,
            List<String> boundedContextIds,
            EndpointTarget endpoint,
            String sourceTool
    ) {

        public TechnicalTarget {
            repositoryId = text(repositoryId);
            gitProjectPath = text(gitProjectPath);
            codeSearchScopeIds = copyTextList(codeSearchScopeIds);
            systemIds = copyTextList(systemIds);
            boundedContextIds = copyTextList(boundedContextIds);
            endpoint = endpoint != null ? endpoint : EndpointTarget.empty();
            sourceTool = text(sourceTool);
        }

        public static TechnicalTarget empty() {
            return new TechnicalTarget(
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    EndpointTarget.empty(),
                    null
            );
        }
    }

    public record EndpointTarget(
            String httpMethod,
            String path,
            String controllerClass,
            String handlerMethod
    ) {

        public EndpointTarget {
            httpMethod = text(httpMethod);
            path = text(path);
            controllerClass = text(controllerClass);
            handlerMethod = text(handlerMethod);
        }

        public static EndpointTarget empty() {
            return new EndpointTarget(null, null, null, null);
        }

        boolean hasAnySignal() {
            return StringUtils.hasText(httpMethod)
                    || StringUtils.hasText(path)
                    || StringUtils.hasText(controllerClass)
                    || StringUtils.hasText(handlerMethod);
        }
    }

    private static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(OperationalContextOwnershipRequest::text)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
