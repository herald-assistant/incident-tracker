package pl.mkn.incidenttracker.features.flowexplorer.job.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FlowExplorerJobNotFoundException extends RuntimeException {

    public FlowExplorerJobNotFoundException(String jobId) {
        super("Flow Explorer job not found: " + jobId);
    }
}

