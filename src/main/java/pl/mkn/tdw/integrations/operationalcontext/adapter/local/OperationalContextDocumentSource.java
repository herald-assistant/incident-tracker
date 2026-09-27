package pl.mkn.tdw.integrations.operationalcontext.adapter.local;

import pl.mkn.tdw.integrations.operationalcontext.internal.OperationalContextRawDocuments;

interface OperationalContextDocumentSource {

    OperationalContextRawDocuments loadDocuments();
}
