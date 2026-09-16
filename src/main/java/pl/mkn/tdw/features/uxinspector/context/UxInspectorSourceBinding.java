package pl.mkn.tdw.features.uxinspector.context;

import java.util.List;

public record UxInspectorSourceBinding(
        String componentId,
        String componentSymbol,
        String componentSelector,
        String sourcePath,
        String templatePath,
        String templateKind,
        Integer templateLineStart,
        Integer templateLineEnd,
        String sourceReference,
        String elementSnippet,
        List<TemplateBinding> elementBindings,
        TemplateBinding formSubmitBinding,
        List<String> referencedSymbols
) {
    public UxInspectorSourceBinding {
        elementSnippet = elementSnippet != null ? elementSnippet : "";
        elementBindings = elementBindings != null ? List.copyOf(elementBindings) : List.of();
        referencedSymbols = referencedSymbols != null ? List.copyOf(referencedSymbols) : List.of();
    }

    public record TemplateBinding(
            String kind,
            String target,
            String expression,
            List<String> referencedSymbols,
            int templateLine
    ) {
        public TemplateBinding {
            referencedSymbols = referencedSymbols != null ? List.copyOf(referencedSymbols) : List.of();
        }
    }
}
