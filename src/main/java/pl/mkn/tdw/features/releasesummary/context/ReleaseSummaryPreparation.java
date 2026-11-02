package pl.mkn.tdw.features.releasesummary.context;

import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryScope;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryReadPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFilePort;
import pl.mkn.tdw.integrations.gitlab.GitLabInstructionContextPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleaseSummaryPreparation {
    private final GitLabRepositoryReadPort files;
    private final GitLabVerifiedRepositoryFilePort verifiedFiles;
    private final GitLabInstructionContextPort instructions;

    public record Prepared(ReleaseSummaryEvidence evidence, Set<String> sources, List<InstructionSource> instructions) {
        public Prepared { sources=Set.copyOf(sources);instructions=List.copyOf(instructions); }
    }

    public Prepared prepare(ReleaseSummaryScope scope, ReleaseSummaryEvidence evidence) {
        var changes=new ArrayList<ReleaseSummaryEvidence.ChangedFile>();
        var gaps=new ArrayList<>(evidence.gaps().stream()
                .filter(gap->!Set.of("PATCH_INCOMPLETE","PATCH_LIMITED").contains(gap.code())).toList());
        var sources=new HashSet<String>();
        for(var file:evidence.changes()) {
            var change=file.change();
            if(!change.patchComplete()) {
                try {
                    var repo=scope.repositories().stream().filter(candidate->candidate.repositoryId().equals(file.repositoryId())
                            &&candidate.project().equals(file.project())).findFirst().orElseThrow();
                    var readSources=new HashSet<String>();
                    change=complete(scope.group(),repo,change,readSources);
                    sources.addAll(readSources);
                } catch(RuntimeException|IOException exception) {
                    gaps.add(new ReleaseSummaryEvidence.Gap("PATCH_INCOMPLETE",file.repositoryId(),"COMPARISON",
                            change.newPath()!=null?change.newPath():change.oldPath(),
                            "Nie mozna przygotowac pelnej roznicy wybranego pliku. Wymaga celowanego badania; nie oznacza braku zmian.",false));
                }
            }
            changes.add(new ReleaseSummaryEvidence.ChangedFile(file.repositoryId(),file.project(),change));
        }
        var instructionSources=new ArrayList<InstructionSource>();
        for(var repo:scope.repositories()) {
            var selectedPaths=changes.stream().filter(file->file.repositoryId().equals(repo.repositoryId()))
                .flatMap(file->java.util.stream.Stream.of(file.change().oldPath(),file.change().newPath()))
                .filter(Objects::nonNull).distinct().toList();
            if(selectedPaths.isEmpty())continue;
            try {
            var result=instructions.discoverApplicable(new InstructionContextRequest(List.of(
                new InstructionRepositoryScope(scope.group()+"/"+repo.project(),repo.targetCommit(),selectedPaths))));
            if(result==null)throw new IllegalStateException("No instruction result.");
                for(var source:result.sources()) {
                    if(!(scope.group()+"/"+repo.project()).equals(source.repositoryKey())||!repo.targetCommit().equals(source.ref())
                            ||source.truncated()||source.content()==null||!GitLabRepositoryPath.isSafePath(source.path(),false)
                            ||!source.path().startsWith(".github/")||!source.path().endsWith(".instructions.md")
                            ||source.applicableChangedFiles().isEmpty()||!selectedPaths.containsAll(source.applicableChangedFiles())) {
                        gaps.add(new ReleaseSummaryEvidence.Gap("INSTRUCTION_LIMITED",repo.repositoryId(),"TARGET",".github",
                                "Instrukcja nie ma kompletnej tresci albo potwierdzonego zakresu i rewizji.",false));
                        continue;
                    }
                    instructionSources.add(source);
                    sources.add("gitlab:"+source.repositoryKey()+"@"+source.ref()+":"+source.path());
                }
            for(var limitation:result.limitations())gaps.add(new ReleaseSummaryEvidence.Gap("INSTRUCTION_LIMITED",repo.repositoryId(),
                "TARGET",".github",limitation,false));
            } catch(RuntimeException exception) {
            gaps.add(new ReleaseSummaryEvidence.Gap("INSTRUCTION_UNAVAILABLE",repo.repositoryId(),"TARGET",".github",
                "Nie mozna dobrac instrukcji repozytorium dla wybranych zmian changelog.",false));
            }
        }
        return new Prepared(new ReleaseSummaryEvidence(changes,gaps,evidence.changeListComplete()),sources,instructionSources);
    }

    private GitLabRepositoryComparison.Change complete(String group,ReleaseSummaryScope.Repository repo,
            GitLabRepositoryComparison.Change change,Set<String> sources) throws IOException {
        if(change.oldMode()!=null&&!Set.of("100644","100755").contains(change.oldMode())
                ||change.newMode()!=null&&!Set.of("100644","100755").contains(change.newMode()))
            throw new IllegalArgumentException("Not a regular text file.");
        String patch;
        if(change.oldBlobId()!=null&&change.oldBlobId().equals(change.newBlobId())) {
            patch=Objects.equals(change.oldPath(),change.newPath())?"":
                    "rename from "+change.oldPath()+"\nrename to "+change.newPath()+"\n";
            if(!Objects.equals(change.oldMode(),change.newMode()))
                patch+="old mode "+change.oldMode()+"\nnew mode "+change.newMode()+"\n";
            if(patch.isEmpty())throw new IllegalArgumentException("No change in path, content or mode.");
        } else {
            var before=change.oldPath()==null?"":read(group,repo.project(),repo.baselineCommit(),change.oldPath(),sources);
            var after=change.newPath()==null?"":read(group,repo.project(),repo.targetCommit(),change.newPath(),sources);
            var oldText=new RawText(before.getBytes(StandardCharsets.UTF_8));
            var newText=new RawText(after.getBytes(StandardCharsets.UTF_8));
            var edits=new HistogramDiff().diff(RawTextComparator.DEFAULT,oldText,newText);
            if(edits.isEmpty())throw new IllegalStateException("Changed blobs have identical text.");
            var output=new ByteArrayOutputStream();
            try(var formatter=new DiffFormatter(output)) {
                formatter.setContext(3);
                formatter.format(edits,oldText,newText);
                formatter.flush();
            }
            patch="--- "+(change.oldPath()==null?"/dev/null":"a/"+change.oldPath())+"\n+++ "
                    +(change.newPath()==null?"/dev/null":"b/"+change.newPath())+"\n"+output.toString(StandardCharsets.UTF_8);
        }
        return new GitLabRepositoryComparison.Change(change.oldPath(),change.newPath(),change.oldBlobId(),change.newBlobId(),
                change.oldMode(),change.newMode(),patch,true);
    }

    private String read(String group,String project,String commit,String path,Set<String> sources) {
        var file=verifiedFiles.readComplete(files,group,project,commit,path);
        if(file==null||!path.equals(file.path())||file.content()==null
                ||file.sizeBytes()!=file.content().getBytes(StandardCharsets.UTF_8).length
                ||RawText.isBinary(file.content().getBytes(StandardCharsets.UTF_8))
                ||file.content().startsWith("version https://git-lfs.github.com/spec/v1"))
            throw new IllegalArgumentException("No complete verified text.");
        sources.add("gitlab:"+group+"/"+project+"@"+commit+":"+path);
        return file.content();
    }
}