package pl.mkn.tdw.api.confluence;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.confluence.ConfluencePageContent;
import pl.mkn.tdw.integrations.confluence.ConfluencePagePort;

import static pl.mkn.tdw.api.confluence.ConfluenceSourceDtos.ConfluencePageContentRequest;

@RestController
@RequestMapping("/api/confluence")
@RequiredArgsConstructor
public class ConfluenceSourceController {

    private final ConfluencePagePort confluencePagePort;

    @PostMapping("/page/content")
    public ConfluencePageContent getPageContent(@Valid @RequestBody ConfluencePageContentRequest request) {
        try {
            return confluencePagePort.getPageContent(request.pageUrl().trim())
                    .orElseThrow(() -> ConfluenceSourceApiException.badRequest(
                            "pageUrl must match the configured analysis.confluence.url-pattern."
                    ));
        }
        catch (ConfluenceSourceApiException exception) {
            throw exception;
        }
        catch (IllegalArgumentException exception) {
            throw ConfluenceSourceApiException.badRequest(safeMessage(exception));
        }
        catch (IllegalStateException exception) {
            throw ConfluenceSourceApiException.unavailable(safeMessage(exception));
        }
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
