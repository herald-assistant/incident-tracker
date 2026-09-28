package pl.mkn.tdw.localworkspace.analysisruns;

import java.util.List;
import java.util.Optional;

public interface LocalAnalysisRunStore {

    List<LocalAnalysisRunIndexEntry> listRuns();

    Optional<LocalAnalysisRunRecord> findById(String analysisId);

    void save(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record);

    default void updateStoreSnapshot(String analysisId, LocalAnalysisRunStoreSnapshot snapshot) {
        throw new UnsupportedOperationException("Store snapshots are unavailable for this local run store.");
    }

    void rename(String analysisId, String name);

    void delete(String analysisId);
}
