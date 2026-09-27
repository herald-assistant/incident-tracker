package pl.mkn.tdw.integrations.gitlab;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabResolvedImport;

public interface GitLabFrontendTypeScriptImportResolverPort {

    GitLabResolvedImport resolve(
            GitLabFrontendRepositoryScope scope,
            String consumerFilePath,
            String moduleSpecifier,
            String importedSymbol
    );
}
