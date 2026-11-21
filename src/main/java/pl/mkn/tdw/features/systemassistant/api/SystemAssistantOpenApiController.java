package pl.mkn.tdw.features.systemassistant.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.openapi.OpenApiSelectionPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection;

import java.util.List;

@RestController
@RequestMapping("/api/system-assistant/configuration/openapi")
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SystemAssistantOpenApiController {
    private final OpenApiSelectionPort selection;

    public record PreviewRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,23}") String id,
                                 @NotBlank @Size(max = OpenApiSelection.MAX_SOURCE_CHARACTERS) String yaml) {
    }

    public record SelectionRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,23}") String id,
                                   @NotBlank @Size(max = OpenApiSelection.MAX_SOURCE_CHARACTERS) String yaml,
                                   @NotEmpty @Size(max = OpenApiSelection.MAX_SELECTED) List<OpenApiSelection.OperationKey> operations) {
    }

    @PostMapping("/preview")
    public OpenApiSelection.Preview preview(@Valid @RequestBody PreviewRequest request) {
        return selection.preview(request.id(), request.yaml());
    }

    @PostMapping("/document")
    public OpenApiSelection.Document document(@Valid @RequestBody PreviewRequest request) {
        return selection.document(request.id(), request.yaml());
    }

    @PostMapping("/selection")
    public OpenApiSelection.Prepared select(@Valid @RequestBody SelectionRequest request) {
        return selection.select(request.id(), request.yaml(), request.operations());
    }
}
