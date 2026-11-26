package pl.mkn.tdw.agenttools.openapi;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A single current catalog; retained operation objects belong only to in-flight calls. */
@Component
public class OpenApiToolCatalog {
    public interface Publisher {
        void publish(Map<String, List<OpenApiExecution>> next, Runnable commit);
    }

    private volatile Map<String, List<OpenApiExecution>> scopes = Map.of();
    private Publisher publisher = (next, commit) -> commit.run();

    public Map<String, List<OpenApiExecution>> current() { return scopes; }

    public synchronized void attach(Publisher publisher) {
        publisher.publish(scopes, () -> {});
        this.publisher = publisher;
    }

    public synchronized void replace(Map<String, List<OpenApiExecution>> next, Runnable persist) {
        var copied = new LinkedHashMap<String, List<OpenApiExecution>>();
        next.forEach((id, executions) -> copied.put(id, List.copyOf(executions)));
        var prepared = Map.copyOf(copied);
        publisher.publish(prepared, () -> {
            persist.run();
            scopes = prepared;
        });
    }
}
