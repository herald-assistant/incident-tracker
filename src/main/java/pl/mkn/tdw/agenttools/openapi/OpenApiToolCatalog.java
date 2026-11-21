package pl.mkn.tdw.agenttools.openapi;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import java.util.List;
import java.util.function.Consumer;

/** A single current catalog; retained operation objects belong only to in-flight calls. */
@Component
public class OpenApiToolCatalog {
    private volatile List<OpenApiOperation> operations = List.of();
    private Consumer<List<OpenApiOperation>> publisher = ignored -> {};

    public List<OpenApiOperation> current() { return operations; }

    public synchronized void attach(Consumer<List<OpenApiOperation>> publisher) {
        publisher.accept(operations);
        this.publisher = publisher;
    }

    public synchronized void replace(List<OpenApiOperation> next, Runnable persist) {
        var previous = operations;
        next = List.copyOf(next);
        try {
            publisher.accept(next);
            persist.run();
            operations = next;
        } catch (RuntimeException failure) {
            try { publisher.accept(previous); }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }
}
