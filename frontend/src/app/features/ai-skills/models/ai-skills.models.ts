export type AiSkillState = 'DEFAULT' | 'CUSTOM';

export type AiSkillSummary = {
  name: string;
  description: string;
  lineCount: number;
  state: AiSkillState;
  restoreAvailable: boolean;
};

export type AiSkillCatalogResponse = {
  contract: 'ai-skills.catalog';
  version: number;
  mode: 'EDITABLE';
  source: 'COPILOT_RUNTIME';
  skillCount: number;
  defaultSkillCount: number;
  customSkillCount: number;
  skills: AiSkillSummary[];
};

export type AiSkillDetailResponse = {
  contract: 'ai-skills.detail';
  version: number;
  mode: 'EDITABLE';
  source: 'COPILOT_RUNTIME';
  name: string;
  description: string;
  lineCount: number;
  markdown: string;
  rawMarkdown: string;
  state: AiSkillState;
  restoreAvailable: boolean;
};

export type AiSkillUpdateRequest = {
  rawMarkdown: string;
};
