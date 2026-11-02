import {parseReleaseSummaryImport,releaseSummaryExport} from './release-summary-import-export';
import {releaseReport,releaseSnapshot} from './release-summary.testing';
import {terminalReleaseSummary} from './release-summary.models';
describe('Release summary file contract',()=>{
  it('preserves complete pinned sources and full nested entity payloads through JSON round-trip',()=>{
    const job=releaseSnapshot('PARTIAL');
    job.comparison={systemId:'crm',systemName:'CRM',group:'CRM',catalogDigest:'digest',
      entities:[{type:'system',id:'crm',payload:{name:'CRM',custom:{nested:['pełne dane']}}}],
      repositories:[{repositoryId:'customer',project:'customer',prefixes:['db'],baselineRef:'refs/heads/current',baselineCommit:'a'.repeat(40),targetRef:'refs/tags/next',targetCommit:'b'.repeat(40)}]};
    job.evidence={changeListComplete:true,changes:[{repositoryId:'customer',project:'customer',change:{oldPath:null,newPath:'db/customer.sql',
      oldBlobId:null,newBlobId:'c'.repeat(40),oldMode:null,newMode:'100644',patch:'@@ -0,0 +1 @@\n+alter table customer add label text;',patchComplete:true}}],gaps:[]};
    const imported=parseReleaseSummaryImport(JSON.parse(JSON.stringify(releaseSummaryExport(job))));
    expect(imported.comparison).toEqual(job.comparison);expect(imported.evidence).toEqual(job.evidence);
    expect(imported.chatAvailability.available).toBe(false);expect(imported.status).toBe('PARTIAL');
  });
  it('rejects other schemas, versions, result contracts, modes and broken SHAs',()=>{
    for(const mutate of [
      (e:any)=>e.version=2,(e:any)=>e.schema='tdw.other',(e:any)=>e.payload.resultContract='unknown',
      (e:any)=>e.payload.job.request.mode='API',(e:any)=>e.payload.job.request.baselineRef.kind='OTHER',
      (e:any)=>e.payload.job.comparison={entities:[],repositories:[{prefixes:[],baselineCommit:'short',targetCommit:'b'.repeat(40)}]}
    ]) {const envelope=releaseSummaryExport(releaseSnapshot());mutate(envelope);expect(()=>parseReleaseSummaryImport(envelope)).toThrow();}
  });
  it('rejects the previous scan format and invented semantic coverage even under a current envelope',()=>{
    for(const old of [
      {evidence:{scannedFiles:2,sourceComplete:true,units:[],sources:[],gaps:[],sections:[]}},
      {result:{resultContract:'release-summary-diff-report-v1',expectedElements:1,documentedElements:1,coverage:[],visibilityLimits:[]}}
    ]) {
      const envelope=releaseSummaryExport(releaseSnapshot());Object.assign(envelope.payload.job,old);
      expect(()=>parseReleaseSummaryImport(envelope)).toThrow();
    }
  });
  it('does not export private runtime keys and keeps polling while an answer is pending',()=>{
    const job=releaseSnapshot();Object.assign(job,{sessionId:'private-session',auth:{token:'private-token'},storeSnapshot:{private:true}});
    expect(JSON.stringify(releaseSummaryExport(job))).not.toContain('private-');
    job.chatMessages=[{id:'answer',role:'ASSISTANT',status:'IN_PROGRESS',content:'',createdAt:job.createdAt,updatedAt:job.updatedAt,
      completedAt:'',errorCode:'',errorMessage:'',toolEvidenceSections:[],aiActivityEvents:[],toolFeedback:[],prompt:'',usage:null}];
    expect(terminalReleaseSummary(job)).toBe(false);expect(()=>parseReleaseSummaryImport(releaseSummaryExport(job))).toThrow();
  });
  it('round-trips table details and metadata and rejects obsolete report layouts',()=>{
    const job=releaseSnapshot();job.report=releaseReport();
    const imported=parseReleaseSummaryImport(JSON.parse(JSON.stringify(releaseSummaryExport(job))));
    expect(imported.report).toMatchObject(job.report!);
    expect(imported.report?.sections[0].markdown).toContain('typ: varchar(40); NOT NULL; wartość domyślna: general');
    for(const mutate of [
      (report:any)=>report.sections[0].id='CHANGE_INDEX',
      (report:any)=>report.sections[0].markdown='| Obiekt | Zmiana techniczna | Znaczenie funkcjonalne | Źródła |\n| --- | --- | --- | --- |\n| label | Dodanie | Etykieta | Plik |',
      (report:any)=>report.sections[0].order=0
    ]) {job.report=releaseReport();mutate(job.report);expect(()=>parseReleaseSummaryImport(releaseSummaryExport(job))).toThrow();}
  });
});
