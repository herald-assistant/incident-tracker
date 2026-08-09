package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

import com.fasterxml.jackson.annotation.JsonIgnore;

public final class ParsedConfigurationDocument {

    private final int index;
    private final Object profileValue;
    private final ParsedConfigurationNode root;

    public ParsedConfigurationDocument(int index, Object profileValue, ParsedConfigurationNode root) {
        this.index = index;
        this.profileValue = profileValue;
        this.root = root;
    }

    public int index() {
        return index;
    }

    @JsonIgnore
    public Object profileValue() {
        return profileValue;
    }

    public ParsedConfigurationNode root() {
        return root;
    }

    @Override
    public String toString() {
        return "ParsedConfigurationDocument[index=" + index
                + ", profileValue=<redacted>, root=<redacted>]";
    }
}
