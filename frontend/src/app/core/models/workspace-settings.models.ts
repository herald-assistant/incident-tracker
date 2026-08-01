export type WorkspaceSettingsSource = 'APPLICATION_PROPERTIES' | 'WORKSPACE_SETTINGS';

export interface WorkspaceSettingsResponse {
  workspaceEnabled: boolean;
  settingsPath: string;
  values: WorkspaceSettingsValues;
}

export interface WorkspaceSettingsValues {
  appUi: WorkspaceSettingsAppUi;
  copilot: WorkspaceSettingsCopilot;
  jira: WorkspaceSettingsJira;
  gitLab: WorkspaceSettingsGitLab;
  runtimeConfigGitLab: WorkspaceSettingsRuntimeConfigGitLab;
  elasticsearch: WorkspaceSettingsElasticsearch;
  dynatrace: WorkspaceSettingsDynatrace;
}

export interface WorkspaceSettingsAppUi {
  title: WorkspaceSettingsField;
}

export interface WorkspaceSettingsCopilot {
  localGithubToken: WorkspaceSettingsField;
}

export interface WorkspaceSettingsJira {
  baseUrl: WorkspaceSettingsField;
  token: WorkspaceSettingsField;
}

export interface WorkspaceSettingsGitLab {
  baseUrl: WorkspaceSettingsField;
  group: WorkspaceSettingsField;
  token: WorkspaceSettingsField;
}

export interface WorkspaceSettingsRuntimeConfigGitLab {
  baseUrl: WorkspaceSettingsField;
  token: WorkspaceSettingsField;
}

export interface WorkspaceSettingsElasticsearch {
  baseUrl: WorkspaceSettingsField;
  kibanaSpaceId: WorkspaceSettingsField;
  indexPattern: WorkspaceSettingsField;
  authorizationHeader: WorkspaceSettingsField;
}

export interface WorkspaceSettingsDynatrace {
  baseUrl: WorkspaceSettingsField;
  apiToken: WorkspaceSettingsField;
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
  copilot: {
    localGithubToken: string;
  };
  jira: {
    baseUrl: string;
    token: string;
  };
  gitLab: {
    baseUrl: string;
    group: string;
    token: string;
  };
  runtimeConfigGitLab: {
    baseUrl: string;
    token: string;
  };
  elasticsearch: {
    baseUrl: string;
    kibanaSpaceId: string;
    indexPattern: string;
    authorizationHeader: string;
  };
  dynatrace: {
    baseUrl: string;
    apiToken: string;
  };
}
