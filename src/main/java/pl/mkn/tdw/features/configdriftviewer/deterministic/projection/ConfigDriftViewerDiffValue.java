package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;

import java.util.Objects;

public record ConfigDriftViewerDiffValue(
        ConfigDriftViewerDiffValuePresence presence,
        ConfigDriftViewerValueType type,
        Object value,
        Integer cardinality
) {

    public ConfigDriftViewerDiffValue {
        Objects.requireNonNull(presence, "presence is required");
        if (presence == ConfigDriftViewerDiffValuePresence.ABSENT) {
            if (type != null || value != null || cardinality != null) {
                throw new IllegalArgumentException("ABSENT value cannot carry type, value or cardinality");
            }
        } else {
            Objects.requireNonNull(type, "type is required for PRESENT value");
            if (type == ConfigDriftViewerValueType.MAP
                    || type == ConfigDriftViewerValueType.LIST) {
                if (cardinality == null || cardinality < 0) {
                    throw new IllegalArgumentException("Collection value requires non-negative cardinality");
                }
            } else if (cardinality != null) {
                throw new IllegalArgumentException("Scalar value cannot carry cardinality");
            }
            if (type == ConfigDriftViewerValueType.NULL && value != null) {
                throw new IllegalArgumentException("NULL value cannot carry a non-null payload");
            }
        }
    }

    public static ConfigDriftViewerDiffValue absent() {
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.ABSENT,
                null,
                null,
                null
        );
    }

    public boolean scalar() {
        return presence == ConfigDriftViewerDiffValuePresence.PRESENT
                && type != ConfigDriftViewerValueType.MAP
                && type != ConfigDriftViewerValueType.LIST;
    }

    @Override
    public String toString() {
        return "ConfigDriftViewerDiffValue[presence=" + presence
                + ", type=" + type
                + ", value=<redacted>"
                + ", cardinality=" + cardinality
                + "]";
    }
}
