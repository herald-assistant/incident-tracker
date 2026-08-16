package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record GitLabFrontendBootstrapRoot(
        String rootId,
        String bootstrapSymbol,
        GitLabFrontendSourceReference bootstrapSource,
        GitLabFrontendSourceReference applicationConfigSource,
        String routerProviderSymbol,
        GitLabFrontendSourceReference routerProviderSource,
        String routeCollectionSymbol
) {

    public GitLabFrontendBootstrapRoot {
        rootId = required(rootId, "rootId");
        bootstrapSymbol = required(bootstrapSymbol, "bootstrapSymbol");
        bootstrapSource = Objects.requireNonNull(bootstrapSource, "bootstrapSource must not be null");
        routerProviderSymbol = required(routerProviderSymbol, "routerProviderSymbol");
        routerProviderSource = Objects.requireNonNull(
                routerProviderSource,
                "routerProviderSource must not be null"
        );
        routeCollectionSymbol = normalize(routeCollectionSymbol);
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
