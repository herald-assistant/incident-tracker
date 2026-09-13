package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;
import java.util.LinkedHashMap;

/** One operator-accepted entity mutation with field-level preconditions for updates. */
public record OperationalContextCatalogConditionalMutationCommand(
        String type,
        String id,
        Operation operation,
        String expectedDigest,
        List<FieldChange> changes
) {

    public OperationalContextCatalogConditionalMutationCommand {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public enum Operation { CREATE, UPDATE }

    public record FieldChange(String path, Object before, Object after) {
        public FieldChange {
            before = immutableValue(before);
            after = immutableValue(after);
        }

        private static Object immutableValue(Object value) {
            var holder = new LinkedHashMap<String, Object>();
            holder.put("value", value);
            return OperationalContextImmutableValues.copyMap(holder).get("value");
        }
    }
}
