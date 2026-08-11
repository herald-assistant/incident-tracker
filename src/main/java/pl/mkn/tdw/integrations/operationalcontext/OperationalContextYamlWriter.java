package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Component
final class OperationalContextYamlWriter {

    private final Yaml yaml;

    OperationalContextYamlWriter() {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        options.setWidth(160);
        yaml = new Yaml(options);
    }

    String write(Map<String, Object> document) {
        return yaml.dump(document);
    }
}
