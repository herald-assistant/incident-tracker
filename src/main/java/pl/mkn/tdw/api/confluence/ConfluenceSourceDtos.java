package pl.mkn.tdw.api.confluence;

import jakarta.validation.constraints.NotBlank;

public final class ConfluenceSourceDtos {

    private ConfluenceSourceDtos() {
    }

    public record ConfluencePageContentRequest(
            @NotBlank String pageUrl
    ) {
    }
}
