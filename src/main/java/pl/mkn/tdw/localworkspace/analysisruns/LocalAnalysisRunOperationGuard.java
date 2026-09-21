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
        var permit = permits.computeIfAbsent(analysisId, ignored -> new Semaphore(1));
        return permit.tryAcquire() ? Optional.of(new Lease(analysisId, permit)) : Optional.empty();
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
                permit.release();
                if (permit.availablePermits() == 1 && !permit.hasQueuedThreads()) {
                    permits.remove(analysisId, permit);
                }
            }
        }
    }
}
