package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import java.util.List;
import java.util.Set;

public record OperationalContextQuery(
        Set<OperationalContextEntryType> includedEntryTypes,
        List<OperationalContextFilter> filters,
        boolean includeIndexDocument
) {

    public OperationalContextQuery {
        includedEntryTypes = includedEntryTypes != null ? Set.copyOf(includedEntryTypes) : Set.of();
        filters = filters == null ? List.of() : filters.stream()
                .filter(OperationalContextFilter::isValid)
                .toList();
    }

    public static OperationalContextQuery all() {
        return new OperationalContextQuery(Set.of(), List.of(), true);
    }

    public static OperationalContextQuery forEntryTypes(OperationalContextEntryType... entryTypes) {
        return new OperationalContextQuery(Set.of(entryTypes), List.of(), true);
    }

    public boolean includes(OperationalContextEntryType entryType) {
        return includedEntryTypes.isEmpty() || includedEntryTypes.contains(entryType);
    }

    public boolean isUnfiltered() {
        return includedEntryTypes.isEmpty() && filters.isEmpty() && includeIndexDocument;
    }

    public List<OperationalContextFilter> filtersFor(OperationalContextEntryType entryType) {
        return filters.stream()
                .filter(filter -> filter.entryType() == entryType)
                .toList();
    }
}
