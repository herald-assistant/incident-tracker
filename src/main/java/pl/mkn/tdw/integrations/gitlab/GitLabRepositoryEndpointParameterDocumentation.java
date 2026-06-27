package pl.mkn.tdw.integrations.gitlab;

public record GitLabRepositoryEndpointParameterDocumentation(
        String name,
        String in,
        boolean required,
        String type,
        String description
) {
    public boolean sameParameter(GitLabRepositoryEndpointParameterDocumentation other) {
        return other != null
                && equalsIgnoreCase(name, other.name())
                && equalsIgnoreCase(in, other.in());
    }

    public GitLabRepositoryEndpointParameterDocumentation merge(
            GitLabRepositoryEndpointParameterDocumentation other
    ) {
        if (other == null || !sameParameter(other)) {
            return this;
        }
        return new GitLabRepositoryEndpointParameterDocumentation(
                firstText(name, other.name()),
                firstText(in, other.in()),
                required || other.required(),
                firstText(type, other.type()),
                firstText(description, other.description())
        );
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.equalsIgnoreCase(right);
    }

    private static String firstText(String left, String right) {
        return hasText(left) ? left : hasText(right) ? right : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
