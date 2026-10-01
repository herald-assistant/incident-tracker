package pl.mkn.tdw.features.deliverycomplexityassessment.evidence;

import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliverycomplexityassessment.deliveryunit.DeliveryUnitBuilder;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestChangedFile;
import java.util.*;

public record DeliveryEvidenceSlice(List<GitLabMergeRequest> mergeRequests, List<String> coverage) {
    public DeliveryEvidenceSlice {
        mergeRequests = List.copyOf(mergeRequests);
        coverage = List.copyOf(coverage);
    }

    public static DeliveryEvidenceSlice whole(DeliveryUnit unit) {
        var refs = new ArrayList<String>();
        for (var mr : unit.mergeRequests()) {
            var identity = DeliveryUnitBuilder.identity(mr);
            if (mr.changedFiles().isEmpty()) refs.add(identity + "#metadata");
            for (var i = 0; i < mr.changedFiles().size(); i++) {
                var file = mr.changedFiles().get(i);
                refs.add(identity + "#file:" + i + ":" + (file.newPath() != null ? file.newPath() : file.oldPath()));
            }
        }
        if (new HashSet<>(refs).size() != refs.size()) throw new IllegalArgumentException("Duplicate delivery evidence identities.");
        return new DeliveryEvidenceSlice(unit.mergeRequests(), refs);
    }

    public DeliveryUnit unit(DeliveryUnit source) {
        return new DeliveryUnit(source.unitId(), source.issues(), mergeRequests, source.limitations());
    }

    public List<DeliveryEvidenceSlice> split() {
        if (mergeRequests.size() > 1) {
            var cut = balancedCut(mergeRequests.stream().map(mr -> Math.max(1L, mr.toString().length())).toList());
            var refsCut = mergeRequests.subList(0, cut).stream().mapToInt(mr -> Math.max(1, mr.changedFiles().size())).sum();
            return List.of(new DeliveryEvidenceSlice(mergeRequests.subList(0, cut), coverage.subList(0, refsCut)),
                    new DeliveryEvidenceSlice(mergeRequests.subList(cut, mergeRequests.size()), coverage.subList(refsCut, coverage.size())));
        }
        var mr = mergeRequests.get(0);
        if (mr.changedFiles().size() <= 1) {
            throw new IllegalArgumentException("EVIDENCE_ATOM_TOO_LARGE: " + coverage
                    + ". Pełny plik lub metadata MR z kontekstem nie mieszczą się w budżecie wybranego modelu.");
        }
        var cut = balancedCut(mr.changedFiles().stream().map(file -> Math.max(1L, file.toString().length())).toList());
        return List.of(new DeliveryEvidenceSlice(List.of(withFiles(mr, mr.changedFiles().subList(0, cut))), coverage.subList(0, cut)),
                new DeliveryEvidenceSlice(List.of(withFiles(mr, mr.changedFiles().subList(cut, mr.changedFiles().size()))), coverage.subList(cut, coverage.size())));
    }

    private static int balancedCut(List<Long> sizes) {
        var target = sizes.stream().mapToLong(Long::longValue).sum() / 2;
        long sum = 0, bestDistance = Long.MAX_VALUE;
        var cut = 1;
        for (var i = 1; i < sizes.size(); i++) {
            sum += sizes.get(i - 1);
            var distance = Math.abs(sum - target);
            if (distance < bestDistance) { bestDistance = distance; cut = i; }
        }
        return cut;
    }

    private static GitLabMergeRequest withFiles(GitLabMergeRequest mr, List<GitLabMergeRequestChangedFile> files) {
        return new GitLabMergeRequest(mr.id(), mr.iid(), mr.projectId(), mr.projectPath(), mr.title(), mr.state(), mr.webUrl(),
                mr.sourceBranch(), mr.targetBranch(), mr.authorName(), mr.authorId(), mr.createdAt(), mr.updatedAt(), mr.mergedAt(),
                mr.changesCount(), mr.commits(), files, mr.limitations());
    }
}
