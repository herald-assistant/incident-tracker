package pl.mkn.tdw.localworkspace.analysisruns;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes mutations of one local analysis run across live and history APIs. */
@Component
public class LocalAnalysisRunOperationGuard {

    private final ConcurrentHashMap<String, Semaphore> permits = new ConcurrentHashMap<>();

    public Optional<Lease> tryAcquire(String analysisId) {
        var acquired = new java.util.concurrent.atomic.AtomicReference<Lease>();
        permits.compute(analysisId, (id, existing) -> {
            var permit = existing != null ? existing : new Semaphore(1);
            if (permit.tryAcquire()) acquired.set(new Lease(id, permit));
            return permit;
        });
        return Optional.ofNullable(acquired.get());
    }

    public final class Lease implements AutoCloseable {
        private final String analysisId;
        private final Semaphore permit;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(String analysisId, Semaphore permit) {
            this.analysisId = analysisId;
            this.permit = permit;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                permits.computeIfPresent(analysisId, (id, current) -> {
                    if (current != permit) return current;
                    permit.release();
                    return permit.availablePermits() == 1 ? null : permit;
                });
            }
        }
    }
}
