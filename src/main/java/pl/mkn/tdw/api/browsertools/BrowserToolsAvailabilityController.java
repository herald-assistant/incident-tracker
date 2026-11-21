package pl.mkn.tdw.api.browsertools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BrowserToolsAvailabilityController {
    @Value("${system-assistant.enabled:false}") private boolean systemAssistantEnabled;
    @GetMapping(value = "/browser-tools/availability.js", produces = "application/javascript")
    public ResponseEntity<String> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                "globalThis.__TDW_BROWSER_TOOLS_AVAILABILITY__=Object.freeze({systemAssistantEnabled:"
                        + systemAssistantEnabled + "});");
    }
}
