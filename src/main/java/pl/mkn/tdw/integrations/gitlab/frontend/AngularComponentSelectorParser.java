package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class AngularComponentSelectorParser {
    private static final Pattern COMPONENT_DECORATOR = Pattern.compile("@Component\\s*\\(");
    private static final Pattern COMPONENT_CLASS = Pattern.compile(
            "(?s)(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern SELECTOR = Pattern.compile("selector\\s*:\\s*(['\"])(.*?)\\1", Pattern.DOTALL);

    private AngularComponentSelectorParser() {
    }

    static List<String> selectors(String source, String componentSymbol) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(componentSymbol)) {
            return List.of();
        }
        var decorator = COMPONENT_DECORATOR.matcher(source);
        while (decorator.find()) {
            var open = source.indexOf('(', decorator.start());
            var close = matching(source, open, '(', ')');
            if (close < 0) {
                continue;
            }
            var componentClass = COMPONENT_CLASS.matcher(source);
            componentClass.region(close + 1, source.length());
            if (!componentClass.find() || !componentSymbol.equals(componentClass.group(1))) {
                continue;
            }
            var selector = SELECTOR.matcher(source.substring(open + 1, close));
            if (!selector.find()) {
                return List.of();
            }
            return Arrays.stream(selector.group(2).split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private static int matching(String source, int start, char open, char close) {
        if (start < 0 || start >= source.length()) {
            return -1;
        }
        var depth = 0;
        var quote = '\0';
        var escaped = false;
        for (var index = start; index < source.length(); index++) {
            var current = source.charAt(index);
            if (quote != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }
}
