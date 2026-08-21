package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class AngularTemplateBindingParser {

    private static final Pattern BOUND_ATTRIBUTE = Pattern.compile(
            "(?s)(\\[\\([^\\]\\)]+\\)\\]|\\([^\\)]+\\)|\\[[^\\]]+\\]|\\*[A-Za-z_$][A-Za-z0-9_$.-]*)"
                    + "\\s*=\\s*([\"'])(.*?)\\2"
    );
    private static final Pattern INTERPOLATION = Pattern.compile("(?s)\\{\\{(.*?)}}", Pattern.MULTILINE);
    private static final Pattern FORM_CONTROL = Pattern.compile(
            "(?s)(?<![\\[A-Za-z0-9_$-])formControlName\\s*=\\s*([\"'])(.*?)\\1"
    );
    private static final Pattern CONTROL_FLOW = Pattern.compile("@(if|for|switch)\\s*\\(");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern LOCAL = Pattern.compile("\\b(?:let|as)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern TEMPLATE_REFERENCE = Pattern.compile("#([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern TEMPLATE_LET_ATTRIBUTE = Pattern.compile(
            "\\blet-([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern FOR_LOCAL = Pattern.compile(
            "@for\\s*\\(\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s+of\\b"
    );
    private static final Pattern FOR_CONTEXT_LOCAL = Pattern.compile(
            "(?:\\blet\\s+|,\\s*)([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*"
                    + "\\$(?:index|count|first|last|even|odd)\\b"
    );
    private static final Set<String> RESERVED = Set.of(
            "true", "false", "null", "undefined", "this", "new", "typeof", "instanceof",
            "let", "as", "of", "track", "else", "if", "for", "switch", "case", "return",
            "async", "json", "keyvalue", "slice", "uppercase", "lowercase", "date", "number",
            "$event", "$index", "$count", "$first", "$last", "$even", "$odd"
    );

    List<GitLabTypeScriptTemplateBinding> parse(String template) {
        if (!StringUtils.hasText(template)) {
            return List.of();
        }
        var locals = localSymbols(template);
        var result = new ArrayList<GitLabTypeScriptTemplateBinding>();
        var attributes = BOUND_ATTRIBUTE.matcher(template);
        while (attributes.find()) {
            var rawTarget = attributes.group(1);
            var expression = attributes.group(3).strip();
            result.add(binding(
                    kind(rawTarget), target(rawTarget), expression,
                    referencedSymbols(expression, locals), lineNumber(template, attributes.start())
            ));
        }
        var interpolations = INTERPOLATION.matcher(template);
        while (interpolations.find()) {
            var expression = interpolations.group(1).strip();
            result.add(binding(
                    GitLabTypeScriptTemplateBindingKind.INTERPOLATION, "interpolation", expression,
                    referencedSymbols(expression, locals), lineNumber(template, interpolations.start())
            ));
        }
        var controls = FORM_CONTROL.matcher(template);
        while (controls.find()) {
            result.add(binding(
                    GitLabTypeScriptTemplateBindingKind.FORM_CONTROL, "formControlName", controls.group(2).strip(),
                    List.of(), lineNumber(template, controls.start())
            ));
        }
        result.addAll(controlFlowBindings(template, locals));
        return result.stream().distinct().toList();
    }

    private List<GitLabTypeScriptTemplateBinding> controlFlowBindings(String template, Set<String> locals) {
        var result = new ArrayList<GitLabTypeScriptTemplateBinding>();
        var matcher = CONTROL_FLOW.matcher(template);
        while (matcher.find()) {
            var open = template.indexOf('(', matcher.start());
            var close = matchingParenthesis(template, open);
            if (close < 0) {
                continue;
            }
            var expression = template.substring(open + 1, close).strip();
            result.add(binding(
                    GitLabTypeScriptTemplateBindingKind.CONTROL_FLOW,
                    matcher.group(1),
                    expression,
                    referencedSymbols(expression, locals),
                    lineNumber(template, matcher.start())
            ));
        }
        return result;
    }

    private int matchingParenthesis(String source, int open) {
        if (open < 0) return -1;
        var depth = 0;
        var quote = '\0';
        for (var index = open; index < source.length(); index++) {
            var current = source.charAt(index);
            if (quote != '\0') {
                if (current == quote && (index == 0 || source.charAt(index - 1) != '\\')) quote = '\0';
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private GitLabTypeScriptTemplateBinding binding(
            GitLabTypeScriptTemplateBindingKind kind,
            String target,
            String expression,
            List<String> referencedSymbols,
            int lineStart
    ) {
        return new GitLabTypeScriptTemplateBinding(kind, target, expression, referencedSymbols, lineStart);
    }

    private GitLabTypeScriptTemplateBindingKind kind(String target) {
        if (target.startsWith("[(")) return GitLabTypeScriptTemplateBindingKind.TWO_WAY;
        if (target.startsWith("(")) return GitLabTypeScriptTemplateBindingKind.EVENT;
        if (target.startsWith("[")) return GitLabTypeScriptTemplateBindingKind.PROPERTY;
        return GitLabTypeScriptTemplateBindingKind.STRUCTURAL;
    }

    private String target(String raw) {
        if (raw.startsWith("[(")) return raw.substring(2, raw.length() - 2);
        if (raw.startsWith("(") || raw.startsWith("[")) return raw.substring(1, raw.length() - 1);
        return raw.startsWith("*") ? raw.substring(1) : raw;
    }

    private Set<String> localSymbols(String template) {
        var result = new LinkedHashSet<String>();
        collect(LOCAL, template, result);
        collect(TEMPLATE_REFERENCE, template, result);
        collect(TEMPLATE_LET_ATTRIBUTE, template, result);
        collect(FOR_LOCAL, template, result);
        collect(FOR_CONTEXT_LOCAL, template, result);
        return result;
    }

    private void collect(Pattern pattern, String value, Set<String> target) {
        var matcher = pattern.matcher(value);
        while (matcher.find()) target.add(matcher.group(1));
    }

    private List<String> referencedSymbols(String expression, Set<String> locals) {
        var masked = maskStrings(expression);
        var result = new LinkedHashSet<String>();
        var matcher = IDENTIFIER.matcher(masked);
        while (matcher.find()) {
            var symbol = matcher.group();
            var normalized = symbol.toLowerCase(Locale.ROOT);
            var previous = previousNonWhitespace(masked, matcher.start() - 1);
            var next = nextNonWhitespace(masked, matcher.end());
            if (previous == '.' || next == ':' || RESERVED.contains(normalized)
                    || locals.contains(symbol) || isPipeName(masked, matcher.start())) {
                continue;
            }
            result.add(symbol);
        }
        return List.copyOf(result);
    }

    private boolean isPipeName(String expression, int symbolStart) {
        for (var cursor = symbolStart - 1; cursor >= 0; cursor--) {
            var current = expression.charAt(cursor);
            if (Character.isWhitespace(current)) {
                continue;
            }
            return current == '|' && (cursor == 0 || expression.charAt(cursor - 1) != '|');
        }
        return false;
    }

    private String maskStrings(String value) {
        var result = new StringBuilder(value);
        var quote = '\0';
        for (var index = 0; index < value.length(); index++) {
            var current = value.charAt(index);
            if (quote == '\0' && (current == '\'' || current == '"' || current == '`')) {
                quote = current;
                result.setCharAt(index, ' ');
            } else if (quote != '\0') {
                result.setCharAt(index, ' ');
                if (current == quote && (index == 0 || value.charAt(index - 1) != '\\')) quote = '\0';
            }
        }
        return result.toString();
    }

    private char previousNonWhitespace(String value, int index) {
        for (var cursor = index; cursor >= 0; cursor--) {
            if (!Character.isWhitespace(value.charAt(cursor))) return value.charAt(cursor);
        }
        return '\0';
    }

    private char nextNonWhitespace(String value, int index) {
        for (var cursor = index; cursor < value.length(); cursor++) {
            if (!Character.isWhitespace(value.charAt(cursor))) return value.charAt(cursor);
        }
        return '\0';
    }

    private int lineNumber(String source, int offset) {
        var line = 1;
        for (var index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return line;
    }
}
