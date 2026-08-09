package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ConfigDriftViewerYamlParser {

    public ParsedConfigurationFile parse(String path, String content) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        var yaml = new Yaml(new SafeConstructor(options));
        var documents = new ArrayList<ParsedConfigurationDocument>();
        var issues = new ArrayList<ParsedConfigurationIssue>();

        try {
            var index = 0;
            for (var value : yaml.loadAll(content != null ? content : "")) {
                var root = ParsedConfigurationNodes.fromObject(
                        "document-" + index,
                        "",
                        value != null ? value : Map.of()
                );
                documents.add(new ParsedConfigurationDocument(
                        index,
                        profileValue(value),
                        root
                ));
                index++;
            }
            if (documents.isEmpty()) {
                documents.add(new ParsedConfigurationDocument(
                        0,
                        null,
                        ParsedConfigurationNodes.fromObject("document-0", "", Map.of())
                ));
            }
        } catch (MarkedYAMLException exception) {
            issues.add(new ParsedConfigurationIssue(
                    duplicate(exception) ? "YAML_DUPLICATE_KEY" : "YAML_PARSE_ERROR",
                    "",
                    exception.getProblemMark() != null
                            ? exception.getProblemMark().getLine() + 1
                            : null
            ));
        } catch (YAMLException exception) {
            issues.add(new ParsedConfigurationIssue("YAML_PARSE_ERROR", "", null));
        }

        return new ParsedConfigurationFile(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                path,
                documents,
                issues
        );
    }

    private Object profileValue(Object value) {
        if (!(value instanceof Map<?, ?> root)) {
            return null;
        }
        var spring = root.get("spring");
        if (!(spring instanceof Map<?, ?> springMap)) {
            return null;
        }
        var config = springMap.get("config");
        if (config instanceof Map<?, ?> configMap) {
            var activate = configMap.get("activate");
            if (activate instanceof Map<?, ?> activateMap && activateMap.containsKey("on-profile")) {
                return activateMap.get("on-profile");
            }
        }
        var profiles = springMap.get("profiles");
        if (profiles instanceof Map<?, ?> profilesMap) {
            return profilesMap.get("active");
        }
        return null;
    }

    private boolean duplicate(MarkedYAMLException exception) {
        var problem = exception.getProblem();
        return problem != null && problem.toLowerCase(java.util.Locale.ROOT).contains("duplicate");
    }
}
