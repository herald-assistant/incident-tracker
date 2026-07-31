package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;

import java.util.Objects;

public record RuntimeConfigurationDiffValue(
        RuntimeConfigurationDiffValuePresence presence,
        RuntimeConfigurationValueType type,
        Object value,
        Integer cardinality
) {

    public RuntimeConfigurationDiffValue {
        Objects.requireNonNull(presence, "presence is required");
        if (presence == RuntimeConfigurationDiffValuePresence.ABSENT) {
            if (type != null || value != null || cardinality != null) {
                throw new IllegalArgumentException("ABSENT value cannot carry type, value or cardinality");
            }
        } else {
            Objects.requireNonNull(type, "type is required for PRESENT value");
            if (type == RuntimeConfigurationValueType.MAP
                    || type == RuntimeConfigurationValueType.LIST) {
                if (cardinality == null || cardinality < 0) {
                    throw new IllegalArgumentException("Collection value requires non-negative cardinality");
                }
            } else if (cardinality != null) {
                throw new IllegalArgumentException("Scalar value cannot carry cardinality");
            }
            if (type == RuntimeConfigurationValueType.NULL && value != null) {
                throw new IllegalArgumentException("NULL value cannot carry a non-null payload");
            }
        }
    }

    public static RuntimeConfigurationDiffValue absent() {
        return new RuntimeConfigurationDiffValue(
                RuntimeConfigurationDiffValuePresence.ABSENT,
                null,
                null,
                null
        );
    }

    public boolean scalar() {
        return presence == RuntimeConfigurationDiffValuePresence.PRESENT
                && type != RuntimeConfigurationValueType.MAP
                && type != RuntimeConfigurationValueType.LIST;
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationDiffValue[presence=" + presence
                + ", type=" + type
                + ", value=<redacted>"
                + ", cardinality=" + cardinality
                + "]";
    }
}
