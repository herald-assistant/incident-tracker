package pl.mkn.tdw.features.releasesummary.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRefPort;
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
    public RefOptions refs(String systemId, GitLabRef.Kind kind, String search, int page) {
        var session=operationalContextPort.capture();
        var selected=select(session,systemId);
        if (kind == GitLabRef.Kind.COMMIT) {
            if (search == null || search.isBlank()) return new RefOptions(List.of(),null,false,List.of("Wpisz pełny SHA commita."));
            var ref=new GitLabRef(kind,search);
            var found=new ArrayList<String>();
            var dates=new ArrayList<RepositoryCommit>();
            selected.repositories().forEach(r -> {
                var revision=revisionPort.resolveRevision(group(),r.project(),ref.canonical());
                validateRevision(revision,r.project(),ref.canonical());
                dates.add(new RepositoryCommit(r.repositoryId(),new GitLabRefOption(ref,revision.committedAt(),false).committedAt()));
                found.add(r.repositoryId());
            });
            return new RefOptions(List.of(new RefOption(ref.canonical(),ref.name(),kind,false,List.copyOf(found),latest(dates),List.copyOf(dates))),null,false,List.of());
        }
        var values=new LinkedHashMap<String,RefOption>();
        Integer next=null;
        for(var repository:selected.repositories()) {
            var result=refPort.listRefs(group()+"/"+repository.project(),kind,search,page);
            if(result.nextPage()!=null) next=result.nextPage();
            for(var option:result.refs()) {
                var ref=option.ref();
                var ids=new ArrayList<String>();
                var dates=new ArrayList<RepositoryCommit>();
                var previous=values.get(ref.canonical());
                if(previous!=null)dates.addAll(previous.repositoryCommits());
                dates.add(new RepositoryCommit(repository.repositoryId(),option.committedAt()));
                if(values.containsKey(ref.canonical())) ids.addAll(values.get(ref.canonical()).repositories());
                ids.add(repository.repositoryId());
                values.put(ref.canonical(),new RefOption(ref.canonical(),ref.name(),kind,option.isDefault() || previous!=null && previous.isDefault(),List.copyOf(ids),latest(dates),List.copyOf(dates)));
            }
        }
        return new RefOptions(List.copyOf(values.values()),next,next!=null,List.of());
    }
    private String latest(List<RepositoryCommit> dates) {
        return dates.stream().map(RepositoryCommit::committedAt).filter(Objects::nonNull)
                .max(java.util.Comparator.comparing(java.time.Instant::parse)).orElse(null);
    }
    public ReleaseSummaryScope pin(ReleaseSummaryRequest request, OperationalContextReadSession session) {
        var selected=select(session, request.systemId());
        var pinned=new ArrayList<ReleaseSummaryScope.Repository>();
        var resolved=new HashMap<String,GitLabRepositoryRevision>();
        for(var r:selected.repositories()) {
            try {
                var before=resolved.computeIfAbsent(r.project()+"@"+request.baselineRef().canonical(),
                        key->revisionPort.resolveRevision(group(),r.project(),request.baselineRef().canonical()));
                var after=resolved.computeIfAbsent(r.project()+"@"+request.targetRef().canonical(),
                        key->revisionPort.resolveRevision(group(),r.project(),request.targetRef().canonical()));
                validateRevision(before,r.project(),request.baselineRef().canonical());
                validateRevision(after,r.project(),request.targetRef().canonical());
                pinned.add(new ReleaseSummaryScope.Repository(r.repositoryId(),r.project(),r.prefixes(),
                        request.baselineRef().canonical(),before.commitId(),request.targetRef().canonical(),after.commitId()));
            } catch(RuntimeException e) {
                throw new ReleaseSummaryException("REF_UNAVAILABLE","Nie można odczytać obu refów repozytorium "+r.repositoryId()+".");
            }
        }
        return new ReleaseSummaryScope(selected.systemId(),selected.systemName(),selected.group(),selected.catalogDigest(),pinned,selected.entities());
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

