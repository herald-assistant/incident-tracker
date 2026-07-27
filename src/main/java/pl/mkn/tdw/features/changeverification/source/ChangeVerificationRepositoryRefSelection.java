package pl.mkn.tdw.features.changeverification.source;

import org.springframework.util.StringUtils;

import java.util.List;

record ChangeVerificationRepositoryRefSelection(
        String repositoryKey,
        String sourceRef,
        String targetRef,
        String analysisRef,
        String analysisRefSource,
        Boolean sourceRefAvailable,
        Boolean targetRefAvailable,
        List<String> limitations
) {

    static final String SOURCE_REF = "SOURCE_REF";
    static final String TARGET_REF = "TARGET_REF";
    static final String UNRESOLVED = "UNRESOLVED";

    ChangeVerificationRepositoryRefSelection {
        repositoryKey = value(repositoryKey);
        sourceRef = value(sourceRef);
        targetRef = value(targetRef);
        analysisRef = StringUtils.hasText(analysisRef) ? analysisRef.trim() : ref(sourceRef, targetRef);
        analysisRefSource = StringUtils.hasText(analysisRefSource) ? analysisRefSource.trim() : source(analysisRef, sourceRef, targetRef);
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }

    String key() {
        return key(repositoryKey, sourceRef, targetRef);
    }

    static String key(String repositoryKey, String sourceRef, String targetRef) {
        return value(repositoryKey) + "|" + value(sourceRef) + "|" + value(targetRef);
    }

    static String ref(String sourceRef, String targetRef) {
        return StringUtils.hasText(sourceRef) ? sourceRef.trim() : value(targetRef);
    }

    private static String source(String analysisRef, String sourceRef, String targetRef) {
        if (StringUtils.hasText(analysisRef) && value(analysisRef).equals(value(sourceRef))) {
            return SOURCE_REF;
        }
        if (StringUtils.hasText(analysisRef) && value(analysisRef).equals(value(targetRef))) {
            return TARGET_REF;
        }
        return UNRESOLVED;
    }

    private static String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
