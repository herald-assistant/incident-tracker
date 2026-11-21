package pl.mkn.tdw.integrations.openapi;

import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import java.util.List;

public interface OpenApiImportPort {
    List<OpenApiOperation> parse(String id, String yaml);
}
