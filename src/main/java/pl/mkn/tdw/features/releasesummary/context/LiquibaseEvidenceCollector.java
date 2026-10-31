package pl.mkn.tdw.features.releasesummary.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryScope;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryReadPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySnapshotPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFilePort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySnapshot;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence.*;

@Service
@RequiredArgsConstructor
public class LiquibaseEvidenceCollector {
    private final GitLabRepositorySnapshotPort snapshotPort;
    private final GitLabRepositoryReadPort readPort;
    private final GitLabVerifiedRepositoryFilePort verifiedPort;
    private final LiquibaseDocumentParser parser;

    public ReleaseSummaryEvidence collect(ReleaseSummaryScope scope) {
        var sources=new ArrayList<Source>(); var units=new ArrayList<Unit>(); var gaps=new ArrayList<Gap>();
        int scanned=0;
        for(var repository:scope.repositories()) {
            try {
                var before=scan(scope,repository,"BASELINE",repository.baselineCommit(),gaps);
                var after=scan(scope,repository,"TARGET",repository.targetCommit(),gaps);
                scanned+=before.reads.size()+after.reads.size();
                var changed=new TreeSet<String>(); changed.addAll(before.documents.keySet()); changed.addAll(after.documents.keySet());
                var required=new TreeSet<String>();
                for(var path:changed) {
                    var old=before.documents.get(path); var next=after.documents.get(path);
                    var type=old==null?"ADDED":next==null?"REMOVED":
                            !old.source.content().equals(next.source.content())?"MODIFIED":
                            !incoming(before,path).equals(incoming(after,path))?"INCLUSION_CHANGED":
                            dependenciesChanged(before,after,path)?"DEPENDENCY_CHANGED":null;
                    if(type==null) continue;
                    required.add(path);
                    var elements=elements(repository,path,old,next,type);
                    units.add(new Unit(id(repository.project()+":"+path),repository.repositoryId(),path,type,
                            old!=null?old.source.sourceRef():null,next!=null?next.source.sourceRef():null,
                            elements,(old==null||old.parsed.structured())&&(next==null||next.parsed.structured())));
                }
                for(var side:List.of(before,after)) {
                    var include=new TreeSet<String>();
                    for(var path:required) dependencies(side,path,include);
                    // Ancestor declarations supply activation/ordering context, without pulling unrelated siblings.
                    boolean updated;
                    do { updated=false;
                        for(var doc:side.documents.values()) if(doc.source.dependencies().stream().anyMatch(include::contains))
                            updated|=include.add(doc.source.path());
                    } while(updated);
                    for(var path:include) { var doc=side.documents.get(path); if(doc!=null) sources.add(doc.source); }
                }
            } catch(RuntimeException e) {
                gaps.add(new Gap("SOURCE_UNAVAILABLE",repository.repositoryId(),"BOTH","",
                        "Nie można zweryfikować pełnego drzewa lub treści repozytorium.",true));
            }
        }
        var items=units.stream().map(u->new AnalysisEvidenceItem(u.path(),List.of(
                new AnalysisEvidenceAttribute("repository",u.repositoryId()),
                new AnalysisEvidenceAttribute("changeType",u.changeType()),
                new AnalysisEvidenceAttribute("unitId",u.unitId()),
                new AnalysisEvidenceAttribute("elements",Integer.toString(u.elements().size())),
                new AnalysisEvidenceAttribute("structuralCoverage",u.structurallyVerified()?"VERIFIED":"AI_IDENTIFIED")))).toList();
        return new ReleaseSummaryEvidence(sources,units,gaps,
                List.of(new AnalysisEvidenceSection("GitLab","Liquibase changes",items)),scanned,gaps.stream().noneMatch(Gap::blocking));
    }
    private Side scan(ReleaseSummaryScope scope,ReleaseSummaryScope.Repository repo,String side,String sha,List<Gap> gaps) {
        var tree=snapshotPort.snapshot(scope.group(),repo.project(),sha);
        if(!scope.group().equals(tree.group()) || !repo.project().equals(tree.project()) || !sha.equals(tree.commitId()))
            throw new IllegalStateException("Snapshot scope mismatch");
        var state=new Side(tree);
        for(var path:tree.blobs().keySet()) if(candidate(path) && repo.prefixes().stream().anyMatch(p->p.isEmpty()||path.equals(p)||path.startsWith(p+"/"))) {
            var content=read(scope,repo,state,path);
            var parsed=parser.parse(path,content);
            if(parsed.changelog()) state.documents.put(path,new Document(source(repo,side,sha,path,tree,content,List.of()),parsed));
        }
        var pending=new ArrayDeque<>(state.documents.keySet()); var expanded=new HashSet<String>();
        while(!pending.isEmpty()) {
            var path=pending.removeFirst();
            if(!expanded.add(path)) continue;
            var document=state.documents.get(path);
            var dependencies=new ArrayList<String>();
            for(var entry:parser.flatten(document.parsed).entrySet()) {
                var node=entry.getValue(); var attrs=node.attributes();
                if("true".equalsIgnoreCase(String.valueOf(attrs.get("ignore")))) continue;
                var key=switch(node.kind()) { case "include","sqlFile","rollbackSqlFile"->"path"; case "includeAll"->"path";
                    case "loadData","loadUpdateData"->"file";
                    case "createProcedure","createFunction","createTrigger","createPackage","createPackageBody","createView"->attrs.containsKey("path")?"path":null;
                    case "property"->attrs.containsKey("file")?"file":null;
                    default->attrs.containsKey("valueClobFile")?"valueClobFile":attrs.containsKey("valueBlobFile")?"valueBlobFile":null; };
                if("include".equals(node.kind())) key="file";
                if((entry.getKey().contains("loadData:")||entry.getKey().contains("loadUpdateData:"))
                        && Set.of("BLOB","CLOB").contains(Objects.toString(attrs.get("type"),"").toUpperCase(Locale.ROOT)))
                    gaps.add(gap(repo,side,path,"CSV_EXTERNAL_CONTENT","CSV wskazuje zewnętrzne wartości BLOB/CLOB; pełność zależności nie jest potwierdzona.",true));
                if(key==null) continue;
                var raw=attrs.get(key);
                if(raw==null || raw.toString().isBlank()) {
                    gaps.add(gap(repo,side,path,"UNRESOLVED_REFERENCE","Brak ścieżki "+node.kind(),true)); continue;
                }
                try {
                    var directory="includeAll".equals(node.kind());
                    var resolved=resolve(tree,path,raw.toString(),Boolean.parseBoolean(String.valueOf(attrs.get("relativeToChangelogFile"))),directory);
                    List<String> paths;
                    if(directory) {
                        var filter=Objects.toString(attrs.get("endsWithFilter"),"");
                        int min=Integer.parseInt(Objects.toString(attrs.get("minDepth"),"1"));
                        int max=Integer.parseInt(Objects.toString(attrs.get("maxDepth"),Integer.toString(Integer.MAX_VALUE)));
                        if(min<1||max<min) throw new IllegalArgumentException("Invalid includeAll depth");
                        paths=tree.blobs().keySet().stream().filter(p->p.startsWith(resolved+"/"))
                                .filter(p->{int depth=p.substring(resolved.length()+1).split("/").length; return depth>=min&&depth<=max;})
                                .filter(p->(candidate(p)||p.endsWith(".groovy"))&&p.endsWith(filter)).toList();
                        if(paths.isEmpty() && !"false".equalsIgnoreCase(Objects.toString(attrs.get("errorIfMissingOrEmpty"),"true")))
                            throw new IllegalArgumentException("Empty includeAll");
                    } else paths=List.of(resolved);
                    for(var dependency:paths) {
                        dependencies.add(dependency);
                        if(!state.documents.containsKey(dependency)) {
                            var content=read(scope,repo,state,dependency);
                            var parsed=parser.parse(dependency,content);
                            if(node.kind().startsWith("include")&&!parsed.changelog()&&!dependency.toLowerCase(Locale.ROOT).endsWith(".sql"))
                                throw new IllegalArgumentException("Unsupported included changelog");
                            if(!parsed.changelog()) parsed=rawDocument(content,dependency);
                            state.documents.put(dependency,new Document(source(repo,side,sha,dependency,tree,content,List.of()),parsed));
                        }
                        pending.add(dependency);
                    }
                } catch(RuntimeException e) {
                    gaps.add(gap(repo,side,path,"UNRESOLVED_REFERENCE","Nie można jednoznacznie odczytać "+node.kind()+": "+raw,true));
                }
            }
            for(var limit:document.parsed.limits()) {
                if(limit.equals("CUSTOM_INCLUDE_FILTER")) gaps.add(gap(repo,side,path,limit,"Niestandardowa selekcja includeAll wymaga jawnego rozstrzygnięcia.",true));
                else if(limit.equals("UNRESOLVED_PROPERTY")) gaps.add(gap(repo,side,path,limit,"Wartości właściwości zachowano jako wyrażenia; środowisko nie jest znane.",false));
                else if(limit.startsWith("UNSUPPORTED_CHANGE")) gaps.add(gap(repo,side,path,"UNSUPPORTED_CHANGE",limit,false));
            }
            state.documents.put(path,new Document(source(repo,side,sha,path,tree,document.source.content(),dependencies),document.parsed));
        }
        detectCycles(state,repo,side,gaps);
        return state;
    }
    private String read(ReleaseSummaryScope scope,ReleaseSummaryScope.Repository repo,Side state,String path) {
        return state.reads.computeIfAbsent(path,p->{
            var file=verifiedPort.readComplete(readPort,scope.group(),repo.project(),state.tree.commitId(),p);
            if(file==null||!p.equals(file.path())) throw new IllegalStateException("Missing verified file");
            var bytes=file.content().getBytes(StandardCharsets.UTF_8);
            var expected=state.tree.blobs().get(p);
            if(expected==null) throw new IllegalStateException("File not in complete tree");
            try {
                var digest=MessageDigest.getInstance(expected.length()==40?"SHA-1":"SHA-256");
                digest.update(("blob "+bytes.length+"\0").getBytes(StandardCharsets.UTF_8)); digest.update(bytes);
                if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected)) throw new IllegalStateException("Blob hash differs from tree");
            } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            return file.content();
        });
    }
    private Source source(ReleaseSummaryScope.Repository repo,String side,String sha,String path,GitLabRepositorySnapshot tree,String text,List<String> deps) {
        return new Source("gitlab:"+tree.group()+"/"+repo.project()+"@"+sha+":"+path,repo.repositoryId(),repo.project(),side,sha,path,tree.blobs().get(path),text,deps);
    }
    private LiquibaseDocumentParser.Parsed rawDocument(String text,String path) {
        return new LiquibaseDocumentParser.Parsed(true,List.of(new LiquibaseDocumentParser.Node(
                path.toLowerCase(Locale.ROOT).endsWith(".sql")?"sql":"data",Map.of("text",text),List.of())),false,List.of("AI_IDENTIFIED"));
    }
    private boolean candidate(String path) { return path.toLowerCase(Locale.ROOT).matches(".*\\.(xml|ya?ml|json|sql)$"); }
    private String resolve(GitLabRepositorySnapshot tree,String owner,String raw,boolean relative,boolean directory) {
        if(raw.contains("${")||raw.contains("\\")||raw.startsWith("/")||raw.contains("://")) throw new IllegalArgumentException("Dynamic path");
        var requested=raw.startsWith("classpath:")?raw.substring(10):raw;
        String base=relative&&owner.contains("/")?owner.substring(0,owner.lastIndexOf('/')+1):"";
        var segments=new ArrayDeque<String>();
        for(var part:(base+requested).split("/")) {
            if(part.isEmpty()||part.equals(".")) continue;
            if(part.equals("..")) {if(segments.isEmpty()) throw new IllegalArgumentException("Path escapes repository"); segments.removeLast();}
            else segments.add(part);
        }
        var normalized=String.join("/",segments);
        var found=new TreeSet<String>();
        if(directory) {
            for(var path:tree.blobs().keySet()) {
                if(path.startsWith(normalized+"/")) found.add(normalized);
                var marker="/resources/"+normalized+"/"; int at=path.indexOf(marker);
                if(!relative&&at>=0) found.add(path.substring(0,at+"/resources/".length())+normalized);
            }
        } else for(var path:tree.blobs().keySet())
            if(path.equals(normalized)||!relative&&path.endsWith("/resources/"+normalized)) found.add(path);
        if(found.size()!=1) throw new IllegalArgumentException("Missing or ambiguous repository path");
        return found.first();
    }
    private List<String> incoming(Side side,String path) {
        return side.documents.values().stream().filter(d->d.source.dependencies().contains(path)).map(d->d.source.path()).sorted().toList();
    }
    private boolean dependenciesChanged(Side before,Side after,String path) {
        var b=new TreeSet<String>(); var a=new TreeSet<String>(); dependencies(before,path,b); dependencies(after,path,a);
        if(!a.equals(b)) return true;
        return a.stream().anyMatch(p->!Objects.equals(before.tree.blobs().get(p),after.tree.blobs().get(p)));
    }
    private void dependencies(Side side,String path,Set<String> result) {
        if(!result.add(path)) return;
        var document=side.documents.get(path);
        if(document!=null) for(var dep:document.source.dependencies()) dependencies(side,dep,result);
    }
    private List<Element> elements(ReleaseSummaryScope.Repository repo,String path,Document before,Document after,String type) {
        var b=before!=null?parser.flatten(before.parsed):Map.<String,LiquibaseDocumentParser.Node>of();
        var a=after!=null?parser.flatten(after.parsed):Map.<String,LiquibaseDocumentParser.Node>of();
        var keys=new LinkedHashSet<String>(); keys.addAll(b.keySet()); keys.addAll(a.keySet());
        var result=new ArrayList<Element>();
        for(var key:keys) {
            var old=b.get(key); var next=a.get(key);
            if(old!=null&&next!=null&&old.attributes().equals(next.attributes())&&!type.equals("INCLUSION_CHANGED")) continue;
            result.add(new Element(id(repo.project()+":"+path+":"+key),next!=null?next.kind():old.kind(),key,
                    old!=null?old.attributes():null,next!=null?next.attributes():null));
        }
        if(result.isEmpty()) result.add(new Element(id(repo.project()+":"+path+":dependency"),"dependency","/",
                Map.of("dependencies",before!=null?before.source.dependencies():List.of()),
                Map.of("dependencies",after!=null?after.source.dependencies():List.of())));
        return result;
    }
    private void detectCycles(Side side,ReleaseSummaryScope.Repository repo,String name,List<Gap> gaps) {
        var done=new HashSet<String>();
        for(var path:side.documents.keySet()) if(cycle(side,path,new HashSet<>(),done)) {
            gaps.add(gap(repo,name,path,"INCLUDE_CYCLE","Cykliczna zależność changelogów.",true)); break;
        }
    }
    private boolean cycle(Side side,String path,Set<String> active,Set<String> done) {
        if(active.contains(path)) return true;
        if(!done.add(path)) return false;
        active.add(path);
        var doc=side.documents.get(path);
        if(doc!=null) for(var dep:doc.source.dependencies()) if(cycle(side,dep,active,done)) return true;
        active.remove(path); return false;
    }
    private Gap gap(ReleaseSummaryScope.Repository repo,String side,String path,String code,String detail,boolean blocking) {
        return new Gap(code,repo.repositoryId(),side,path,detail,blocking);
    }
    public static String id(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).substring(0,20); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Document(Source source,LiquibaseDocumentParser.Parsed parsed) {}
    private static final class Side {
        final GitLabRepositorySnapshot tree;
        final Map<String,String> reads=new TreeMap<>();
        final Map<String,Document> documents=new TreeMap<>();
        Side(GitLabRepositorySnapshot tree) {this.tree=tree;}
    }
}

