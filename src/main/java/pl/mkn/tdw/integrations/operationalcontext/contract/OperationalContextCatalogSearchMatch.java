package pl.mkn.tdw.integrations.operationalcontext.contract;

import java.util.List;

public record OperationalContextCatalogSearchMatch(
        String type, String id, int score, List<String> fields, List<String> signals, String why
) {
}
