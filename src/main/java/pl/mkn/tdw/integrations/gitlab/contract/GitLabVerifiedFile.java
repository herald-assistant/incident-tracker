package pl.mkn.tdw.integrations.gitlab.contract;

public record GitLabVerifiedFile(String path, String content, int sizeBytes) {
}
