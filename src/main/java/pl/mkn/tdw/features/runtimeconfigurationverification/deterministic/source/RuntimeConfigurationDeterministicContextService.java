package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine.RuntimeConfigurationDeterministicEngine;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationVarParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationYamlParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationDeterministicContextService {

    private final RuntimeConfigurationSourceLoader sourceLoader;
    private final RuntimeConfigurationYamlParser yamlParser;
    private final RuntimeConfigurationVarParser varParser;
    private final RuntimeConfigurationDeterministicEngine deterministicEngine;

    public RuntimeConfigurationDeterministicContext build(
            RuntimeConfigurationScope scope,
            String sourceBranch,
            String targetBranch
    ) {
        return build(
                scope,
                sourceBranch,
                targetBranch,
                RuntimeConfigurationDeterministicContextListener.NO_OP
        );
    }

    public RuntimeConfigurationDeterministicContext build(
            RuntimeConfigurationScope scope,
            String sourceBranch,
            String targetBranch,
            RuntimeConfigurationDeterministicContextListener listener
    ) {
        var resolvedListener = listener != null
                ? listener
                : RuntimeConfigurationDeterministicContextListener.NO_OP;
        resolvedListener.onSourceStarted();
        var snapshots = sourceLoader.load(scope, sourceBranch, targetBranch);
        resolvedListener.onSourceCompleted();
        resolvedListener.onParseStarted();
        var source = parse(snapshots.source());
        var target = parse(snapshots.target());
        resolvedListener.onParseCompleted();
        resolvedListener.onDiffStarted();
        var context = deterministicEngine.build(
                scope,
                snapshots.source().coverage(),
                snapshots.target().coverage(),
                source,
                target
        );
        resolvedListener.onDiffCompleted(context);
        return context;
    }

    private ParsedConfigurationSnapshot parse(RuntimeConfigurationRawSnapshot snapshot) {
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
