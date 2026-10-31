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
    @org.springframework.beans.factory.annotation.Value("${release-summary.enabled:true}")
    private boolean releaseSummaryEnabled = true;

    public UiConfigResponse currentConfig() {
        var configuredTitle = normalize(properties.getTitle());
        if (!StringUtils.hasText(configuredTitle)) {
            return new UiConfigResponse(
                    DEFAULT_TITLE,
                    null,
                    DEFAULT_TITLE,
                    platformSourceCodeProperties.getDefaultBranch(), releaseSummaryEnabled
            );
        }

        return new UiConfigResponse(
                configuredTitle,
                DEFAULT_TITLE,
                DEFAULT_TITLE,
                platformSourceCodeProperties.getDefaultBranch(), releaseSummaryEnabled
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
