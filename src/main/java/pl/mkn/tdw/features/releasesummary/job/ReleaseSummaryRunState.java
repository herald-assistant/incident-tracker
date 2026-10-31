package pl.mkn.tdw.features.releasesummary.job;

import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryAiContext;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService;
import pl.mkn.tdw.shared.ai.*;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import java.time.Instant;
import java.util.*;

final class ReleaseSummaryRunState {
    final String id;
    final ReleaseSummaryRequest request;
    final Instant createdAt;
    Instant updatedAt,completedAt;
    String status="QUEUED",error,errorCode,prompt,sessionId;
    AnalysisAiAuthRef auth;
    ReleaseSummaryScope scope;
    ReleaseSummaryEvidence evidence;
    ReleaseSummaryAiContext context;
    AnalysisReport report;
    ReleaseSummaryReportService.Result result;
    AnalysisAiUsage usage;
    final List<AnalysisJobStepResponse> steps=new ArrayList<>();
    final List<AnalysisEvidenceSection> toolEvidence=new ArrayList<>();
    final List<AnalysisAiActivityEvent> activities=new ArrayList<>();
    final List<AnalysisChatMessageResponse> messages=new ArrayList<>();
    ReleaseSummaryRunState(String id,ReleaseSummaryRequest request,AnalysisAiAuthRef auth) {
        this.id=id;this.request=request;this.auth=auth;createdAt=Instant.now();updatedAt=createdAt;
    }
    void step(String code,String label,String state,String message,int count) {
        var previous=steps.stream().filter(s->s.code().equals(code)).findFirst().orElse(null);
        steps.removeIf(s->s.code().equals(code));
        updatedAt=Instant.now();
        steps.add(new AnalysisJobStepResponse(code,label,code.equals("AI_REPORT")?"AI":"CONTEXT",state,message,count,
                previous!=null?previous.startedAt():updatedAt,state.equals("RUNNING")?null:updatedAt,List.of(),List.of(),usage));
    }
    void finish(String status,String code,String message) {
        this.status=status;this.errorCode=code;this.error=message;updatedAt=Instant.now();completedAt=updatedAt;
        for(int i=0;i<steps.size();i++) if(steps.get(i).status().equals("RUNNING")) {
            var old=steps.get(i);steps.set(i,new AnalysisJobStepResponse(old.code(),old.label(),old.phase(),
                    status.equals("COMPLETED")||status.equals("NO_CHANGES")?"COMPLETED":status,message,old.itemCount(),
                    old.startedAt(),completedAt,List.of(),List.of(),usage));
        }
    }
    ReleaseSummarySnapshot snapshot() {
        var chat=Set.of("COMPLETED","PARTIAL").contains(status)&&report!=null&&sessionId!=null;
        return new ReleaseSummarySnapshot(id,status,request,scope,evidence,errorCode,error,createdAt,updatedAt,completedAt,
                steps,evidence!=null?evidence.sections():List.of(),toolEvidence,activities,List.of(),prompt,report,result,usage,messages,
                new ReleaseSummarySnapshot.ChatAvailability(chat,chat?"AVAILABLE":"UNAVAILABLE",chat?"Możesz kontynuować analizę.":"Kontynuacja wymaga zapisanego raportu i sesji AI."),
                !status.equals("RUNNING")&&!status.equals("QUEUED"));
    }
}

