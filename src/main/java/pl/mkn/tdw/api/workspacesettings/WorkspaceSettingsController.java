package pl.mkn.tdw.api.workspacesettings;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsResponse;
import static pl.mkn.tdw.api.workspacesettings.WorkspaceSettingsDtos.WorkspaceSettingsUpdateRequest;

@RestController
@RequestMapping("/api/workspace/settings")
@RequiredArgsConstructor
public class WorkspaceSettingsController {

    private final WorkspaceSettingsService workspaceSettingsService;

    @GetMapping
    public WorkspaceSettingsResponse currentSettings() {
        return workspaceSettingsService.currentSettings();
    }

    @PutMapping
    public WorkspaceSettingsResponse saveSettings(@RequestBody WorkspaceSettingsUpdateRequest request) {
        return workspaceSettingsService.saveSettings(request);
    }
}
