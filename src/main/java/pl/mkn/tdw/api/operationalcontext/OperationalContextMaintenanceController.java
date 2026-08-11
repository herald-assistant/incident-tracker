package pl.mkn.tdw.api.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextMaintenanceDtos.CapabilitiesDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextMaintenanceDtos.DeleteImpactDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextMaintenanceDtos.EditableEntityDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextMaintenanceDtos.EntityWriteRequest;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextMaintenanceDtos.MutationResultDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationResult;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/operational-context/catalog")
@RequiredArgsConstructor
class OperationalContextMaintenanceController {

    private final OperationalContextCatalogMaintenanceService maintenanceService;
    private final OperationalContextPort operationalContextPort;

    @GetMapping("/capabilities")
    ResponseEntity<CapabilitiesDto> capabilities() {
        var snapshot = operationalContextPort.currentSnapshot();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CapabilitiesDto(
                        snapshot.source(),
                        Arrays.stream(OperationalContextCatalogEntityType.values())
                                .map(OperationalContextCatalogEntityType::externalName)
                                .toList()
                ));
    }

    @GetMapping("/entities/{type}/{id}")
    ResponseEntity<EditableEntityDto> entity(@PathVariable String type, @PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(toDto(maintenanceService.entity(type, id)));
    }

    @PostMapping("/entities/{type}")
    ResponseEntity<MutationResultDto> create(
            @PathVariable String type,
            @RequestBody EntityWriteRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        var command = command(type, null, request);
        var result = maintenanceService.create(command);
        var location = uriBuilder.path("/api/operational-context/catalog/entities/{type}/{id}")
                .buildAndExpand(type, command.id())
                .toUri();
        return ResponseEntity.created(location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(toDto(result));
    }

    @PutMapping("/entities/{type}/{id}")
    ResponseEntity<MutationResultDto> update(
            @PathVariable String type,
            @PathVariable String id,
            @RequestBody EntityWriteRequest request
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(toDto(maintenanceService.update(command(type, id, request))));
    }

    @GetMapping("/entities/{type}/{id}/delete-impact")
    ResponseEntity<DeleteImpactDto> deleteImpact(@PathVariable String type, @PathVariable String id) {
        var impact = maintenanceService.deleteImpact(type, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new DeleteImpactDto(
                        impact.type(), impact.id(), impact.sourceFile(),
                        impact.allowed(), impact.inboundReferences()
                ));
    }

    @DeleteMapping("/entities/{type}/{id}")
    ResponseEntity<Void> delete(@PathVariable String type, @PathVariable String id) {
        maintenanceService.delete(type, id);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private OperationalContextCatalogMutationCommand command(
            String pathType,
            String pathId,
            EntityWriteRequest request
    ) {
        if (request == null) {
            throw OperationalContextCatalogMaintenanceException.validation("Mutation request is required", List.of());
        }
        var canonicalPathType = OperationalContextCatalogEntityType.fromExternalName(pathType).externalName();
        if (!StringUtils.hasText(request.type()) || !canonicalPathType.equals(request.type().trim().replace('_', '-'))) {
            throw new OperationalContextCatalogMaintenanceException(
                    OperationalContextCatalogMaintenanceException.Code.ID_MISMATCH,
                    "Request type must match path type",
                    List.of(new OperationalContextCatalogFieldError("/type", "Type must match path type"))
            );
        }
        var effectiveId = pathId != null ? pathId : request.id();
        if (pathId != null && !java.util.Objects.equals(pathId, request.id())) {
            throw new OperationalContextCatalogMaintenanceException(
                    OperationalContextCatalogMaintenanceException.Code.ID_MISMATCH,
                    "Request ID must match path ID",
                    List.of(new OperationalContextCatalogFieldError("/id", "ID must match path ID"))
            );
        }
        return new OperationalContextCatalogMutationCommand(canonicalPathType, effectiveId, request.payload());
    }

    private EditableEntityDto toDto(pl.mkn.tdw.integrations.operationalcontext.OperationalContextEditableEntity entity) {
        return new EditableEntityDto(entity.type(), entity.id(), entity.sourceFile(), entity.payload());
    }

    private MutationResultDto toDto(OperationalContextCatalogMutationResult result) {
        return new MutationResultDto(toDto(result.entity()));
    }
}
