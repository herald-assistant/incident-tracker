package pl.mkn.tdw.agenttools.openapi;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A single current catalog; retained operation objects belong only to in-flight calls. */
@Component
public class OpenApiToolCatalog {
    private volatile Map<String, List<OpenApiExecution>> scopes = Map.of();
    private Consumer<Map<String, List<OpenApiExecution>>> publisher = ignored -> {};

    public Map<String, List<OpenApiExecution>> current() { return scopes; }

    public synchronized void attach(Consumer<Map<String, List<OpenApiExecution>>> publisher) {
        publisher.accept(scopes);
        this.publisher = publisher;
    }

    public synchronized void replace(Map<String, List<OpenApiExecution>> next, Runnable persist) {
        var previous = scopes;
        var copied = new java.util.LinkedHashMap<String, List<OpenApiExecution>>();
        next.forEach((id, executions) -> copied.put(id, List.copyOf(executions)));
        next = Map.copyOf(copied);
        try {
            publisher.accept(next);
            persist.run();
            scopes = next;
        } catch (RuntimeException failure) {
            try { publisher.accept(previous); }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }
}
