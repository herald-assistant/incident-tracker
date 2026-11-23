package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;

final class OpenApiYaml {
    private OpenApiYaml() {}

    static JsonNode read(ObjectMapper mapper, String yaml, int limit) {
        if (yaml == null || yaml.isBlank() || yaml.length() > limit)
            throw new OpenApiException("Wymagany dokument JSON/YAML do " + limit + " znaków.");
        try {
            var options = new LoaderOptions();
            options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(100); options.setCodePointLimit(limit);
            return mapper.valueToTree(new Yaml(new SafeConstructor(options)).load(yaml));
        } catch (RuntimeException ex) { throw new OpenApiException("Niepoprawny dokument JSON/YAML specyfikacji OpenAPI."); }
    }

    static String write(ObjectMapper mapper, JsonNode document) {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        return new Yaml(options).dump(mapper.convertValue(document, Object.class));
    }
}
