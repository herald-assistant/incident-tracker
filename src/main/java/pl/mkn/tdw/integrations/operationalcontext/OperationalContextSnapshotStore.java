package pl.mkn.tdw.integrations.operationalcontext;

interface OperationalContextSnapshotStore {

    OperationalContextStoredSnapshot currentStoredSnapshot();

    OperationalContextSnapshot publishCandidate(
            java.util.Map<String, String> candidateDocuments
    );
}
