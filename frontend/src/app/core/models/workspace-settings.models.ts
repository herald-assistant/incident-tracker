export type WorkspaceSettingsSource = 'APPLICATION_PROPERTIES' | 'WORKSPACE_SETTINGS';

export interface WorkspaceSettingsResponse {
  workspaceEnabled: boolean;
  settingsPath: string;
  values: WorkspaceSettingsValues;
}

export interface WorkspaceSettingsValues {
  appUi: WorkspaceSettingsAppUi;
  gitLab: WorkspaceSettingsGitLab;
  elasticsearch: WorkspaceSettingsElasticsearch;
}

export interface WorkspaceSettingsAppUi {
  title: WorkspaceSettingsField;
}

export interface WorkspaceSettingsGitLab {
  baseUrl: WorkspaceSettingsField;
  group: WorkspaceSettingsField;
  token: WorkspaceSettingsField;
}

export interface WorkspaceSettingsElasticsearch {
  baseUrl: WorkspaceSettingsField;
  kibanaSpaceId: WorkspaceSettingsField;
  indexPattern: WorkspaceSettingsField;
  authorizationHeader: WorkspaceSettingsField;
}

export interface WorkspaceSettingsField {
  propertyKey: string;
  value: string;
  applicationValue: string;
  workspaceValue: string | null;
  source: WorkspaceSettingsSource;
  secret: boolean;
}

export interface WorkspaceSettingsUpdateRequest {
  appUi: {
    title: string;
  };
  gitLab: {
    baseUrl: string;
    group: string;
    token: string;
  };
  elasticsearch: {
    baseUrl: string;
    kibanaSpaceId: string;
    indexPattern: string;
    authorizationHeader: string;
  };
}
