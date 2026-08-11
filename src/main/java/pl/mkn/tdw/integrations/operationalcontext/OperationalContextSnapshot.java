package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.Objects;

public record OperationalContextSnapshot(
        String contentDigest,
        String source,
        OperationalContextCatalog catalog
) {

    public OperationalContextSnapshot {
        contentDigest = StringUtils.hasText(contentDigest) ? contentDigest : "unknown";
        source = StringUtils.hasText(source) ? source : "unknown";
        catalog = Objects.requireNonNull(catalog, "catalog");
    }

    static OperationalContextSnapshot local(OperationalContextCatalog catalog) {
        return new OperationalContextSnapshot("unknown", "local", catalog);
    }
}
