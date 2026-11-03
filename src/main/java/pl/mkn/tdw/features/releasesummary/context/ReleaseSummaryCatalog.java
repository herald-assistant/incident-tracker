package pl.mkn.tdw.features.releasesummary.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRefPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryHistoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRevisionPort;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.common.GitLabPathUtils;
import java.util.*;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.*;

@Service
@RequiredArgsConstructor
public class ReleaseSummaryCatalog {
    private final OperationalContextPort operationalContextPort;
    private final GitLabSettingsPort gitLabSettingsPort;
    private final GitLabRepositoryRefPort refPort;
    private final GitLabRepositoryRevisionPort revisionPort;
    private final GitLabRepositoryHistoryPort historyPort;
    public record Application(String systemId, String name, String summary, int repositoryCount) {}
    public record RepositoryCommit(String repositoryId, String committedAt) {}
    public record RefOption(String name, String label, GitLabRef.Kind kind, boolean isDefault,
                            List<String> repositories, String committedAt, List<RepositoryCommit> repositoryCommits) {}
    public record RefOptions(List<RefOption> branches, Integer nextPage, boolean truncated, List<String> warnings) {}

    public List<Application> applications() {
        var catalog = operationalContextPort.capture().snapshot().catalog();
        return catalog.systems().stream().map(s -> new Application(s.id(), s.name(), s.summary(),
                scopes(catalog,s.id()).stream().flatMap(c -> c.repositories().stream())
                        .filter(this::includedRepository).map(r -> r.repoId()).distinct().toList().size()))
                .filter(s -> s.repositoryCount()>0).sorted(Comparator.comparing(a -> a.name()!=null?a.name():a.systemId())).toList();
    }
    public RefOptions branches(String systemId, String search, int page) {
        var selected = select(operationalContextPort.capture(), systemId);
        var repository = onlyRepository(selected);
        var result = refPort.listRefs(group()+"/"+repository.project(), GitLabRef.Kind.BRANCH, search, page);
        var options = result.refs().stream().map(option -> new RefOption(option.ref().canonical(), option.ref().name(),
                GitLabRef.Kind.BRANCH, option.isDefault(), List.of(repository.repositoryId()), option.committedAt(),
                List.of(new RepositoryCommit(repository.repositoryId(), option.committedAt())))).toList();
        return new RefOptions(options, result.nextPage(), result.nextPage()!=null, List.of());
    }
    public ReleaseSummaryRange preview(String systemId, ReleaseSummaryDateSelection selection) {
        return resolveRange(select(operationalContextPort.capture(), systemId), selection);
    }
    private ReleaseSummaryRange resolveRange(ReleaseSummaryScope selected, ReleaseSummaryDateSelection selection) {
        var repo = onlyRepository(selected);
        try {
            var head = revisionPort.resolveRevision(group(), repo.project(), selection.branch());
            validateRevision(head, repo.project(), selection.branch());
            var zone = java.time.ZoneId.of(selection.timeZone());
            var from = selection.dateFrom().atStartOfDay(zone).toInstant();
            var until = selection.dateTo().plusDays(1).atStartOfDay(zone).toInstant();
            var baseline = historyPort.latestBefore(group(), repo.project(), head.commitId(), from)
                    .orElseThrow(() -> new ReleaseSummaryException("BASELINE_NOT_FOUND", "Brak commita przed datą Od. Wybierz późniejszą datę początkową."));
            var target = historyPort.latestBefore(group(), repo.project(), head.commitId(), until)
                    .orElseThrow(() -> new ReleaseSummaryException("TARGET_NOT_FOUND", "Brak commita do wybranej daty Do."));
            return new ReleaseSummaryRange(selection, repo.repositoryId(), baseline, target);
        } catch (ReleaseSummaryException e) { throw e; }
        catch (RuntimeException e) {
            throw new ReleaseSummaryException("HISTORY_UNAVAILABLE", "Nie można dobrać commitów dla wybranego brancha i dat. Spróbuj ponownie.");
        }
    }
    private ReleaseSummaryScope.Repository onlyRepository(ReleaseSummaryScope selected) {
        if (selected.repositories().size()!=1)
            throw new ReleaseSummaryException("SINGLE_PRIMARY_REQUIRED", "Release summary wymaga dokładnie jednego repozytorium primary z priority 1.");
        return selected.repositories().get(0);
    }
    public ReleaseSummaryScope pin(ReleaseSummaryRequest request, OperationalContextReadSession session) {
        var selected = select(session, request.systemId());
        var range = resolveRange(selected, request.range().selection());
        if (!range.equals(request.range()))
            throw new ReleaseSummaryException("RANGE_CHANGED", "Dobór commitów zmienił się od podglądu. Odśwież zakres dat przed ponownym startem.");
        var repo = onlyRepository(selected);
        var pinned = new ReleaseSummaryScope.Repository(repo.repositoryId(), repo.project(), repo.prefixes(),
                range.baseline().id(), range.baseline().id(), range.target().id(), range.target().id());
        return new ReleaseSummaryScope(selected.systemId(), selected.systemName(), selected.group(), selected.catalogDigest(),
                List.of(pinned), selected.entities());
    }
    public OperationalContextReadSession capture() { return operationalContextPort.capture(); }
    private void validateRevision(GitLabRepositoryRevision revision,String project,String ref) {
        if(revision==null || !group().equals(revision.group()) || !project.equals(revision.projectName())
                || !ref.equals(revision.ref()) || !revision.commitId().matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))
            throw new IllegalStateException("Invalid pinned revision");
    }
    private ReleaseSummaryScope select(OperationalContextReadSession session,String systemId) {
        var catalog=session.snapshot().catalog();
        var system=catalog.systems().stream().filter(s -> s.id().equals(systemId)).findFirst()
                .orElseThrow(() -> new ReleaseSummaryException("SYSTEM_NOT_FOUND","Nieznana aplikacja."));
        var entities=new ArrayList<Map<String,Object>>();
        entities.add(entity("system",system.id(),system.payload()));
        var repositories=new LinkedHashMap<String,ReleaseSummaryScope.Repository>();
        for(var scope:scopes(catalog,systemId)) {
            entities.add(entity("codeSearchScope",scope.id(),scope.payload()));
            for(var entry:scope.repositories()) {
                if(!includedRepository(entry)) continue;
                var repo=catalog.repositories().stream().filter(r -> r.id().equals(entry.repoId())).findFirst()
                        .orElseThrow(() -> new ReleaseSummaryException("REPOSITORY_NOT_FOUND","Brak encji repozytorium "+entry.repoId()));
                var path=repo.git().projectPath();
                if(!"gitlab".equalsIgnoreCase(repo.git().provider()) || path==null || !path.startsWith(group()+"/"))
                    throw new ReleaseSummaryException("REPOSITORY_OUTSIDE_SCOPE","Repozytorium "+repo.id()+" nie jest dostępne w skonfigurowanej grupie GitLab.");
                var project=GitLabPathUtils.relativeProjectPath(group(),path);
                var prefixes=new LinkedHashSet<String>();
                if(repositories.containsKey(repo.id())) prefixes.addAll(repositories.get(repo.id()).prefixes());
                else entities.add(entity("repository",repo.id(),repo.payload()));
                if(entry.pathPrefixes().isEmpty()) prefixes.add("");
                else entry.pathPrefixes().forEach(p -> {
                    if(p.startsWith("/") || p.contains("..") || p.contains("\\")) throw new ReleaseSummaryException("INVALID_PREFIX","Nieprawidłowy zakres repozytorium.");
                    prefixes.add(p.replaceAll("/+$",""));
                });
                var effectivePrefixes = prefixes.contains("") ? List.of("") : List.copyOf(prefixes);
                repositories.put(repo.id(),new ReleaseSummaryScope.Repository(repo.id(),project,effectivePrefixes,null,null,null,null));
            }
        }
        if(repositories.isEmpty()) throw new ReleaseSummaryException("NO_REPOSITORIES","Aplikacja nie ma zakresu repozytoriów.");
        return new ReleaseSummaryScope(system.id(),system.name(),group(),session.contentDigest(),List.copyOf(repositories.values()),entities);
    }
    private boolean includedRepository(OperationalContextRepositorySearchRepository entry) {
        return Integer.valueOf(1).equals(entry.priority())
                && "primary".equals(entry.role());
    }
    private List<OperationalContextRepositorySearchScope> scopes(OperationalContextCatalog c,String systemId) {
        return c.codeSearchScopes().stream().filter(s -> "system".equals(s.target().type()) && systemId.equals(s.target().id())).toList();
    }
    private Map<String,Object> entity(String type,String id,Map<String,Object> payload) {
        return Map.of("type",type,"id",id,"payload",payload);
    }
    private String group() {
        var value=gitLabSettingsPort.getGroup();
        if(value==null||value.isBlank()) throw new ReleaseSummaryException("GITLAB_UNAVAILABLE","Nie skonfigurowano grupy GitLab.");
        return value.replaceAll("^/+|/+$","");
    }
}

