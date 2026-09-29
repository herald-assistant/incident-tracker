package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.uxinspector.job.error.UxInspectorJobException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

@RestController
@RequestMapping("/api/ux-inspector/captures")
@RequiredArgsConstructor
public class UxInspectorCaptureSnapshotController {
    private final UxInspectorCaptureSnapshotService snapshots;

    @CrossOrigin(origins = "${ux-inspector.capture.allowed-origins:*}", allowCredentials = "false")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<UploadReceipt> upload(@RequestBody JsonNode request,
            @RequestHeader(name = "Origin", required = false) String origin) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadReceipt(snapshots.save(request, origin)));
        } catch (IllegalArgumentException exception) {
            throw new UxInspectorJobException("UX_INSPECTOR_CAPTURE_INVALID", UserFacingErrorType.BAD_REQUEST,
                    exception.getMessage());
        }
    }

    @GetMapping("/{captureId}")
    public ResponseEntity<UxInspectorCaptureSnapshot> get(@PathVariable String captureId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.get(captureId));
    }

    public record UploadReceipt(String captureId) {}
}
