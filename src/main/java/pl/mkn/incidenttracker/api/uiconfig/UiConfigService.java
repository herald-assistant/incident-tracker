package pl.mkn.incidenttracker.api.uiconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UiConfigService {

    static final String DEFAULT_TITLE = "Team Delivery Workspace";

    private final UiConfigProperties properties;

    public UiConfigResponse currentConfig() {
        var configuredTitle = normalize(properties.getTitle());
        if (!StringUtils.hasText(configuredTitle)) {
            return new UiConfigResponse(DEFAULT_TITLE, null, DEFAULT_TITLE);
        }

        return new UiConfigResponse(configuredTitle, DEFAULT_TITLE, DEFAULT_TITLE);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
