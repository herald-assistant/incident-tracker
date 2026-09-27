package pl.mkn.tdw.integrations.operationalcontext.contract;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

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

    public static OperationalContextSnapshot local(OperationalContextCatalog catalog) {
        return new OperationalContextSnapshot("unknown", "local", catalog);
    }
}
