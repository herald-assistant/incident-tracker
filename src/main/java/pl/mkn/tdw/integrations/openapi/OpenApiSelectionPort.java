package pl.mkn.tdw.integrations.openapi;

import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection;

import java.util.List;

public interface OpenApiSelectionPort {
    OpenApiSelection.Preview preview(String apiId, String yaml);

    OpenApiSelection.Document document(String apiId, String yaml);

    OpenApiSelection.Prepared select(String apiId, String yaml, List<OpenApiSelection.OperationKey> operations);
}
