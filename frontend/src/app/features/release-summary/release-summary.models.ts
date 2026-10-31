import { GitLabBranchesResponse } from '../../core/services/gitlab-system-branches-api.service';
export type ReleaseRefKind = 'BRANCH'|'TAG'|'COMMIT';
export interface ReleaseRef { kind: ReleaseRefKind; name:string }
export interface ReleaseSummaryRequest {
  systemId:string; mode:'LIQUIBASE'; baselineRef:ReleaseRef; targetRef:ReleaseRef;
  model?:string; reasoningEffort?:string;
}
export interface ReleaseSummarySnapshot {
  jobId:string; status:string; request:ReleaseSummaryRequest;
  comparison: {systemName:string; repositories:{repositoryId:string; baselineCommit:string; targetCommit:string}[]}|null;
  error:string|null; createdAt:string;
}
export interface ReleaseRefOptions extends GitLabBranchesResponse { nextPage:number|null }
export function releaseRef(value:string):ReleaseRef {
  if(value.startsWith('refs/heads/')) return {kind:'BRANCH',name:value.slice(11)};
  if(value.startsWith('refs/tags/')) return {kind:'TAG',name:value.slice(10)};
  return {kind:'COMMIT',name:value};
}

