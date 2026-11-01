package pl.mkn.tdw.features.releasesummary.context;

import org.springframework.web.client.RestClientResponseException;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryTreeException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Keeps source location without exposing remote response bodies or exception messages. */
final class ReleaseSummarySourceFailure extends RuntimeException {
    final String side;
    final String path;
    private final String operation;

    ReleaseSummarySourceFailure(String side, String path, String operation, RuntimeException cause) {
        super(operation, cause);
        this.side = side;
        this.path = path;
        this.operation = operation;
    }

    String detail() {
        var causes = causes();
        for (var cause : causes) {
            if (cause instanceof RestClientResponseException response) {
                return operation + ": GitLab zwrócił HTTP " + response.getStatusCode().value() + ".";
            }
            if (cause instanceof GitLabRepositoryTreeException tree) {
                return operation + ": GitLab zwrócił HTTP " + tree.statusCode() + ".";
            }
        }
        if (causes.stream().anyMatch(c -> c instanceof SocketTimeoutException || c instanceof HttpTimeoutException)) {
            return operation + ": przekroczono czas oczekiwania na GitLab.";
        }
        if (causes.stream().anyMatch(SSLException.class::isInstance)) {
            return operation + ": nie udało się nawiązać poprawnego połączenia TLS z GitLabem.";
        }
        if (causes.stream().anyMatch(IOException.class::isInstance)) {
            return operation + ": błąd połączenia lub odczytu danych z GitLaba.";
        }
        return operation + ": nie można potwierdzić poprawności i kompletności źródła.";
    }

    String causeTypes() {
        return String.join(" -> ", causes().stream().map(c -> c.getClass().getSimpleName()).toList());
    }

    private List<Throwable> causes() {
        var result = new ArrayList<Throwable>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var cause = getCause(); cause != null && seen.add(cause); cause = cause.getCause()) {
            result.add(cause);
        }
        return result;
    }
}
