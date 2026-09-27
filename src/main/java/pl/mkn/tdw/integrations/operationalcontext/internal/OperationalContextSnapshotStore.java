package pl.mkn.tdw.integrations.operationalcontext.internal;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextSnapshot;

public interface OperationalContextSnapshotStore {

    OperationalContextStoredSnapshot currentStoredSnapshot();

    OperationalContextSnapshot publishCandidate(
            java.util.Map<String, String> candidateDocuments
    );

    OperationalContextCandidateAssessment assessCandidate(
            java.util.Map<String, String> candidateDocuments
    );

    OperationalContextStoredSnapshot decodeCandidate(java.util.Map<String, String> candidateDocuments);

    OperationalContextCandidateAssessment assessBatchCandidate(java.util.Map<String, String> candidateDocuments);

    OperationalContextSnapshot publishBatchCandidate(
            java.util.Map<String, String> candidateDocuments, String expectedDigest
    );
}
