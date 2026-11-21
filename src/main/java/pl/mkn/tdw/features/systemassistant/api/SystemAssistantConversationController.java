package pl.mkn.tdw.features.systemassistant.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.systemassistant.chat.SystemAssistantConversationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConversation;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantMessageRequest;

import java.net.URI;

@RestController
@RequestMapping("/api/system-assistant/conversations")
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SystemAssistantConversationController {
    private final SystemAssistantConversationService conversations;

    public record CreateRequest(SystemAssistantConversation.PageContext pageContext) {
    }

    @PostMapping
    public ResponseEntity<SystemAssistantConversation> create(@RequestBody CreateRequest request) {
        var result = conversations.create(request.pageContext());
        return ResponseEntity.created(URI.create("/api/system-assistant/conversations/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public SystemAssistantConversation get(@PathVariable String id) {
        return conversations.get(id);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SystemAssistantConversation> send(@PathVariable String id, @Valid @RequestBody SystemAssistantMessageRequest request) {
        return ResponseEntity.accepted().body(conversations.send(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        conversations.remove(id);
        return ResponseEntity.noContent().build();
    }
}
