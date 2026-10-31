import { ReleaseSummarySnapshot } from './release-summary.models';
export function releaseSnapshot(status = 'COMPLETED'): ReleaseSummarySnapshot {
  return {jobId:'crm-run',status,request:{systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'current'},targetRef:{kind:'TAG',name:'next'}},
    comparison:null,evidence:null,errorCode:null,error:null,createdAt:'2026-10-31T10:00:00Z',updatedAt:'2026-10-31T10:00:00Z',completedAt:null,
    steps:[],contextSections:[],toolEvidenceSections:[],aiActivityEvents:[],toolFeedback:[],preparedPrompt:'CRM full prompt',report:null,result:null,usage:null,chatMessages:[],
    chatAvailability:{available:false,code:'UNAVAILABLE',message:'Brak sesji'},exportAvailable:true};
}
