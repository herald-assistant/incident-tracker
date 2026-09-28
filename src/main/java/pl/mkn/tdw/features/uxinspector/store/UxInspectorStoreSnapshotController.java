package pl.mkn.tdw.features.uxinspector.store;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;

import java.io.IOException;

@RestController
@RequestMapping("/api/ux-inspector/store-snapshots")
@RequiredArgsConstructor
public class UxInspectorStoreSnapshotController {
    private final UxInspectorStoreSnapshotService snapshots;
    private final AnalysisAiAuthRefResolver authResolver;

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public UxInspectorStoreSnapshotService.UploadReceipt upload(HttpServletRequest request) throws IOException {
        try {
            return snapshots.stage(request.getInputStream(), authResolver.resolveForCurrentRequest());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
