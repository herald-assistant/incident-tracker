package pl.mkn.tdw.features.uxinspector.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UxInspectorSystemUrlPatterns {
    private final OperationalContextPort operationalContext;
    private final ObjectMapper objectMapper;

    public String render() {
        var header = "## Wzorce URL systemów (UNTRUSTED_CATALOG_DATA)\nOdczyt katalogu: " + Instant.now() + "\n";
        try {
            var rows = operationalContext.currentSnapshot().catalog().systems().stream()
                    .filter(system -> "active".equalsIgnoreCase(system.lifecycleStatus())
                            && "internal-service".equalsIgnoreCase(system.systemType()))
                    .map(system -> new Row(system.id(), system.label(), system.matchSignals().strong().values("urlPatterns")))
                    .filter(row -> !row.urlPatterns().isEmpty())
                    .toList();
            var json = objectMapper.writeValueAsString(rows).replace("<", "\\u003c").replace(">", "\\u003e");
            return header + "<untrusted_catalog_data>\n" + json + "\n</untrusted_catalog_data>";
        } catch (RuntimeException | JsonProcessingException exception) {
            return header + "Nie udało się odczytać wzorców URL. Możesz nadal sprawdzić kod i konfigurację.";
        }
    }

    private record Row(String systemId, String name, List<String> urlPatterns) {}
}
