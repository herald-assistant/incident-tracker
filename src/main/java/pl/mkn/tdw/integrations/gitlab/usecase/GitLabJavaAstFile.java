package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

public record GitLabJavaAstFile(
        String path,
        String packageName,
        List<String> imports,
        List<String> staticImports,
        List<GitLabJavaTypeDeclaration> types,
        CompilationUnit compilationUnit,
        List<String> limitations
) {
    public GitLabJavaAstFile {
        path = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        packageName = GitLabEndpointUseCaseModelSupport.trimToNull(packageName);
        imports = GitLabEndpointUseCaseModelSupport.copyStrings(imports);
        staticImports = GitLabEndpointUseCaseModelSupport.copyStrings(staticImports);
        types = GitLabEndpointUseCaseModelSupport.copy(types);
        limitations = GitLabEndpointUseCaseModelSupport.copyStrings(limitations);
    }

    public boolean parsed() {
        return compilationUnit != null;
    }
}
