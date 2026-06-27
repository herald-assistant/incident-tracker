package pl.mkn.tdw.localworkspace.tokens;

import java.util.List;

public record LocalAccessTokenFile(
        String schema,
        int version,
        List<LocalAccessTokenRecord> tokens
) {
    public static final String SCHEMA = "tdw.local-access-tokens";
    public static final int VERSION = 1;

    public LocalAccessTokenFile {
        if (schema == null || schema.isBlank()) {
            schema = SCHEMA;
        }
        if (version <= 0) {
            version = VERSION;
        }
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    public static LocalAccessTokenFile empty() {
        return new LocalAccessTokenFile(SCHEMA, VERSION, List.of());
    }
}
