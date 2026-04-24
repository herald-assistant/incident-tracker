package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.List;

public record OperationalContextFilter(
        OperationalContextEntryType entryType,
        String path,
        List<String> values,
        OperationalContextFilterMode mode
) {

    public OperationalContextFilter {
        values = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        mode = mode != null ? mode : OperationalContextFilterMode.CONTAINS;
    }

    public static OperationalContextFilter exact(
            OperationalContextEntryType entryType,
            String path,
            String... values
    ) {
        return new OperationalContextFilter(entryType, path, List.of(values), OperationalContextFilterMode.EXACT);
    }

    public static OperationalContextFilter contains(
            OperationalContextEntryType entryType,
            String path,
            String... values
    ) {
        return new OperationalContextFilter(entryType, path, List.of(values), OperationalContextFilterMode.CONTAINS);
    }

    boolean isValid() {
        return entryType != null && StringUtils.hasText(path) && !values.isEmpty();
    }
}
