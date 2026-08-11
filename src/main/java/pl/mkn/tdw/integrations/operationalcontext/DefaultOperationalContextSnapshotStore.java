package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
@RequiredArgsConstructor
final class DefaultOperationalContextSnapshotStore implements OperationalContextSnapshotStore {

    private final LocalOperationalContextStore localStore;
    private final AtomicReference<OperationalContextStoredSnapshot> currentSnapshot = new AtomicReference<>();

    @Override
    public OperationalContextStoredSnapshot currentStoredSnapshot() {
        var snapshot = currentSnapshot.get();
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (currentSnapshot.get() == null) {
                currentSnapshot.set(logSnapshot(localStore.loadOrBootstrap()));
            }
            return currentSnapshot.get();
        }
    }

    @Override
    public synchronized OperationalContextSnapshot publishCandidate(java.util.Map<String, String> candidateDocuments) {
        var published = localStore.publishCandidate(candidateDocuments);
        currentSnapshot.set(published);
        return published.readSnapshot();
    }

    private OperationalContextStoredSnapshot logSnapshot(OperationalContextStoredSnapshot snapshot) {
        var catalog = snapshot.readSnapshot().catalog();
        log.info(
                "Operational context catalog loaded source={} teams={} processes={} systems={} integrations={} repositories={} codeSearchScopes={} boundedContexts={} glossaryTerms={} handoffRules={} openQuestions={}",
                snapshot.readSnapshot().source(),
                catalog.teams().size(),
                catalog.processes().size(),
                catalog.systems().size(),
                catalog.integrations().size(),
                catalog.repositories().size(),
                catalog.codeSearchScopes().size(),
                catalog.boundedContexts().size(),
                catalog.glossaryTerms().size(),
                catalog.handoffRules().size(),
                catalog.openQuestions().size()
        );
        return snapshot;
    }
}
