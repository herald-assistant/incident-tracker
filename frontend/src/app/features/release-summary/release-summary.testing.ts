import { ReleaseSummarySnapshot } from './release-summary.models';
import { AnalysisReport } from '../../core/models/analysis.models';
export function releaseReport():AnalysisReport {
  const meta={references:[],visibilityLimits:[],openQuestions:[],gaps:[],confidence:'',warnings:[]};
  return {reportId:'crm-report',header:'Zmiany bazodanowe we wdrożeniu',subHeader:'CRM',markdownSummary:'',meta,
    sections:[
      {id:'DB_DETAIL_customer',title:'customer',order:3,
        markdown:'| Obiekt | Zmiana | Opis funkcjonalny |\n| --- | --- | --- |\n| Kolumna customer.label | Dodanie. typ: varchar(40); NOT NULL; wartość domyślna: general | Grupuje klientów przy wyszukiwaniu. |',
        meta:{...meta,references:[{type:'source',label:'Definicja kolumny',target:'https://gitlab.example.com/CRM/customer/-/blob/'+'b'.repeat(40)+'/db/customer.xml',description:'Typ, NULL i wartość domyślna.'}]}},
      {id:'APPENDIX',title:'Appendix',order:4,markdown:'Sprawdzić zapis etykiety klienta po wdrożeniu.',meta},
      {id:'OVERVIEW',title:'Podsumowanie',order:1,markdown:'Etykiety ułatwiają wyszukiwanie klientów.',meta},
      {id:'TABLE_LIST',title:'Lista tabel',order:2,markdown:'| Tabela | Zakres zmian |\n| --- | --- |\n| customer | Dodanie etykiety klienta. |',meta}
    ]};
}
export function releaseSnapshot(status = 'COMPLETED'): ReleaseSummarySnapshot {
  return {jobId:'crm-run',status,request:{systemId:'crm',mode:'LIQUIBASE',baselineRef:{kind:'BRANCH',name:'current'},targetRef:{kind:'TAG',name:'next'}},
    comparison:null,evidence:null,errorCode:null,error:null,createdAt:'2026-10-31T10:00:00Z',updatedAt:'2026-10-31T10:00:00Z',completedAt:null,
    steps:[],contextSections:[],toolEvidenceSections:[],aiActivityEvents:[],toolFeedback:[],preparedPrompt:'CRM full prompt',report:null,result:null,usage:null,chatMessages:[],
    chatAvailability:{available:false,code:'UNAVAILABLE',message:'Brak sesji'},exportAvailable:true};
}
