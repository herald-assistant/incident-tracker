package pl.mkn.tdw.api.uiconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.common.PlatformSourceCodeProperties;

@Service
@RequiredArgsConstructor
public class UiConfigService {

    static final String DEFAULT_TITLE = "Team Delivery Workspace";

    private final UiConfigProperties properties;
    private final PlatformSourceCodeProperties platformSourceCodeProperties;

    public UiConfigResponse currentConfig() {
        var configuredTitle = normalize(properties.getTitle());
        if (!StringUtils.hasText(configuredTitle)) {
            return new UiConfigResponse(
                    DEFAULT_TITLE,
                    null,
                    DEFAULT_TITLE,
                    platformSourceCodeProperties.getDefaultBranch()
            );
        }

        return new UiConfigResponse(
                configuredTitle,
                DEFAULT_TITLE,
                DEFAULT_TITLE,
                platformSourceCodeProperties.getDefaultBranch()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
