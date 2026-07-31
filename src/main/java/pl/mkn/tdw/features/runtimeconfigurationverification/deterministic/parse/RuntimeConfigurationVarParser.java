package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RuntimeConfigurationVarParser {

    private static final Pattern VARIABLE_BLOCK =
            Pattern.compile("^variable\\s+\"([A-Za-z0-9_.-]+)\"\\s*\\{$");
    private static final Pattern EMPTY_VARIABLE_BLOCK =
            Pattern.compile("^variable\\s+\"([A-Za-z0-9_.-]+)\"\\s*\\{\\s*}[,;]?$");
    private static final Pattern NAMED_BLOCK =
            Pattern.compile("^([A-Za-z0-9_.-]+)\\s*\\{$");
    private static final Pattern EMPTY_NAMED_BLOCK =
            Pattern.compile("^([A-Za-z0-9_.-]+)\\s*\\{\\s*}[,;]?$");
    private static final Pattern MAP_ASSIGNMENT =
            Pattern.compile("^([A-Za-z0-9_.-]+)\\s*[=:]\\s*\\{\\s*$");
    private static final Pattern EMPTY_MAP_ASSIGNMENT =
            Pattern.compile("^([A-Za-z0-9_.-]+)\\s*[=:]\\s*\\{\\s*}[,;]?$");
    private static final Pattern ASSIGNMENT =
            Pattern.compile("^([A-Za-z0-9_.-]+)\\s*[=:]\\s*(.+)$");

    public ParsedConfigurationFile parse(
            RuntimeConfigurationFileRole role,
            String path,
            String content
    ) {
        var issues = new ArrayList<ParsedConfigurationIssue>();
        var root = new LinkedHashMap<String, Object>();
        var stack = new ArrayDeque<Map<String, Object>>();
        stack.push(root);
        var lines = (content != null ? content : "").split("\\R", -1);

        for (var index = 0; index < lines.length; index++) {
            var lineNumber = index + 1;
            var line = stripComment(lines[index]).trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.matches("^}[,;]?$")) {
                if (stack.size() == 1) {
                    issues.add(new ParsedConfigurationIssue("VAR_UNEXPECTED_CLOSING_BRACE", "", lineNumber));
                } else {
                    stack.pop();
                }
                continue;
            }

            var emptyVariableMatcher = EMPTY_VARIABLE_BLOCK.matcher(line);
            if (emptyVariableMatcher.matches()) {
                var variables = childMap(root, "variable", issues, lineNumber);
                childMap(variables, emptyVariableMatcher.group(1), issues, lineNumber);
                continue;
            }

            var variableMatcher = VARIABLE_BLOCK.matcher(line);
            if (variableMatcher.matches()) {
                var variables = childMap(root, "variable", issues, lineNumber);
                var variable = childMap(variables, variableMatcher.group(1), issues, lineNumber);
                stack.push(variable);
                continue;
            }

            if (line.matches("^locals\\s*\\{\\s*}[,;]?$")) {
                childMap(root, "local", issues, lineNumber);
                continue;
            }

            if (line.equals("locals {")) {
                stack.push(childMap(root, "local", issues, lineNumber));
                continue;
            }

            var emptyMapAssignment = EMPTY_MAP_ASSIGNMENT.matcher(line);
            if (emptyMapAssignment.matches()) {
                childMap(stack.peek(), emptyMapAssignment.group(1), issues, lineNumber);
                continue;
            }

            var mapAssignment = MAP_ASSIGNMENT.matcher(line);
            if (mapAssignment.matches()) {
                stack.push(childMap(stack.peek(), mapAssignment.group(1), issues, lineNumber));
                continue;
            }

            var emptyNamedBlock = EMPTY_NAMED_BLOCK.matcher(line);
            if (emptyNamedBlock.matches()) {
                childMap(stack.peek(), emptyNamedBlock.group(1), issues, lineNumber);
                continue;
            }

            var namedBlock = NAMED_BLOCK.matcher(line);
            if (namedBlock.matches()) {
                stack.push(childMap(stack.peek(), namedBlock.group(1), issues, lineNumber));
                continue;
            }

            var assignment = ASSIGNMENT.matcher(line);
            if (assignment.matches()) {
                var key = assignment.group(1);
                var expression = assignment.group(2).trim();
                if (expression.startsWith("[") && !balanced(expression, '[', ']')) {
                    var collected = new StringBuilder(expression);
                    while (++index < lines.length) {
                        collected.append('\n').append(stripComment(lines[index]).trim());
                        if (balanced(collected.toString(), '[', ']')) {
                            break;
                        }
                    }
                    expression = collected.toString();
                }
                putValue(stack.peek(), key, parseExpression(expression, issues, lineNumber), issues, lineNumber);
                continue;
            }

            issues.add(new ParsedConfigurationIssue("VAR_UNSUPPORTED_SYNTAX", "", lineNumber));
        }

        if (stack.size() > 1) {
            issues.add(new ParsedConfigurationIssue("VAR_PARSE_ERROR", "", lines.length));
        }

        return new ParsedConfigurationFile(
                role,
                path,
                List.of(new ParsedConfigurationDocument(
                        0,
                        null,
                        ParsedConfigurationNodes.fromObject("document-0", "", root)
                )),
                issues
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(
            Map<String, Object> parent,
            String key,
            List<ParsedConfigurationIssue> issues,
            int line
    ) {
        var existing = parent.get(key);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (existing != null) {
            issues.add(new ParsedConfigurationIssue("VAR_DUPLICATE_KEY", key, line));
        }
        var child = new LinkedHashMap<String, Object>();
        parent.put(key, child);
        return child;
    }

    private void putValue(
            Map<String, Object> parent,
            String key,
            Object value,
            List<ParsedConfigurationIssue> issues,
            int line
    ) {
        if (parent.containsKey(key)) {
            issues.add(new ParsedConfigurationIssue("VAR_DUPLICATE_KEY", key, line));
        }
        parent.put(key, value);
    }

    private Object parseExpression(
            String expression,
            List<ParsedConfigurationIssue> issues,
            int line
    ) {
        var normalized = expression.stripTrailing().replaceFirst("[,;]$", "").trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            return unescape(normalized.substring(1, normalized.length() - 1));
        }
        if (normalized.equalsIgnoreCase("true") || normalized.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(normalized);
        }
        if (normalized.equalsIgnoreCase("null")) {
            return null;
        }
        if (normalized.matches("[-+]?\\d+(\\.\\d+)?")) {
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ignored) {
                return normalized;
            }
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return parseList(normalized.substring(1, normalized.length() - 1), issues, line);
        }
        if (normalized.startsWith("{") || normalized.endsWith("}")) {
            issues.add(new ParsedConfigurationIssue("VAR_UNSUPPORTED_EXPRESSION", "", line));
        }
        return normalized;
    }

    private List<Object> parseList(
            String content,
            List<ParsedConfigurationIssue> issues,
            int line
    ) {
        var values = new ArrayList<Object>();
        var current = new StringBuilder();
        var quoted = false;
        var escaped = false;
        for (var character : content.toCharArray()) {
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\' && quoted) {
                current.append(character);
                escaped = true;
            } else if (character == '"') {
                current.append(character);
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(parseExpression(current.toString().trim(), issues, line));
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (!current.toString().isBlank()) {
            values.add(parseExpression(current.toString().trim(), issues, line));
        }
        if (quoted) {
            issues.add(new ParsedConfigurationIssue("VAR_PARSE_ERROR", "", line));
        }
        return List.copyOf(values);
    }

    private String stripComment(String line) {
        var quoted = false;
        var escaped = false;
        for (var index = 0; index < line.length(); index++) {
            var character = line.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\' && quoted) {
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && character == '#') {
                return line.substring(0, index);
            }
            if (!quoted && character == '/' && index + 1 < line.length() && line.charAt(index + 1) == '/') {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private boolean balanced(String value, char open, char close) {
        var depth = 0;
        var quoted = false;
        for (var character : value.toCharArray()) {
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && character == open) {
                depth++;
            } else if (!quoted && character == close) {
                depth--;
            }
        }
        return depth == 0 && !quoted;
    }

    private String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
