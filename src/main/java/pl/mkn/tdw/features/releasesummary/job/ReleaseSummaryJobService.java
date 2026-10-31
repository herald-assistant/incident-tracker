package pl.mkn.tdw.features.releasesummary.job;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ReleaseSummaryJobService {
    private final ReleaseSummaryCatalog catalog;
    private final TaskExecutor applicationTaskExecutor;
    private final Map<String,Snapshot> jobs=new ConcurrentHashMap<>();
    public record Snapshot(String jobId,String status,ReleaseSummaryRequest request,ReleaseSummaryScope comparison,
                           String error, Instant createdAt) {}
    public Snapshot start(ReleaseSummaryRequest request) {
        var id=UUID.randomUUID().toString();
        var queued=new Snapshot(id,"QUEUED",request,null,null,Instant.now());
        jobs.put(id,queued);
        applicationTaskExecutor.execute(() -> {
            jobs.put(id,new Snapshot(id,"RUNNING",request,null,null,queued.createdAt()));
            try {
                var scope=catalog.pin(request,catalog.capture());
                jobs.put(id,new Snapshot(id,"COMPLETED",request,scope,null,queued.createdAt()));
            } catch(RuntimeException e) {
                jobs.put(id,new Snapshot(id,"BLOCKED",request,null,e instanceof ReleaseSummaryException?e.getMessage():"Nie udało się przygotować porównania.",queued.createdAt()));
            }
        });
        return queued;
    }
    public Snapshot get(String id) {
        var value=jobs.get(id);
        if(value==null) throw new ReleaseSummaryException("JOB_NOT_FOUND","Nie znaleziono analizy.");
        return value;
    }
}

