package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

public record GitLabEndpointUseCaseParsedSourceFile(
        GitLabEndpointUseCaseSourceFile sourceFile,
        CompilationUnit compilationUnit,
        boolean parseSuccessful,
        List<String> limitations
) {
    public GitLabEndpointUseCaseParsedSourceFile {
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }

    public boolean hasCompilationUnit() {
        return compilationUnit != null;
    }
}
