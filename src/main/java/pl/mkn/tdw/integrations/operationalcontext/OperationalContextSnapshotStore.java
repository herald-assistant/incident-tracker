package pl.mkn.tdw.integrations.operationalcontext;

interface OperationalContextSnapshotStore {

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
