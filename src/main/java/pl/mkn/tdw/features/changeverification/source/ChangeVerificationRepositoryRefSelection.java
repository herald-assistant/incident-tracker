package pl.mkn.tdw.features.changeverification.source;

import org.springframework.util.StringUtils;

import java.util.List;

record ChangeVerificationRepositoryRefSelection(
        String repositoryKey,
        String sourceRef,
        String targetRef,
        String analysisRef,
        String analysisRefSource,
        List<String> limitations
) {

    static final String MR_HEAD_SHA = "MR_HEAD_SHA";
    static final String UNRESOLVED = "UNRESOLVED";

    ChangeVerificationRepositoryRefSelection {
        repositoryKey = value(repositoryKey);
        sourceRef = value(sourceRef);
        targetRef = value(targetRef);
        analysisRef = value(analysisRef);
        analysisRefSource = StringUtils.hasText(analysisRefSource) ? analysisRefSource.trim() : UNRESOLVED;
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    String key() {
        return key(repositoryKey, sourceRef, targetRef);
    }

    static String key(String repositoryKey, String sourceRef, String targetRef) {
        return value(repositoryKey) + "|" + value(sourceRef) + "|" + value(targetRef);
    }

    private static String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
