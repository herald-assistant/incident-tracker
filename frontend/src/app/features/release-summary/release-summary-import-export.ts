import { ReleaseSummarySnapshot } from './release-summary.models';
import { normalizeAnalysisReport } from '../../core/utils/analysis-import-export.utils';
const schema='tdw.release-summary-export';
const contract='release-summary-analyst-report-v1';
const keys=['jobId','status','request','comparison','evidence','errorCode','error','createdAt','updatedAt','completedAt','steps',
  'contextSections','toolEvidenceSections','aiActivityEvents','toolFeedback','preparedPrompt','report','result','usage','chatMessages','chatAvailability','exportAvailable'];
function object(value:unknown):Record<string,any> {
  if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('Niepoprawny obiekt eksportu Release summary.');
  return value as Record<string,any>;
}
export function releaseSummaryExport(job:ReleaseSummarySnapshot) {
  const publicJob=Object.fromEntries(keys.map(key=>[key,(job as unknown as Record<string,unknown>)[key]]));
  return {schema,version:1,exportedAt:new Date().toISOString(),payload:{type:'release-summary-analysis',resultContract:contract,job:publicJob}};
}
export function parseReleaseSummaryImport(value:unknown):ReleaseSummarySnapshot {
  const envelope=object(value),payload=object(envelope['payload']),job=object(payload['job']),request=object(job['request']);
  if(envelope['schema']!==schema||envelope['version']!==1||payload['type']!=='release-summary-analysis'||payload['resultContract']!==contract||
    request['mode']!=='LIQUIBASE'||typeof request['systemId']!=='string'||!request['systemId']||
    typeof job['jobId']!=='string'||!['COMPLETED','PARTIAL','NO_CHANGES','BLOCKED','FAILED','INTERRUPTED'].includes(job['status']))
    throw new Error('Nieobsługiwany format, wersja lub tryb Release summary.');
  for(const field of ['baselineRef','targetRef']) {
    const ref=object(request[field]);
    if(!['BRANCH','TAG','COMMIT'].includes(ref['kind'])||typeof ref['name']!=='string'||!ref['name'])
      throw new Error('Brak poprawnych refów porównania.');
  }
  for(const field of ['steps','contextSections','toolEvidenceSections','aiActivityEvents','toolFeedback','chatMessages'])
    if(!Array.isArray(job[field])||job[field].some((item:unknown)=>!item||typeof item!=='object'||Array.isArray(item)))
      throw new Error('Niepoprawne dane przebiegu analizy.');
  if(!Number.isFinite(Date.parse(job['createdAt']))||!Number.isFinite(Date.parse(job['updatedAt'])))
    throw new Error('Niepoprawne daty analizy.');
  if(job['result']) {
    const result=object(job['result']);
    if(result['resultContract']!==contract||!Array.isArray(result['coverage'])||!Array.isArray(result['visibilityLimits'])||
      ['expectedUnits','documentedUnits','expectedElements','documentedElements'].some(key=>!Number.isInteger(result[key])||result[key]<0))
      throw new Error('Niepoprawny kontrakt wyniku.');
  }
  if(job['comparison']) {
    const comparison=object(job['comparison']);
    if(!Array.isArray(comparison['repositories'])||!Array.isArray(comparison['entities'])||
      comparison['repositories'].some((repo:unknown)=>{const r=object(repo);return !Array.isArray(r['prefixes'])||
        !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(r['baselineCommit'])||!/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(r['targetCommit']);}))
      throw new Error('Niepoprawny manifest przypiętych rewizji.');
  }
  if(job['evidence']) for(const field of ['units','sources','gaps','sections'])
    if(!Array.isArray(object(job['evidence'])[field]))throw new Error('Niepoprawne evidence Liquibase.');
  if(job['chatMessages'].some((m:Record<string,unknown>)=>m['status']==='IN_PROGRESS'))
    throw new Error('Eksport zawiera niezakończoną odpowiedź AI.');
  const result=Object.fromEntries(keys.map(key=>[key,job[key]])) as unknown as ReleaseSummarySnapshot;
  result.request={systemId:request['systemId'],mode:'LIQUIBASE',baselineRef:request['baselineRef'],targetRef:request['targetRef'],
    model:request['model'],reasoningEffort:request['reasoningEffort']};
  result.report=job['report']?normalizeAnalysisReport(job['report']):null;
  if(result.report) {
    for(const section of result.report.sections) {
      if(!['OVERVIEW','TABLE_LIST','APPENDIX'].includes(section.id)&&!section.id.startsWith('DB_DETAIL_'))
        throw new Error('Raport nie odpowiada aktualnemu układowi sekcji Release summary.');
      if(section.order==null||!Number.isInteger(section.order)||section.order<1)
        throw new Error('Niepoprawna kolejność sekcji raportu.');
      if(section.id.startsWith('DB_DETAIL_')&&!/(?:^|\n)\s*\|\s*Obiekt\s*\|\s*Zmiana\s*\|\s*Opis funkcjonalny\s*\|\s*(?:\n|$)/.test(section.markdown))
        throw new Error('Szczegóły tabeli wymagają kolumn: Obiekt, Zmiana, Opis funkcjonalny.');
    }
  }
  result.chatAvailability={available:false,code:'IMPORTED_READ_ONLY',message:'Import pliku jest tylko do odczytu i nie odtwarza sesji AI.'};
  result.exportAvailable=true;
  return result;
}
