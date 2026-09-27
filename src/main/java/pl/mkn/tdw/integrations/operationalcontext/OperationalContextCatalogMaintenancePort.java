package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogBatchMutationPreview;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogBatchMutationResult;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogConditionalBatchCommand;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogConditionalMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogMutationPreview;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogMutationResult;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDeleteImpact;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextEditableEntity;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextSnapshot;

import java.util.List;
import java.util.Map;

/** Validated, conditional maintenance of the local operational catalog. */
public interface OperationalContextCatalogMaintenancePort {

    OperationalContextEditableEntity entity(String typeName, String id);

    Map<String, Object> writablePayloadForUpdate(String typeName, String id);

    List<OperationalContextCatalogFieldError> validatePartialEditablePayload(
            String typeName, Map<String, Object> partialPayload);

    OperationalContextCatalogMutationResult create(OperationalContextCatalogMutationCommand command);

    OperationalContextCatalogMutationResult update(OperationalContextCatalogMutationCommand command);

    OperationalContextCatalogMutationResult applyAcceptedChanges(
            OperationalContextCatalogConditionalMutationCommand command);

    OperationalContextCatalogBatchMutationPreview previewAcceptedBatch(
            OperationalContextCatalogConditionalBatchCommand command);

    OperationalContextCatalogBatchMutationResult applyAcceptedBatch(
            OperationalContextCatalogConditionalBatchCommand command);

    OperationalContextCatalogMutationPreview previewCreate(OperationalContextCatalogMutationCommand command);

    OperationalContextCatalogMutationPreview previewUpdate(OperationalContextCatalogMutationCommand command);

    OperationalContextDeleteImpact deleteImpact(String typeName, String id);

    OperationalContextSnapshot delete(String typeName, String id);
}
