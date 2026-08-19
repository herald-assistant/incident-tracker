package pl.mkn.tdw.integrations.gitlab.frontend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GitLabTypeScriptSymbolSliceService {

    public static final int DEFAULT_OUTPUT_CHARACTERS = 12_000;
    public static final int MAX_OUTPUT_CHARACTERS = 40_000;

    private static final int MAX_SOURCE_CHARACTERS = 200_000;
    private static final int MAX_INCLUDED_HELPERS = 12;
    private static final int MAX_CANDIDATES = 60;
    private static final Pattern IMPORT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?"
    );
    private static final Pattern CLASS = Pattern.compile(
            "(?m)(?:^|\\n)\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)[^\\{]*\\{"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final Pattern THIS_MEMBER_CALL = Pattern.compile(
            "\\bthis\\.([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
    );

    private final GitLabRepositoryPort repositoryPort;

    public GitLabTypeScriptSymbolSliceResponse readSymbolSlice(GitLabTypeScriptSymbolSliceRequest request) {
        var limitations = new LinkedHashSet<String>();
        if (request.symbolSelectors().isEmpty()) {
            limitations.add("At least one TypeScript symbol selector is required.");
            return empty(request, "INVALID_REQUEST", 0, 0, List.copyOf(limitations));
        }
        if (!inScope(request.scope(), request.filePath())) {
            limitations.add("TypeScript source is outside the configured code-search scope.");
            return empty(request, "OUT_OF_SCOPE", 0, 0, List.copyOf(limitations));
        }

        final String source;
        try {
            var file = repositoryPort.readFile(
                    request.scope().group(), request.scope().projectName(), request.scope().ref(),
                    request.filePath(), MAX_SOURCE_CHARACTERS
            );
            if (file == null || file.content() == null) {
                limitations.add("TypeScript source could not be read.");
                return empty(request, "READ_FAILED", 0, 0, List.copyOf(limitations));
            }
            if (file.truncated()) {
                limitations.add("TypeScript source exceeded the parser input limit.");
                return empty(request, "SOURCE_TRUNCATED", file.content().length(), lineCount(file.content()),
                        List.copyOf(limitations));
            }
            source = file.content();
        } catch (RuntimeException exception) {
            limitations.add("TypeScript source could not be read: " + safeMessage(exception));
            return empty(request, "READ_FAILED", 0, 0, List.copyOf(limitations));
        }

        var parsed = parse(source, limitations);
        var candidates = parsed.members().stream()
                .map(member -> candidate(source, member))
                .sorted(Comparator.comparingInt(GitLabTypeScriptSymbolCandidate::lineStart))
                .toList();
        var selected = select(request, source, parsed.members(), limitations);
        if (selected.isEmpty()) {
            limitations.add("No TypeScript symbol matched requested selectors.");
            return response(request, "NOT_FOUND", source, null, List.of(), List.of(), List.of(),
                    List.of(), candidates.stream().limit(MAX_CANDIDATES).toList(), 0, 0, 0, false,
                    "", List.copyOf(limitations));
        }

        var declaringTypes = selected.stream().map(Member::declaringType).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (declaringTypes.size() > 1) {
            limitations.add("Selected symbols belong to more than one declaring type. Narrow declaringTypeName.");
            return response(request, "MULTIPLE_DECLARING_TYPES", source, null, List.of(), List.of(), List.of(),
                    List.of(), candidates.stream().limit(MAX_CANDIDATES).toList(), 0, 0, 0, false,
                    "", List.copyOf(limitations));
        }

        var declaringType = declaringTypes.iterator().next();
        var includedSymbols = includeHelpers(source, parsed.members(), selected,
                Boolean.TRUE.equals(request.includeLocalHelpers()));
        var fields = parsed.members().stream()
                .filter(member -> java.util.Objects.equals(declaringType, member.declaringType()))
                .filter(member -> member.kind() == MemberKind.FIELD || member.kind() == MemberKind.CONSTRUCTOR)
                .toList();
        var includedFields = Boolean.TRUE.equals(request.includeRelevantFields())
                ? relevantFields(source, fields, includedSymbols)
                : List.<Member>of();
        var retainedMembers = new ArrayList<Member>();
        retainedMembers.addAll(includedFields);
        retainedMembers.addAll(includedSymbols);
        retainedMembers = retainedMembers.stream().distinct().sorted(Comparator.comparingInt(Member::start)).collect(
                java.util.stream.Collectors.toCollection(ArrayList::new));

        var imports = parsed.imports();
        var semanticContent = retainedMembers.stream().map(member -> source.substring(member.start(), member.end()))
                .collect(java.util.stream.Collectors.joining("\n"));
        var usedIdentifiers = identifiers(semanticContent + " " + value(declaringType));
        var includedImports = Boolean.TRUE.equals(request.includeRelevantImports())
                ? imports.stream().filter(candidate -> candidate.identifiers().stream().anyMatch(usedIdentifiers::contains)).toList()
                : List.<ImportStatement>of();
        var downstream = downstreamReferences(request, source, includedSymbols, includedFields, imports);
        var classInfo = parsed.classes().stream().filter(candidate -> candidate.name().equals(declaringType)).findFirst().orElse(null);
        var retainedFieldMembers = new LinkedHashSet<>(includedFields);
        includedSymbols.stream().filter(member -> member.kind() == MemberKind.FIELD).forEach(retainedFieldMembers::add);
        var omittedFields = Math.max(0, fields.size() - retainedFieldMembers.size());
        var symbolPool = parsed.members().stream()
                .filter(member -> java.util.Objects.equals(declaringType, member.declaringType()))
                .filter(member -> member.kind() != MemberKind.FIELD && member.kind() != MemberKind.CONSTRUCTOR)
                .toList();
        var includedNonFields = includedSymbols.stream().filter(member -> member.kind() != MemberKind.FIELD).count();
        var omittedSymbols = Math.max(0, symbolPool.size() - (int) includedNonFields);
        var omittedImports = Math.max(0, imports.size() - includedImports.size());
        var rendered = render(source, classInfo, retainedMembers, includedImports, omittedImports, omittedFields,
                omittedSymbols);
        var maxCharacters = normalizeLimit(request.maxCharacters());
        var truncated = rendered.length() > maxCharacters;
        if (truncated) {
            rendered = rendered.substring(0, Math.max(0, maxCharacters - 48))
                    + "\n// ... TypeScript symbol slice truncated ...";
            limitations.add("TypeScript symbol slice reached maxCharacters=" + maxCharacters + ".");
        }
        return response(
                request, truncated ? "PARTIAL" : "OK", source, declaringType, includedImports, includedFields,
                includedSymbols, downstream, candidates.stream().limit(MAX_CANDIDATES).toList(), omittedImports,
                omittedFields, omittedSymbols, truncated, rendered, List.copyOf(limitations)
        );
    }

    private ParsedSource parse(String source, Set<String> limitations) {
        var mask = mask(source);
        var imports = imports(source);
        var classes = new ArrayList<ClassInfo>();
        var members = new ArrayList<Member>();
        var matcher = CLASS.matcher(mask);
        while (matcher.find()) {
            var open = mask.lastIndexOf('{', matcher.end() - 1);
            var close = matching(mask, open, '{', '}');
            if (close < 0) {
                limitations.add("Class " + matcher.group(1) + " has no statically matched closing brace.");
                continue;
            }
            var classInfo = new ClassInfo(matcher.group(1), matcher.start(), open, close);
            classes.add(classInfo);
            members.addAll(classMembers(source, mask, classInfo));
        }
        members.addAll(topLevelFunctions(source, mask, classes));
        members.addAll(topLevelProperties(source, mask, classes));
        return new ParsedSource(List.copyOf(imports), List.copyOf(classes), List.copyOf(members));
    }

    private List<Member> classMembers(String source, String mask, ClassInfo owner) {
        var result = new ArrayList<Member>();
        var cursor = owner.openBrace() + 1;
        while (cursor < owner.closeBrace()) {
            while (cursor < owner.closeBrace() && Character.isWhitespace(mask.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= owner.closeBrace()) {
                break;
            }
            var start = cursor;
            var round = 0;
            var square = 0;
            var end = -1;
            var boundary = -1;
            for (var index = cursor; index < owner.closeBrace(); index++) {
                var character = mask.charAt(index);
                if (character == '(') round++;
                else if (character == ')') round = Math.max(0, round - 1);
                else if (character == '[') square++;
                else if (character == ']') square = Math.max(0, square - 1);
                else if (character == ';' && round == 0 && square == 0) {
                    boundary = index;
                    end = index + 1;
                    break;
                } else if (character == '{' && round == 0 && square == 0) {
                    boundary = index;
                    var close = matching(mask, index, '{', '}');
                    if (close < 0 || close > owner.closeBrace()) {
                        end = owner.closeBrace();
                    } else {
                        end = close + 1;
                        while (end < owner.closeBrace() && Character.isWhitespace(mask.charAt(end))) end++;
                        if (end < owner.closeBrace() && mask.charAt(end) == ';') end++;
                    }
                    break;
                }
            }
            if (end <= start) {
                break;
            }
            var header = source.substring(start, boundary >= start ? boundary : end).strip();
            var classified = classifyMember(header);
            if (classified != null) {
                result.add(new Member(owner.name(), classified.name(), classified.kind(), start, end, header));
            }
            cursor = end;
        }
        return result;
    }

    private ClassifiedMember classifyMember(String header) {
        var normalized = header.replaceAll("(?m)^\\s*@[^\\n]+", "").strip();
        if (normalized.matches("(?s).*\\bconstructor\\s*\\(.*")) {
            return new ClassifiedMember("constructor", MemberKind.CONSTRUCTOR);
        }
        var getter = Pattern.compile("\\bget\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(").matcher(normalized);
        if (getter.find()) return new ClassifiedMember(getter.group(1), MemberKind.GETTER);
        var setter = Pattern.compile("\\bset\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(").matcher(normalized);
        if (setter.find()) return new ClassifiedMember(setter.group(1), MemberKind.SETTER);
        var assignment = normalized.indexOf('=');
        var method = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:<[^>]+>)?\\s*\\(").matcher(normalized);
        String lastMethod = null;
        var lastMethodStart = -1;
        while (method.find()) {
            lastMethod = method.group(1);
            lastMethodStart = method.start();
        }
        if (lastMethod != null && (assignment < 0 || lastMethodStart < assignment)) {
            return new ClassifiedMember(lastMethod, MemberKind.METHOD);
        }
        var field = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:[?!])?\\s*(?::[^=]+)?(?:=|$)")
                .matcher(normalized);
        String fieldName = null;
        while (field.find()) fieldName = field.group(1);
        return fieldName != null ? new ClassifiedMember(fieldName, MemberKind.FIELD) : null;
    }

    private List<Member> topLevelFunctions(String source, String mask, List<ClassInfo> classes) {
        var result = new ArrayList<Member>();
        var matcher = Pattern.compile("(?m)(?:^|\\n)\\s*(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][A-Za-z0-9_$]*)[^\\{]*\\{")
                .matcher(mask);
        while (matcher.find()) {
            if (insideClass(matcher.start(), classes)) continue;
            var open = mask.lastIndexOf('{', matcher.end() - 1);
            var close = matching(mask, open, '{', '}');
            if (close > open) {
                result.add(new Member(null, matcher.group(1), MemberKind.FUNCTION, matcher.start(), close + 1,
                        source.substring(matcher.start(), open).strip()));
            }
        }
        return result;
    }

    private List<Member> topLevelProperties(String source, String mask, List<ClassInfo> classes) {
        var result = new ArrayList<Member>();
        var matcher = Pattern.compile(
                "(?m)(?:^|\\n)\\s*(?:export\\s+)?const\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?::[^=;]+)?="
        ).matcher(mask);
        while (matcher.find()) {
            if (insideClass(matcher.start(), classes)) continue;
            var round = 0;
            var square = 0;
            var curly = 0;
            var end = -1;
            for (var index = matcher.end(); index < mask.length(); index++) {
                var character = mask.charAt(index);
                if (character == '(') round++;
                else if (character == ')') round = Math.max(0, round - 1);
                else if (character == '[') square++;
                else if (character == ']') square = Math.max(0, square - 1);
                else if (character == '{') curly++;
                else if (character == '}') curly = Math.max(0, curly - 1);
                else if (character == ';' && round == 0 && square == 0 && curly == 0) {
                    end = index + 1;
                    break;
                }
            }
            if (end > matcher.start()) {
                result.add(new Member(null, matcher.group(1), MemberKind.FIELD, matcher.start(), end,
                        source.substring(matcher.start(), matcher.end()).strip()));
            }
        }
        return result;
    }

    private List<Member> select(
            GitLabTypeScriptSymbolSliceRequest request,
            String source,
            List<Member> members,
            Set<String> limitations
    ) {
        var result = new LinkedHashSet<Member>();
        for (var selector : request.symbolSelectors()) {
            var matches = members.stream()
                    .filter(member -> selector.name().equals(member.name()))
                    .filter(member -> !StringUtils.hasText(request.declaringTypeName())
                            || request.declaringTypeName().equals(member.declaringType()))
                    .filter(member -> kindMatches(selector.kind(), member.kind()))
                    .filter(member -> selector.lineStart() == null
                            || selector.lineStart() >= lineNumber(source, member.start())
                            && selector.lineStart() <= lineNumber(source, member.end()))
                    .toList();
            if (matches.isEmpty()) {
                limitations.add("No symbol matched selector " + selector.kind() + ":" + selector.name() + ".");
            }
            result.addAll(matches);
        }
        return result.stream().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<Member> includeHelpers(String source, List<Member> all, List<Member> selected, boolean enabled) {
        var included = new LinkedHashSet<>(selected);
        if (!enabled) return List.copyOf(included);
        var cursor = 0;
        while (cursor < included.size() && included.size() < selected.size() + MAX_INCLUDED_HELPERS) {
            var current = new ArrayList<>(included).get(cursor++);
            var content = source.substring(current.start(), current.end());
            var calls = new LinkedHashSet<String>();
            var matcher = Pattern.compile("(?<![.\\w])(?:this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(").matcher(content);
            while (matcher.find()) calls.add(matcher.group(1));
            all.stream()
                    .filter(candidate -> java.util.Objects.equals(current.declaringType(), candidate.declaringType()))
                    .filter(candidate -> candidate.kind() != MemberKind.FIELD && candidate.kind() != MemberKind.CONSTRUCTOR)
                    .filter(candidate -> calls.contains(candidate.name()))
                    .forEach(included::add);
        }
        return included.stream().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<Member> relevantFields(String source, List<Member> fields, List<Member> symbols) {
        var semantic = symbols.stream().map(member -> source.substring(member.start(), member.end()))
                .collect(java.util.stream.Collectors.joining("\n"));
        var identifiers = identifiers(semantic);
        var directFields = fields.stream()
                .filter(member -> member.kind() == MemberKind.FIELD && identifiers.contains(member.name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var constructor = fields.stream().filter(member -> member.kind() == MemberKind.CONSTRUCTOR).findFirst().orElse(null);
        if (constructor != null) {
            var constructorSource = source.substring(constructor.start(), constructor.end());
            var parameterProperty = Pattern.compile("(?:private|protected|public)\\s+(?:readonly\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)")
                    .matcher(constructorSource);
            while (parameterProperty.find()) {
                if (identifiers.contains(parameterProperty.group(1))) {
                    directFields.add(constructor);
                    break;
                }
            }
        }
        return directFields.stream().distinct().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<GitLabTypeScriptDownstreamReference> downstreamReferences(
            GitLabTypeScriptSymbolSliceRequest request,
            String source,
            List<Member> symbols,
            List<Member> fields,
            List<ImportStatement> imports
    ) {
        var fieldTypes = fieldTypes(source, fields);
        var importByIdentifier = new LinkedHashMap<String, ImportStatement>();
        imports.forEach(candidate -> candidate.identifiers().forEach(identifier -> importByIdentifier.put(identifier, candidate)));
        var result = new LinkedHashMap<String, GitLabTypeScriptDownstreamReference>();
        for (var symbol : symbols) {
            var content = source.substring(symbol.start(), symbol.end());
            var matcher = THIS_MEMBER_CALL.matcher(content);
            while (matcher.find()) {
                var owner = matcher.group(1);
                var member = matcher.group(2);
                var target = fieldTypes.get(owner);
                var imported = target != null ? importByIdentifier.get(target) : null;
                var reference = new GitLabTypeScriptDownstreamReference(
                        symbol.name(), owner, member, target,
                        imported != null ? imported.moduleSpecifier() : null,
                        null
                );
                result.put(symbol.name() + "|" + owner + "|" + member, reference);
            }
        }
        return List.copyOf(result.values());
    }

    private Map<String, String> fieldTypes(String source, List<Member> fields) {
        var result = new LinkedHashMap<String, String>();
        for (var field : fields) {
            var content = source.substring(field.start(), field.end());
            var typed = Pattern.compile("(?:private|protected|public)?\\s*(?:readonly\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*[?!]?\\s*:\\s*([A-Za-z_$][A-Za-z0-9_$]*)")
                    .matcher(content);
            while (typed.find()) result.put(typed.group(1), typed.group(2));
            var injected = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*inject\\s*\\(\\s*([A-Za-z_$][A-Za-z0-9_$]*)")
                    .matcher(content);
            while (injected.find()) result.put(injected.group(1), injected.group(2));
        }
        return result;
    }

    private String render(
            String source,
            ClassInfo owner,
            List<Member> members,
            List<ImportStatement> imports,
            int omittedImports,
            int omittedFields,
            int omittedSymbols
    ) {
        var builder = new StringBuilder();
        imports.forEach(candidate -> builder.append(candidate.statement().strip()).append('\n'));
        if (omittedImports > 0) builder.append("// ... ").append(omittedImports).append(" unrelated imports omitted ...\n");
        if (owner != null) {
            builder.append(source, owner.start(), owner.openBrace() + 1).append('\n');
            if (omittedFields > 0) builder.append("  // ... ").append(omittedFields).append(" unrelated fields omitted ...\n");
            if (omittedSymbols > 0) builder.append("  // ... ").append(omittedSymbols).append(" unrelated symbols omitted ...\n");
            members.forEach(member -> builder.append(source, member.start(), member.end()).append('\n'));
            builder.append('}');
        } else {
            if (omittedSymbols > 0) builder.append("// ... ").append(omittedSymbols).append(" unrelated symbols omitted ...\n");
            members.forEach(member -> builder.append(source, member.start(), member.end()).append('\n'));
        }
        return builder.toString().stripTrailing();
    }

    private List<ImportStatement> imports(String source) {
        var result = new ArrayList<ImportStatement>();
        var matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            var bindings = matcher.group(1) != null ? matcher.group(1) : "";
            result.add(new ImportStatement(matcher.group(), matcher.group(2), identifiers(bindings)));
        }
        return result;
    }

    private GitLabTypeScriptSymbolCandidate candidate(String source, Member member) {
        var signature = member.header().replaceAll("\\s+", " ").strip();
        if (signature.length() > 240) signature = signature.substring(0, 237) + "...";
        return new GitLabTypeScriptSymbolCandidate(
                member.declaringType(), member.name(), publicKind(member.kind()), signature,
                lineNumber(source, member.start()), lineNumber(source, Math.max(member.start(), member.end() - 1))
        );
    }

    private GitLabTypeScriptSymbolSliceResponse response(
            GitLabTypeScriptSymbolSliceRequest request,
            String status,
            String source,
            String declaringType,
            List<ImportStatement> imports,
            List<Member> fields,
            List<Member> symbols,
            List<GitLabTypeScriptDownstreamReference> downstream,
            List<GitLabTypeScriptSymbolCandidate> candidates,
            int omittedImports,
            int omittedFields,
            int omittedSymbols,
            boolean truncated,
            String content,
            List<String> limitations
    ) {
        var retained = new ArrayList<Member>();
        retained.addAll(fields);
        retained.addAll(symbols);
        var lineStart = retained.stream().mapToInt(member -> lineNumber(source, member.start())).min().orElse(0);
        var lineEnd = retained.stream().mapToInt(member -> lineNumber(source, Math.max(member.start(), member.end() - 1))).max().orElse(0);
        return new GitLabTypeScriptSymbolSliceResponse(
                request.scope(), request.filePath(), status, declaringType, lineStart, lineEnd, lineCount(source),
                source.length(), content, content.length(), Math.max(0, source.length() - content.length()), truncated,
                imports.stream().map(ImportStatement::statement).toList(),
                fields.stream().filter(field -> field.kind() == MemberKind.FIELD).map(Member::name).toList(),
                symbols.stream().map(member -> candidate(source, member)).toList(),
                omittedImports, omittedFields, omittedSymbols, downstream, candidates, limitations
        );
    }

    private GitLabTypeScriptSymbolSliceResponse empty(
            GitLabTypeScriptSymbolSliceRequest request,
            String status,
            int sourceCharacters,
            int totalLines,
            List<String> limitations
    ) {
        return new GitLabTypeScriptSymbolSliceResponse(
                request.scope(), request.filePath(), status, request.declaringTypeName(), 0, 0, totalLines,
                sourceCharacters, "", 0, sourceCharacters, false, List.of(), List.of(), List.of(), 0, 0, 0,
                List.of(), List.of(), limitations
        );
    }

    private boolean kindMatches(GitLabTypeScriptSymbolKind requested, MemberKind actual) {
        if (requested == GitLabTypeScriptSymbolKind.AUTO) return true;
        return requested == publicKind(actual);
    }

    private GitLabTypeScriptSymbolKind publicKind(MemberKind kind) {
        return switch (kind) {
            case METHOD -> GitLabTypeScriptSymbolKind.METHOD;
            case FIELD -> GitLabTypeScriptSymbolKind.PROPERTY;
            case GETTER -> GitLabTypeScriptSymbolKind.GETTER;
            case SETTER -> GitLabTypeScriptSymbolKind.SETTER;
            case CONSTRUCTOR -> GitLabTypeScriptSymbolKind.CONSTRUCTOR;
            case FUNCTION -> GitLabTypeScriptSymbolKind.FUNCTION;
        };
    }

    private String mask(String source) {
        var masked = new StringBuilder(source);
        var state = MaskState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (state == MaskState.CODE) {
                if (current == '/' && next == '/') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.BLOCK_COMMENT;
                } else if (current == '\'') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.SINGLE;
                } else if (current == '"') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.DOUBLE;
                } else if (current == '`') {
                    masked.setCharAt(index, ' ');
                    state = MaskState.TEMPLATE;
                }
            } else {
                if (current != '\n' && current != '\r') masked.setCharAt(index, ' ');
                if (state == MaskState.LINE_COMMENT && (current == '\n' || current == '\r')) state = MaskState.CODE;
                else if (state == MaskState.BLOCK_COMMENT && current == '*' && next == '/') {
                    if (index + 1 < masked.length()) masked.setCharAt(index + 1, ' ');
                    index++;
                    state = MaskState.CODE;
                } else if ((state == MaskState.SINGLE && current == '\'')
                        || (state == MaskState.DOUBLE && current == '"')
                        || (state == MaskState.TEMPLATE && current == '`')) {
                    var escaped = index > 0 && source.charAt(index - 1) == '\\';
                    if (!escaped) state = MaskState.CODE;
                }
            }
        }
        return masked.toString();
    }

    private int matching(String source, int open, char opening, char closing) {
        if (open < 0 || open >= source.length() || source.charAt(open) != opening) return -1;
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            if (source.charAt(index) == opening) depth++;
            else if (source.charAt(index) == closing && --depth == 0) return index;
        }
        return -1;
    }

    private Set<String> identifiers(String value) {
        var result = new LinkedHashSet<String>();
        var matcher = IDENTIFIER.matcher(value != null ? value : "");
        while (matcher.find()) {
            var identifier = matcher.group();
            if (!Set.of("import", "from", "as", "type").contains(identifier.toLowerCase(Locale.ROOT))) {
                result.add(identifier);
            }
        }
        return result;
    }

    private int lineNumber(String source, int offset) {
        var line = 1;
        for (var index = 0; index < Math.min(offset, source.length()); index++) if (source.charAt(index) == '\n') line++;
        return line;
    }

    private int lineCount(String source) {
        return source.isEmpty() ? 0 : lineNumber(source, source.length());
    }

    private boolean insideClass(int offset, List<ClassInfo> classes) {
        return classes.stream().anyMatch(owner -> offset >= owner.start() && offset <= owner.closeBrace());
    }

    private boolean inScope(GitLabFrontendRepositoryScope scope, String path) {
        return scope.pathPrefixes().isEmpty() || scope.pathPrefixes().stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private int normalizeLimit(Integer value) {
        return value == null ? DEFAULT_OUTPUT_CHARACTERS : Math.max(1_000, Math.min(MAX_OUTPUT_CHARACTERS, value));
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private String value(String value) {
        return value != null ? value : "";
    }

    private enum MaskState {CODE, LINE_COMMENT, BLOCK_COMMENT, SINGLE, DOUBLE, TEMPLATE}

    private enum MemberKind {METHOD, FIELD, GETTER, SETTER, CONSTRUCTOR, FUNCTION}

    private record ImportStatement(String statement, String moduleSpecifier, Set<String> identifiers) {
    }

    private record ClassInfo(String name, int start, int openBrace, int closeBrace) {
    }

    private record Member(String declaringType, String name, MemberKind kind, int start, int end, String header) {
    }

    private record ClassifiedMember(String name, MemberKind kind) {
    }

    private record ParsedSource(List<ImportStatement> imports, List<ClassInfo> classes, List<Member> members) {
    }
}
