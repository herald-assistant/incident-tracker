package pl.mkn.tdw.features.configdriftviewer.deterministic.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.deterministic.engine.ConfigDriftViewerDeterministicEngine;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerVarParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerYamlParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjectionBuilder;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerDeterministicContextService {

    private final ConfigDriftViewerSourceLoader sourceLoader;
    private final ConfigDriftViewerYamlParser yamlParser;
    private final ConfigDriftViewerVarParser varParser;
    private final ConfigDriftViewerDeterministicEngine deterministicEngine;
    private final ConfigDriftViewerDiffProjectionBuilder diffProjectionBuilder;

    public ConfigDriftViewerDeterministicBuildResult build(
            ConfigDriftViewerScope scope,
            String sourceBranch,
            String targetBranch
    ) {
        return build(
                scope,
                sourceBranch,
                targetBranch,
                ConfigDriftViewerDeterministicContextListener.NO_OP
        );
    }

    public ConfigDriftViewerDeterministicBuildResult build(
            ConfigDriftViewerScope scope,
            String sourceBranch,
            String targetBranch,
            ConfigDriftViewerDeterministicContextListener listener
    ) {
        var resolvedListener = listener != null
                ? listener
                : ConfigDriftViewerDeterministicContextListener.NO_OP;
        resolvedListener.onSourceStarted();
        var snapshots = sourceLoader.load(scope, sourceBranch, targetBranch);
        resolvedListener.onSourceCompleted();
        resolvedListener.onParseStarted();
        var source = parse(snapshots.source());
        var target = parse(snapshots.target());
        resolvedListener.onParseCompleted();
        resolvedListener.onDiffStarted();
        ConfigDriftViewerDeterministicContext context = deterministicEngine.build(
                scope,
                snapshots.source().coverage(),
                snapshots.target().coverage(),
                source,
                target
        );
        var result = new ConfigDriftViewerDeterministicBuildResult(
                context,
                diffProjectionBuilder.build(source, target, context)
        );
        resolvedListener.onDiffCompleted(result);
        return result;
    }

    private ParsedConfigurationSnapshot parse(ConfigDriftViewerRawSnapshot snapshot) {
        var files = new ArrayList<ParsedConfigurationFile>();
        for (var file : snapshot.files()) {
            files.add(switch (file.role()) {
                case APPLICATION_YAML -> yamlParser.parse(file.path(), file.content());
                case GLOBAL_VAR, LOCAL_VAR -> varParser.parse(
                        file.role(),
                        file.path(),
                        file.content()
                );
            });
        }
        return new ParsedConfigurationSnapshot(snapshot.branch(), files);
    }
}
