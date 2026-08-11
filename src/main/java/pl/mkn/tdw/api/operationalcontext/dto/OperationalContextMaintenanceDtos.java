package pl.mkn.tdw.api.operationalcontext.dto;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDeleteImpact.InboundReference;

import java.util.List;
import java.util.Map;

public final class OperationalContextMaintenanceDtos {

    private OperationalContextMaintenanceDtos() {
    }

    public record CapabilitiesDto(
            String source,
            List<String> supportedEntityTypes
    ) {

        public CapabilitiesDto {
            supportedEntityTypes = supportedEntityTypes == null ? List.of() : List.copyOf(supportedEntityTypes);
        }
    }

    public record EntityWriteRequest(
            String type,
            String id,
            Map<String, Object> payload
    ) {
    }

    public record EditableEntityDto(
            String type,
            String id,
            String sourceFile,
            Map<String, Object> payload
    ) {
    }

    public record MutationResultDto(
            EditableEntityDto entity
    ) {
    }

    public record DeleteImpactDto(
            String type,
            String id,
            String sourceFile,
            boolean allowed,
            List<InboundReference> inboundReferences
    ) {

        public DeleteImpactDto {
            inboundReferences = inboundReferences == null ? List.of() : List.copyOf(inboundReferences);
        }
    }
}
