package pl.mkn.tdw.features.releasesummary.contract;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import java.util.List;
import java.util.Map;

public record ReleaseSummaryEvidence(
        List<Source> sources, List<Unit> units, List<Gap> gaps,
        List<AnalysisEvidenceSection> sections, int scannedFiles, boolean sourceComplete) {
    public ReleaseSummaryEvidence {
        sources=List.copyOf(sources); units=List.copyOf(units); gaps=List.copyOf(gaps); sections=List.copyOf(sections);
    }
    public record Source(String sourceRef,String repositoryId,String project,String side,String commitId,
                         String path,String blobId,String content,List<String> dependencies) {
        public Source { dependencies=List.copyOf(dependencies); }
    }
    public record Unit(String unitId,String repositoryId,String path,String changeType,
                       String baselineSource,String targetSource,List<Element> elements,boolean structurallyVerified) {
        public Unit { elements=List.copyOf(elements); }
    }
    public record Element(String elementId,String kind,String locator,Map<String,Object> before,Map<String,Object> after) {}
    public record Gap(String code,String repositoryId,String side,String path,String detail,boolean blocking) {}
    public boolean noChanges() { return sourceComplete && units.isEmpty() && gaps.isEmpty(); }
}

