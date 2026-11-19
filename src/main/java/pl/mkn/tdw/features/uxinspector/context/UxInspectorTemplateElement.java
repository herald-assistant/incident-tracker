package pl.mkn.tdw.features.uxinspector.context;

import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptTemplateBinding;
import pl.mkn.tdw.integrations.gitlab.contract.frontend.GitLabTypeScriptTemplateBindingKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class UxInspectorTemplateElement {
    private static final Pattern TAG = Pattern.compile(
            "<!--(?s:.*?)(?:-->|\\z)|<(/?)([A-Za-z][A-Za-z0-9_.:-]*)(?=[\\s/>])"
                    + "((?:[^<>'\"]++|'[^']*'|\"[^\"]*\")*+)>"
    );
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([^\\s=/>\"']+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s'\">]+)))?"
    );
    private static final Set<String> VOID_TAGS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    final int offset;
    final String openingTag;
    final UxInspectorTemplateElement form;
    private final Map<String, String> attributes = new HashMap<>();
    private final StringBuilder text = new StringBuilder();

    private UxInspectorTemplateElement(int offset, String tag, String openingTag, String rawAttributes,
                                       UxInspectorTemplateElement form) {
        this.offset = offset;
        this.openingTag = openingTag;
        this.form = "form".equalsIgnoreCase(tag) ? this : form;
        var matcher = ATTRIBUTE.matcher(rawAttributes);
        while (matcher.find()) {
            var name = matcher.group(1);
            var value = matcher.group(2);
            if (value == null) value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            attributes.putIfAbsent(attributeKey(name), value == null ? "" : value);
        }
    }

    static List<UxInspectorTemplateElement> find(String template, String targetTag) {
        var elements = new ArrayList<UxInspectorTemplateElement>();
        var open = new ArrayDeque<UxInspectorTemplateElement>();
        var tags = TAG.matcher(template);
        UxInspectorTemplateElement form = null;
        var previousEnd = 0;
        while (tags.find()) {
            if (!open.isEmpty()) open.peek().text.append(template, previousEnd, tags.start()).append(' ');
            previousEnd = tags.end();
            var tag = tags.group(2);
            if (tag == null) continue; // HTML comment
            var isTarget = targetTag.equalsIgnoreCase(tag);
            var isForm = "form".equalsIgnoreCase(tag);
            if (!isTarget && !isForm) continue;
            if (!tags.group(1).isEmpty()) {
                if (isTarget && !open.isEmpty()) open.pop();
                if (isForm) form = null;
                continue;
            }
            var element = new UxInspectorTemplateElement(tags.start(), tag, tags.group(), tags.group(3), form);
            if (isForm) form = element;
            if (isTarget) {
                elements.add(element);
                if (!VOID_TAGS.contains(tag.toLowerCase(Locale.ROOT)) && !tags.group().endsWith("/>")) open.push(element);
            }
        }
        if (!open.isEmpty()) open.peek().text.append(template, previousEnd, template.length());
        return elements;
    }

    String text() {
        return WHITESPACE.matcher(text.toString().strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    boolean matches(String kind, String value) {
        if ("class".equals(kind)) {
            return attributes.containsKey("[class." + value + "]")
                    || Arrays.asList(WHITESPACE.split(attributes.getOrDefault("class", ""))).contains(value);
        }
        if (value.equals(attributes.get(kind))) return true;
        var bound = attributes.getOrDefault("[attr." + kind + "]", attributes.get("[" + kind + "]"));
        return bound != null && (bound.strip().equals("'" + value + "'") || bound.strip().equals("\"" + value + "\""));
    }

    boolean hasBinding(GitLabTypeScriptTemplateBinding binding) {
        if (binding.kind() == GitLabTypeScriptTemplateBindingKind.INTERPOLATION) {
            var interpolation = Pattern.compile("\\{\\{\\s*" + Pattern.quote(binding.expression()) + "\\s*}}");
            return interpolation.matcher(text).find() || interpolation.matcher(openingTag).find();
        }
        var attribute = switch (binding.kind()) {
            case EVENT -> "(" + binding.target() + ")";
            case PROPERTY -> "[" + binding.target() + "]";
            case TWO_WAY -> "[(" + binding.target() + ")]";
            case STRUCTURAL -> "*" + binding.target();
            case FORM_CONTROL -> "formcontrolname";
            case INTERPOLATION, CONTROL_FLOW -> null;
        };
        if (attribute == null) return false;
        var value = attributes.get(attributeKey(attribute));
        return value != null && binding.expression().equals(value.strip());
    }

    private static String attributeKey(String name) {
        return name.startsWith("[class.") ? name : name.toLowerCase(Locale.ROOT);
    }
}
