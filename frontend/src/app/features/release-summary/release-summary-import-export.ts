import { ReleaseSummarySnapshot } from './release-summary.models';
import { normalizeAnalysisReport } from '../../core/utils/analysis-import-export.utils';
const schema='tdw.release-summary-export';
const contract='release-summary-diff-report-v1';
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
  const range=object(request['range']),selection=object(range['selection']);
  const validDate=(value:unknown): value is string => typeof value==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0,10)===value;
  if(typeof selection['branch']!=='string'||!selection['branch'].startsWith('refs/heads/')||selection['branch'].length<=11||
    !validDate(selection['dateFrom'])||!validDate(selection['dateTo'])||selection['dateFrom']>selection['dateTo']||
    typeof selection['timeZone']!=='string'||!selection['timeZone']||typeof range['repositoryId']!=='string'||!range['repositoryId']||
    'baselineRef' in request||'targetRef' in request)
    throw new Error('Brak poprawnego zakresu dat i brancha.');
  try { new Intl.DateTimeFormat('pl-PL',{timeZone:selection['timeZone']}).format(); }
  catch { throw new Error('Niepoprawna strefa czasowa zakresu.'); }
  for(const side of ['baseline','target']) {
    const commit=object(range[side]);
    if(typeof commit['id']!=='string'||!/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(commit['id'])||
      typeof commit['title']!=='string'||typeof commit['committedAt']!=='string'||!Number.isFinite(Date.parse(commit['committedAt'])))
      throw new Error('Brak poprawnych commitów porównania.');
  }
  for(const field of ['steps','contextSections','toolEvidenceSections','aiActivityEvents','toolFeedback','chatMessages'])
    if(!Array.isArray(job[field])||job[field].some((item:unknown)=>!item||typeof item!=='object'||Array.isArray(item)))
      throw new Error('Niepoprawne dane przebiegu analizy.');
  if(!Number.isFinite(Date.parse(job['createdAt']))||!Number.isFinite(Date.parse(job['updatedAt'])))
    throw new Error('Niepoprawne daty analizy.');
  if(job['result']) {
    const result=object(job['result']);
    if(result['resultContract']!==contract||typeof result['changeListComplete']!=='boolean'||
      !['AI_ASSESSED','NOT_APPLICABLE'].includes(result['semanticCoverage'])||!Array.isArray(result['visibilityLimits'])||
      result['visibilityLimits'].some((limit:unknown)=>typeof limit!=='string')||'coverage' in result||'expectedElements' in result||
      ['changedFiles','documentedTables','readSources'].some(key=>!Number.isInteger(result[key])||result[key]<0))
      throw new Error('Niepoprawny kontrakt wyniku.');
  }
  if(job['comparison']) {
    const comparison=object(job['comparison']);
    if(!Array.isArray(comparison['repositories'])||comparison['repositories'].length!==1||!Array.isArray(comparison['entities'])||
      comparison['repositories'].some((repo:unknown)=>{const r=object(repo);return r['repositoryId']!==range['repositoryId']||r['baselineCommit']!==range['baseline']['id']||
        r['targetCommit']!==range['target']['id']||!Array.isArray(r['prefixes'])||
        !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(r['baselineCommit'])||!/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(r['targetCommit']);}))
      throw new Error('Niepoprawny manifest przypiętych rewizji.');
  }
  if(job['evidence']) {
    const evidence=object(job['evidence']);
    if(typeof evidence['changeListComplete']!=='boolean'||!Array.isArray(evidence['changes'])||!Array.isArray(evidence['gaps'])||
      ['units','sources','scannedFiles','sourceComplete','sections'].some(key=>key in evidence))
      throw new Error('Niepoprawny manifest zmian Git.');
    for(const entry of evidence['changes']) {
      const file=object(entry),change=object(file['change']);
      if(typeof file['repositoryId']!=='string'||!file['repositoryId']||typeof file['project']!=='string'||!file['project']||
        typeof change['patchComplete']!=='boolean'||(change['patch']!==null&&typeof change['patch']!=='string')||
        (!change['oldPath']&&!change['newPath']))throw new Error('Niepoprawna zmiana pliku.');
      for(const side of ['old','new']) {
        const path=change[side+'Path'];
        if(path===null) {if(change[side+'BlobId']!==null||change[side+'Mode']!==null)throw new Error('Niepoprawna strona zmiany.');}
        else if(typeof path!=='string'||!path||path.startsWith('/')||path.includes('\\')||path.split('/').some(part=>['','..','.'].includes(part))||
          !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(change[side+'BlobId'])||!['100644','100755','120000','160000'].includes(change[side+'Mode']))
          throw new Error('Niepoprawna sciezka lub obiekt Git.');
      }
    }
  }
  if(job['chatMessages'].some((m:Record<string,unknown>)=>m['status']==='IN_PROGRESS'))
    throw new Error('Eksport zawiera niezakończoną odpowiedź AI.');
  const result=Object.fromEntries(keys.map(key=>[key,job[key]])) as unknown as ReleaseSummarySnapshot;
  result.request={systemId:request['systemId'],mode:'LIQUIBASE',range:{selection:{branch:selection['branch'],dateFrom:selection['dateFrom'],dateTo:selection['dateTo'],timeZone:selection['timeZone']},
      repositoryId:range['repositoryId'],baseline:{id:range['baseline']['id'],committedAt:range['baseline']['committedAt'],title:range['baseline']['title']},
      target:{id:range['target']['id'],committedAt:range['target']['committedAt'],title:range['target']['title']}},
    model:request['model'],reasoningEffort:request['reasoningEffort']};
  result.report=job['report']?normalizeAnalysisReport(job['report']):null;
  if(result.report) {
    for(const section of result.report.sections) {
      if(!['OVERVIEW','TABLE_LIST','APPENDIX'].includes(section.id)&&!/^DB_DETAIL_[A-Za-z0-9_]{1,100}$/.test(section.id))
        throw new Error('Raport nie odpowiada aktualnemu układowi sekcji Release summary.');
      if(section.order==null||!Number.isInteger(section.order)||section.order<1)
        throw new Error('Niepoprawna kolejność sekcji raportu.');
      if(section.id==='OVERVIEW'&&section.order!==1||section.id==='TABLE_LIST'&&section.order!==2||
        section.id==='APPENDIX'&&section.order!==1000000||section.id.startsWith('DB_DETAIL_')&&(section.order<3||section.order>=1000000))
        throw new Error('Kolejnosc nie odpowiada aktualnemu ukladowi raportu.');
      if(section.id.startsWith('DB_DETAIL_')&&!/(?:^|\n)\s*\|\s*Obiekt\s*\|\s*Zmiana\s*\|\s*Opis funkcjonalny\s*\|\s*(?:\n|$)/.test(section.markdown))
        throw new Error('Szczegóły tabeli wymagają kolumn: Obiekt, Zmiana, Opis funkcjonalny.');
    }
  }
  result.chatAvailability={available:false,code:'IMPORTED_READ_ONLY',message:'Import pliku jest tylko do odczytu i nie odtwarza sesji AI.'};
  result.exportAvailable=true;
  return result;
}
