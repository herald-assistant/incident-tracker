package pl.mkn.tdw.features.uxinspector.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.frontendcatalog.FrontendApplicationCatalogService;
import pl.mkn.tdw.frontendcatalog.FrontendViewCatalogService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

@Service
@RequiredArgsConstructor
public class UxInspectorInputOptionsService {
    public static final String FEATURE_ID = "ux-inspector";
    private final FrontendApplicationCatalogService applicationCatalogService;
    private final FrontendViewCatalogService viewCatalogService;

    public UxInspectorInputOptionsResponse inputOptions() {
        var catalog = applicationCatalogService.loadCatalog();
        return new UxInspectorInputOptionsResponse(FEATURE_ID,
                catalog.frontends().stream().map(value -> new UxInspectorInputOptionsResponse.SystemOption(
                        value.systemId(), value.label(), value.summary(), value.defaultBranch())).toList(),
                catalog.configurationFindings().stream().map(value -> new UxInspectorInputOptionsResponse.ConfigurationFinding(
                        value.severity(), value.code(), value.message(), value.entityType(), value.entityId())).toList());
    }

    public UxInspectorViewCatalogResponse views(String systemId, String branch, boolean refreshCache) {
        if (!StringUtils.hasText(systemId) || systemId.trim().length() > 160
                || !StringUtils.hasText(branch) || branch.trim().length() > 255) {
            throw new UxInspectorContextException("UX_INSPECTOR_VIEW_SCOPE_INVALID", UserFacingErrorType.BAD_REQUEST,
                    "Frontend system and branch/ref are required within the supported limits.");
        }
        if (applicationCatalogService.loadCatalog().findFrontend(systemId.trim()).isEmpty()) {
            throw new UxInspectorContextException("UX_INSPECTOR_FRONTEND_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                    "Selected frontend is not registered for source analysis.");
        }
        pl.mkn.tdw.frontendcatalog.FrontendViewCatalog catalog;
        try {
            catalog = viewCatalogService.loadCatalog(systemId.trim(), branch.trim(), refreshCache);
        } catch (GitLabFrontendDiscoveryException exception) {
            if ("FRONTEND_REF_NOT_FOUND".equals(exception.code())) {
                throw new UxInspectorContextException("UX_INSPECTOR_SOURCE_REF_NOT_FOUND", UserFacingErrorType.NOT_FOUND,
                        "Selected frontend branch or ref does not exist.");
            }
            throw new UxInspectorContextException("UX_INSPECTOR_VIEW_CATALOG_UNAVAILABLE",
                    UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Frontend views could not be prepared for the selected source ref.");
        } catch (IllegalArgumentException exception) {
            throw new UxInspectorContextException("UX_INSPECTOR_FRONTEND_CATALOG_CHANGED",
                    UserFacingErrorType.CONFLICT,
                    "Frontend registration changed while loading views. Reload input options.");
        }
        return new UxInspectorViewCatalogResponse(catalog.systemId(), catalog.systemLabel(),
                new UxInspectorViewCatalogResponse.SourceRevision(catalog.sourceRevision().branch(),
                        catalog.sourceRevision().revision()), catalog.status().name(),
                catalog.views().stream().map(value -> new UxInspectorViewCatalogResponse.ViewOption(
                        value.viewId(), value.label(), value.routePattern(), value.componentSelectors(),
                        value.status(), value.limitations())).toList(),
                catalog.diagnostics().stream().map(value -> new UxInspectorViewCatalogResponse.Diagnostic(
                        value.severity(), value.code(), value.message(), value.sourcePath())).toList(), catalog.limitations());
    }
}
