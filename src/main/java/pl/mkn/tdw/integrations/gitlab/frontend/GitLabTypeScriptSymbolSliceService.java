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
    public static final int MAX_OUTPUT_CHARACTERS = 200_000;

    private static final int MAX_SOURCE_CHARACTERS = 200_000;
    private static final int MAX_CANDIDATES = 60;
    private static final Pattern IMPORT = Pattern.compile(
            "(?ms)^\\s*import\\s+(?!\\()(?:(?:type\\s+)?(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?"
    );
    private static final Pattern CLASS = Pattern.compile(
            "(?m)(?:^|\\n)\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)[^\\{]*\\{"
    );
    private static final Pattern IDENTIFIER = Pattern.compile(
            "(?<![A-Za-z0-9_$])[A-Za-z_$][A-Za-z0-9_$]*(?![A-Za-z0-9_$])"
    );
    private static final Pattern THIS_MEMBER_CALL = Pattern.compile(
            "\\bthis\\.([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
    );
    private static final Pattern THIS_MEMBER_ACCESS = Pattern.compile(
            "\\bthis\\.([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "(?![A-Za-z0-9_$])(?!\\s*\\()"
    );
    private static final Pattern QUALIFIED_CALL = Pattern.compile(
            "(?<![.\\w])([A-Za-z_$][A-Za-z0-9_$]*)\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
    );
    private static final Pattern DIRECT_CALL = Pattern.compile(
            "(?<![.\\w])([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
    );
    private static final Pattern TEMPLATE_URL = Pattern.compile("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern INLINE_TEMPLATE_START = Pattern.compile("template\\s*:\\s*([`'\"])");
    private static final Set<String> ANGULAR_LIFECYCLE = Set.of(
            "ngOnChanges", "ngOnInit", "ngDoCheck", "ngAfterContentInit", "ngAfterContentChecked",
            "ngAfterViewInit", "ngAfterViewChecked", "ngOnDestroy"
    );

    private final GitLabRepositoryPort repositoryPort;

    public GitLabTypeScriptSymbolSliceResponse readSymbolSlice(GitLabTypeScriptSymbolSliceRequest request) {
        var limitations = new LinkedHashSet<String>();
        var templateRequested = Boolean.TRUE.equals(request.includeTemplateBindings());
        if (request.symbolSelectors().isEmpty() && !templateRequested) {
            limitations.add("At least one TypeScript symbol selector or template-driven discovery is required.");
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
        var template = templateContext(request, source, limitations);
        var requestedClass = selectedClass(request, parsed);
        var inheritedSymbols = inheritedSymbols(request, source, parsed, template, requestedClass);
        var candidates = parsed.members().stream()
                .map(member -> candidate(source, member))
                .sorted(Comparator.comparingInt(GitLabTypeScriptSymbolCandidate::lineStart))
                .toList();
        var selected = new LinkedHashSet<>(select(
                request, source, parsed.members(), inheritedSymbols, limitations
        ));
        selected.addAll(templateEntrySymbols(
                request, source, parsed, template, inheritedSymbols, limitations
        ));
        var inheritedReferences = inheritedReferences(request, requestedClass, inheritedSymbols, parsed.imports());
        var classReferenceRequested = requestedClass != null && request.symbolSelectors().stream()
                .anyMatch(selector -> requestedClass.name().equals(selector.name()));
        var unresolvedRequestedSelector = limitations.stream()
                .anyMatch(limitation -> limitation.startsWith("No symbol matched selector "));
        var unresolvedTemplateReference = limitations.stream()
                .anyMatch(limitation -> limitation.startsWith("Template references without a matching member"));
        if (selected.isEmpty() && inheritedReferences.isEmpty() && !classReferenceRequested
                && templateRequested && template.complete() && !unresolvedTemplateReference
                && request.symbolSelectors().isEmpty()) {
            var declaringType = StringUtils.hasText(request.declaringTypeName())
                    ? request.declaringTypeName()
                    : parsed.classes().size() == 1 ? parsed.classes().get(0).name() : null;
            return response(request, "STATIC_PRESENTATIONAL", source, declaringType,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    candidates.stream().limit(MAX_CANDIDATES).toList(),
                    parsed.imports().size(), 0, 0, false, "", template, List.copyOf(limitations));
        }
        if (selected.isEmpty() && inheritedReferences.isEmpty() && !classReferenceRequested) {
            limitations.add("No TypeScript symbol matched requested selectors.");
            return response(request, "NOT_FOUND", source, null, List.of(), List.of(), List.of(),
                    List.of(), List.of(), candidates.stream().limit(MAX_CANDIDATES).toList(), 0, 0, 0, false,
                    "", template, List.copyOf(limitations));
        }

        var entrySymbols = selected.stream().sorted(Comparator.comparingInt(Member::start)).toList();
        if (entrySymbols.isEmpty() && requestedClass != null) {
            var includedImports = importsForBase(parsed.imports(), requestedClass.baseType());
            var omittedImportCount = Math.max(0, parsed.imports().size() - includedImports.size());
            var omittedFieldCount = parsed.members().stream()
                    .filter(member -> requestedClass.name().equals(member.declaringType()))
                    .filter(member -> member.kind() == MemberKind.FIELD
                            || member.kind() == MemberKind.CONSTRUCTOR).toList().size();
            var omittedSymbolCount = parsed.members().stream()
                    .filter(member -> requestedClass.name().equals(member.declaringType()))
                    .filter(member -> member.kind() != MemberKind.FIELD
                            && member.kind() != MemberKind.CONSTRUCTOR).toList().size();
            var rendered = render(
                    source, requestedClass, List.of(), includedImports,
                    omittedImportCount, omittedFieldCount, omittedSymbolCount
            );
            return response(
                    request, "OK", source, requestedClass.name(), includedImports, List.of(),
                    List.of(), List.of(), inheritedReferences, candidates.stream().limit(MAX_CANDIDATES).toList(),
                    omittedImportCount, omittedFieldCount, omittedSymbolCount,
                    false, rendered, template, List.copyOf(limitations)
            );
        }
        var declaringTypes = entrySymbols.stream().map(Member::declaringType).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (declaringTypes.size() > 1) {
            limitations.add("Selected symbols belong to more than one declaring type. Narrow declaringTypeName.");
            return response(request, "MULTIPLE_DECLARING_TYPES", source, null, List.of(), List.of(), List.of(),
                    List.of(), List.of(), candidates.stream().limit(MAX_CANDIDATES).toList(), 0, 0, 0, false,
                    "", template, List.copyOf(limitations));
        }

        var declaringType = declaringTypes.iterator().next();
        var classInfo = parsed.classes().stream()
                .filter(candidate -> candidate.name().equals(declaringType))
                .findFirst().orElse(null);
        var includedSymbols = includeHelpers(source, parsed.members(), entrySymbols,
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
        var usedIdentifiers = identifiers(semanticContent + " " + value(declaringType)
                + " " + (classInfo != null ? value(classInfo.baseType()) : ""));
        var includedImports = Boolean.TRUE.equals(request.includeRelevantImports())
                ? imports.stream().filter(candidate -> candidate.identifiers().stream().anyMatch(usedIdentifiers::contains)).toList()
                : List.<ImportStatement>of();
        var downstream = new ArrayList<>(downstreamReferences(source, retainedMembers, imports));
        inheritedReferences.stream().filter(reference -> !downstream.contains(reference)).forEach(downstream::add);
        var retainedFieldMembers = new LinkedHashSet<>(includedFields);
        includedSymbols.stream().filter(member -> member.kind() == MemberKind.FIELD).forEach(retainedFieldMembers::add);
        var responseFields = retainedFieldMembers.stream().sorted(Comparator.comparingInt(Member::start)).toList();
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
        var unresolvedTemplateBindings = limitations.stream()
                .anyMatch(limitation -> limitation.startsWith("Template references without a matching member"));
        var partial = truncated || unresolvedRequestedSelector
                || template.requested() && (!template.complete() || unresolvedTemplateBindings);
        return response(
                request, partial ? "PARTIAL" : "OK", source, declaringType, includedImports, responseFields,
                entrySymbols, includedSymbols, downstream, candidates.stream().limit(MAX_CANDIDATES).toList(),
                omittedImports, omittedFields, omittedSymbols, truncated, rendered, template, List.copyOf(limitations)
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
            var header = source.substring(matcher.start(), open);
            var baseMatcher = Pattern.compile("\\bextends\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(header);
            var baseType = baseMatcher.find() ? baseMatcher.group(1) : null;
            var classInfo = new ClassInfo(matcher.group(1), matcher.start(), open, close, baseType);
            classes.add(classInfo);
            members.addAll(classMembers(source, mask, classInfo));
        }
        members.addAll(topLevelFunctions(source, mask, classes));
        members.addAll(topLevelProperties(source, mask, classes));
        return new ParsedSource(List.copyOf(imports), List.copyOf(classes), List.copyOf(members));
    }

    private TemplateContext templateContext(
            GitLabTypeScriptSymbolSliceRequest request,
            String source,
            Set<String> limitations
    ) {
        if (!Boolean.TRUE.equals(request.includeTemplateBindings())) {
            return TemplateContext.notRequested();
        }
        var inline = inlineTemplate(source);
        if (inline != null && !StringUtils.hasText(request.templatePath())) {
            return new TemplateContext(
                    true, true, null, inline,
                    new AngularTemplateBindingParser().parse(inline)
            );
        }
        var path = request.templatePath();
        if (!StringUtils.hasText(path)) {
            var matcher = TEMPLATE_URL.matcher(source);
            if (matcher.find()) {
                path = relative(request.filePath(), matcher.group(1));
            }
        }
        if (!StringUtils.hasText(path)) {
            limitations.add("Template-driven discovery was requested, but no templatePath or static template was found.");
            return new TemplateContext(true, false, null, "", List.of());
        }
        if (!inScope(request.scope(), path)) {
            limitations.add("Angular template is outside the configured code-search scope.");
            return new TemplateContext(true, false, path, "", List.of());
        }
        try {
            var file = repositoryPort.readFile(
                    request.scope().group(), request.scope().projectName(), request.scope().ref(),
                    path, MAX_SOURCE_CHARACTERS
            );
            if (file == null || file.content() == null) {
                limitations.add("Angular template could not be read.");
                return new TemplateContext(true, false, path, "", List.of());
            }
            if (file.truncated()) {
                limitations.add("Angular template exceeded the parser input limit.");
                return new TemplateContext(true, false, path, file.content(), List.of());
            }
            return new TemplateContext(
                    true, true, path, file.content(),
                    new AngularTemplateBindingParser().parse(file.content())
            );
        } catch (RuntimeException exception) {
            limitations.add("Angular template could not be read: " + safeMessage(exception));
            return new TemplateContext(true, false, path, "", List.of());
        }
    }

    private ClassInfo selectedClass(GitLabTypeScriptSymbolSliceRequest request, ParsedSource parsed) {
        if (StringUtils.hasText(request.declaringTypeName())) {
            return parsed.classes().stream()
                    .filter(candidate -> request.declaringTypeName().equals(candidate.name()))
                    .findFirst().orElse(null);
        }
        return parsed.classes().size() == 1 ? parsed.classes().get(0) : null;
    }

    private Set<String> inheritedSymbols(
            GitLabTypeScriptSymbolSliceRequest request,
            String source,
            ParsedSource parsed,
            TemplateContext template,
            ClassInfo selectedClass
    ) {
        if (selectedClass == null || !StringUtils.hasText(selectedClass.baseType())) {
            return Set.of();
        }
        var localNames = parsed.members().stream()
                .filter(member -> selectedClass.name().equals(member.declaringType()))
                .map(Member::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        localNames.addAll(constructorParameterProperties(source, parsed.members(), selectedClass.name()));
        localNames.add(selectedClass.name());
        var result = new LinkedHashSet<String>();
        request.symbolSelectors().stream().map(GitLabTypeScriptSymbolSelector::name)
                .filter(symbol -> !localNames.contains(symbol))
                .forEach(result::add);
        if (template.requested() && template.complete()) {
            template.bindings().stream()
                    .flatMap(binding -> binding.referencedSymbols().stream())
                    .filter(symbol -> !localNames.contains(symbol))
                    .forEach(result::add);
        }
        return Set.copyOf(result);
    }

    private List<GitLabTypeScriptDownstreamReference> inheritedReferences(
            GitLabTypeScriptSymbolSliceRequest request,
            ClassInfo selectedClass,
            Set<String> inheritedSymbols,
            List<ImportStatement> imports
    ) {
        if (selectedClass == null || inheritedSymbols.isEmpty()) {
            return List.of();
        }
        var module = imports.stream()
                .filter(candidate -> candidate.identifiers().contains(selectedClass.baseType()))
                .map(ImportStatement::moduleSpecifier)
                .findFirst().orElse(null);
        var sourceSymbol = StringUtils.hasText(request.declaringTypeName())
                ? request.declaringTypeName() : selectedClass.name();
        return inheritedSymbols.stream().sorted()
                .map(symbol -> new GitLabTypeScriptDownstreamReference(
                        GitLabTypeScriptDownstreamReferenceKind.INHERITED_MEMBER,
                        sourceSymbol, selectedClass.baseType(), symbol,
                        selectedClass.baseType(), module, null
                ))
                .toList();
    }

    private List<ImportStatement> importsForBase(List<ImportStatement> imports, String baseType) {
        if (!StringUtils.hasText(baseType)) {
            return List.of();
        }
        return imports.stream().filter(candidate -> candidate.identifiers().contains(baseType)).toList();
    }

    private Set<String> constructorParameterProperties(
            String source,
            List<Member> members,
            String declaringType
    ) {
        var result = new LinkedHashSet<String>();
        members.stream()
                .filter(member -> declaringType.equals(member.declaringType()))
                .filter(member -> member.kind() == MemberKind.CONSTRUCTOR)
                .findFirst()
                .ifPresent(constructor -> {
                    var matcher = Pattern.compile(
                            "(?:private|protected|public)\\s+(?:readonly\\s+)?"
                                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*[?!]?\\s*:"
                    ).matcher(source.substring(constructor.start(), constructor.end()));
                    while (matcher.find()) result.add(matcher.group(1));
                });
        return result;
    }

    private List<Member> templateEntrySymbols(
            GitLabTypeScriptSymbolSliceRequest request,
            String source,
            ParsedSource parsed,
            TemplateContext template,
            Set<String> inheritedSymbols,
            Set<String> limitations
    ) {
        if (!template.requested() || !template.complete()) {
            return List.of();
        }
        var declaringType = request.declaringTypeName();
        if (!StringUtils.hasText(declaringType)) {
            if (parsed.classes().size() == 1) {
                declaringType = parsed.classes().get(0).name();
            } else {
                limitations.add("Template bindings require declaringTypeName when a source contains multiple classes.");
                return List.of();
            }
        }
        var referenced = template.bindings().stream()
                .flatMap(binding -> binding.referencedSymbols().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var selectedType = declaringType;
        var result = parsed.members().stream()
                .filter(member -> selectedType.equals(member.declaringType()))
                .filter(member -> referenced.contains(member.name())
                        || member.kind() != MemberKind.FIELD && ANGULAR_LIFECYCLE.contains(member.name()))
                .sorted(Comparator.comparingInt(Member::start))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var parameterProperties = constructorParameterProperties(source, parsed.members(), selectedType);
        var referencedParameterProperties = referenced.stream().filter(parameterProperties::contains).toList();
        if (!referencedParameterProperties.isEmpty()) {
            parsed.members().stream()
                    .filter(member -> selectedType.equals(member.declaringType()))
                    .filter(member -> member.kind() == MemberKind.CONSTRUCTOR)
                    .findFirst().ifPresent(result::add);
        }
        var matched = result.stream().map(Member::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        matched.addAll(referencedParameterProperties);
        var unmatched = referenced.stream()
                .filter(symbol -> !matched.contains(symbol) && !inheritedSymbols.contains(symbol))
                .sorted().toList();
        if (!unmatched.isEmpty()) {
            limitations.add("Template references without a matching member in " + declaringType + ": "
                    + String.join(", ", unmatched) + ".");
        }
        return result.stream().distinct().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private String inlineTemplate(String source) {
        var matcher = INLINE_TEMPLATE_START.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        var quote = matcher.group(1).charAt(0);
        var start = matcher.end();
        for (var index = start; index < source.length(); index++) {
            if (source.charAt(index) == quote && source.charAt(index - 1) != '\\') {
                return source.substring(start, index);
            }
        }
        return null;
    }

    private String relative(String sourcePath, String target) {
        var normalized = GitLabFrontendTargetedSourceSession.normalize(sourcePath);
        var separator = normalized.lastIndexOf('/');
        var parent = separator >= 0 ? normalized.substring(0, separator) : "";
        return GitLabFrontendTargetedSourceSession.normalize(parent + "/" + target);
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
        var normalized = stripMemberDecorators(header);
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
        var field = Pattern.compile(
                "(?s)^(?:(?:public|private|protected|readonly|static|declare|abstract|override)\\s+)*"
                        + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*[?!]?\\s*(?::.*?\\s*)?(?:=|$)"
        ).matcher(normalized);
        return field.find() ? new ClassifiedMember(field.group(1), MemberKind.FIELD) : null;
    }

    private String stripMemberDecorators(String header) {
        var normalized = header.strip();
        while (normalized.startsWith("@")) {
            var cursor = 1;
            while (cursor < normalized.length()) {
                var current = normalized.charAt(cursor);
                if (!Character.isJavaIdentifierPart(current) && current != '.' && current != '$') break;
                cursor++;
            }
            while (cursor < normalized.length() && Character.isWhitespace(normalized.charAt(cursor))) cursor++;
            if (cursor < normalized.length() && normalized.charAt(cursor) == '(') {
                var close = matching(normalized, cursor, '(', ')');
                if (close < 0) return normalized;
                cursor = close + 1;
            }
            normalized = normalized.substring(Math.min(cursor, normalized.length())).strip();
        }
        return normalized;
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
            Set<String> inheritedSymbols,
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
                if (selector.name().equals(request.declaringTypeName())) {
                    members.stream()
                            .filter(member -> selector.name().equals(member.declaringType()))
                            .filter(member -> member.kind() == MemberKind.CONSTRUCTOR)
                            .findFirst().ifPresent(result::add);
                } else if (!inheritedSymbols.contains(selector.name())) {
                    limitations.add("No symbol matched selector " + selector.kind() + ":" + selector.name() + ".");
                }
            }
            result.addAll(matches);
        }
        return result.stream().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<Member> includeHelpers(String source, List<Member> all, List<Member> selected, boolean enabled) {
        var included = new LinkedHashSet<>(selected);
        if (!enabled) return List.copyOf(included);
        var cursor = 0;
        while (cursor < included.size()) {
            var current = new ArrayList<>(included).get(cursor++);
            var content = source.substring(current.start(), current.end());
            var references = new LinkedHashSet<String>();
            var calls = Pattern.compile("(?<![.\\w])(?:this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
                    .matcher(content);
            while (calls.find()) references.add(calls.group(1));
            var members = Pattern.compile("\\bthis\\.([A-Za-z_$][A-Za-z0-9_$]*)\\b").matcher(content);
            while (members.find()) references.add(members.group(1));
            all.stream()
                    .filter(candidate -> java.util.Objects.equals(current.declaringType(), candidate.declaringType())
                            || candidate.declaringType() == null && candidate.kind() == MemberKind.FUNCTION)
                    .filter(candidate -> candidate.kind() != MemberKind.FIELD && candidate.kind() != MemberKind.CONSTRUCTOR)
                    .filter(candidate -> references.contains(candidate.name()))
                    .forEach(included::add);
        }
        return included.stream().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<Member> relevantFields(String source, List<Member> fields, List<Member> symbols) {
        var required = symbols.stream()
                .map(member -> source.substring(member.start(), member.end()))
                .map(this::identifiers)
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var directFields = new LinkedHashSet<Member>();
        var changed = true;
        while (changed) {
            changed = false;
            for (var field : fields) {
                if (field.kind() == MemberKind.FIELD && required.contains(field.name()) && directFields.add(field)) {
                    required.addAll(identifiers(source.substring(field.start(), field.end())));
                    changed = true;
                }
            }
        }
        var constructor = fields.stream().filter(member -> member.kind() == MemberKind.CONSTRUCTOR).findFirst().orElse(null);
        if (constructor != null) {
            var constructorSource = source.substring(constructor.start(), constructor.end());
            var parameterProperty = Pattern.compile("(?:private|protected|public)\\s+(?:readonly\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)")
                    .matcher(constructorSource);
            while (parameterProperty.find()) {
                if (required.contains(parameterProperty.group(1))) {
                    directFields.add(constructor);
                    break;
                }
            }
        }
        return directFields.stream().sorted(Comparator.comparingInt(Member::start)).toList();
    }

    private List<GitLabTypeScriptDownstreamReference> downstreamReferences(
            String source,
            List<Member> retainedMembers,
            List<ImportStatement> imports
    ) {
        var fieldTypes = fieldTypes(source, retainedMembers);
        var importByIdentifier = new LinkedHashMap<String, ImportStatement>();
        imports.forEach(candidate -> candidate.identifiers().forEach(identifier -> importByIdentifier.put(identifier, candidate)));
        var result = new LinkedHashMap<String, GitLabTypeScriptDownstreamReference>();
        for (var symbol : retainedMembers) {
            var content = source.substring(symbol.start(), symbol.end());
            var memberCalls = THIS_MEMBER_CALL.matcher(content);
            while (memberCalls.find()) addMemberReference(
                    result, symbol, memberCalls.group(1), memberCalls.group(2), true,
                    fieldTypes, importByIdentifier
            );
            var memberAccesses = THIS_MEMBER_ACCESS.matcher(content);
            while (memberAccesses.find()) addMemberReference(
                    result, symbol, memberAccesses.group(1), memberAccesses.group(2), false,
                    fieldTypes, importByIdentifier
            );
            var qualifiedCalls = QUALIFIED_CALL.matcher(content);
            while (qualifiedCalls.find()) {
                var owner = qualifiedCalls.group(1);
                var imported = importByIdentifier.get(owner);
                if (imported == null) continue;
                var member = qualifiedCalls.group(2);
                var kind = classifyReference(owner, imported.moduleSpecifier(), member, true, true);
                var reference = new GitLabTypeScriptDownstreamReference(
                        kind, symbol.name(), owner, member, owner, imported.moduleSpecifier(), null
                );
                result.put(referenceKey(reference), reference);
            }
            var directCalls = DIRECT_CALL.matcher(content);
            while (directCalls.find()) {
                var owner = directCalls.group(1);
                var imported = importByIdentifier.get(owner);
                if (imported == null) continue;
                var kind = classifyReference(owner, imported.moduleSpecifier(), owner, true, false);
                var reference = new GitLabTypeScriptDownstreamReference(
                        kind, symbol.name(), owner, owner, owner, imported.moduleSpecifier(), null
                );
                result.put(referenceKey(reference), reference);
            }
        }
        return List.copyOf(result.values());
    }

    private void addMemberReference(
            Map<String, GitLabTypeScriptDownstreamReference> result,
            Member symbol,
            String owner,
            String member,
            boolean call,
            Map<String, String> fieldTypes,
            Map<String, ImportStatement> importByIdentifier
    ) {
        var target = fieldTypes.get(owner);
        var imported = target != null ? importByIdentifier.get(target) : null;
        var module = imported != null ? imported.moduleSpecifier() : null;
        var kind = classifyReference(target, module, member, call, false);
        var reference = new GitLabTypeScriptDownstreamReference(
                kind, symbol.name(), owner, member, target, module, null
        );
        result.put(referenceKey(reference), reference);
    }

    private String referenceKey(GitLabTypeScriptDownstreamReference reference) {
        return reference.kind() + "|" + reference.sourceSymbol() + "|" + reference.ownerSymbol()
                + "|" + reference.memberSymbol();
    }

    private GitLabTypeScriptDownstreamReferenceKind classifyReference(
            String target,
            String module,
            String member,
            boolean call,
            boolean qualified
    ) {
        var type = value(target);
        var source = value(module).toLowerCase(Locale.ROOT);
        var directImportedFunction = !qualified && type.equals(member);
        var generatedServiceModule = (source.contains("data-access-swagger") || source.contains("openapi"))
                && !source.contains("/models/")
                && (source.contains("/services/") || type.matches(".*(?:Api|ApiService|Client|ControllerService|Service)$"));
        if (call && !directImportedFunction && (generatedServiceModule
                || type.matches(".*(?:Api|ApiService|Client|ControllerService)$"))) {
            return GitLabTypeScriptDownstreamReferenceKind.BACKEND_OPERATION;
        }
        if ("dispatch".equals(member) && ("Store".equals(type) || source.contains("@ngrx/store"))) {
            return GitLabTypeScriptDownstreamReferenceKind.NGRX_DISPATCH;
        }
        if (("select".equals(member) || "selectSignal".equals(member))
                && ("Store".equals(type) || source.contains("@ngrx/store"))) {
            return GitLabTypeScriptDownstreamReferenceKind.NGRX_SELECT;
        }
        if (qualified && (type.endsWith("Actions") || source.contains(".actions") || source.contains("/actions"))) {
            return GitLabTypeScriptDownstreamReferenceKind.NGRX_ACTION;
        }
        if (source.equals("rxjs") || source.startsWith("rxjs/") || "Observable".equals(type)
                || "pipe".equals(member)) {
            return GitLabTypeScriptDownstreamReferenceKind.RXJS_PIPELINE;
        }
        if (!call) return GitLabTypeScriptDownstreamReferenceKind.PROPERTY_ACCESS;
        return qualified || target != null && target.equals(member)
                ? GitLabTypeScriptDownstreamReferenceKind.IMPORTED_FUNCTION
                : GitLabTypeScriptDownstreamReferenceKind.METHOD_CALL;
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
            members.stream().filter(member -> member.declaringType() == null)
                    .forEach(member -> builder.append(source, member.start(), member.end()).append('\n'));
            builder.append(source, owner.start(), owner.openBrace() + 1).append('\n');
            if (omittedFields > 0) builder.append("  // ... ").append(omittedFields).append(" unrelated fields omitted ...\n");
            if (omittedSymbols > 0) builder.append("  // ... ").append(omittedSymbols).append(" unrelated symbols omitted ...\n");
            members.stream().filter(member -> java.util.Objects.equals(owner.name(), member.declaringType()))
                    .forEach(member -> builder.append(source, member.start(), member.end()).append('\n'));
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
            List<Member> entrySymbols,
            List<Member> symbols,
            List<GitLabTypeScriptDownstreamReference> downstream,
            List<GitLabTypeScriptSymbolCandidate> candidates,
            int omittedImports,
            int omittedFields,
            int omittedSymbols,
            boolean truncated,
            String content,
            TemplateContext template,
            List<String> limitations
    ) {
        var retained = new ArrayList<Member>();
        retained.addAll(fields);
        retained.addAll(symbols);
        var lineStart = retained.stream().mapToInt(member -> lineNumber(source, member.start())).min().orElse(0);
        var lineEnd = retained.stream().mapToInt(member -> lineNumber(source, Math.max(member.start(), member.end() - 1))).max().orElse(0);
        var includedFieldNames = new LinkedHashSet<String>();
        fields.stream().filter(field -> field.kind() == MemberKind.FIELD).map(Member::name)
                .forEach(includedFieldNames::add);
        includedFieldNames.addAll(fieldTypes(source, fields).keySet());
        return new GitLabTypeScriptSymbolSliceResponse(
                request.scope(), request.filePath(), status, declaringType, lineStart, lineEnd, lineCount(source),
                source.length(), template.path(), template.content().length(), template.bindings(),
                content, content.length(), Math.max(0, source.length() - content.length()), truncated,
                imports.stream().map(ImportStatement::statement).toList(),
                List.copyOf(includedFieldNames),
                entrySymbols.stream().map(member -> candidate(source, member)).toList(),
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
                sourceCharacters, request.templatePath(), 0, List.of(), "", 0, sourceCharacters, false,
                List.of(), List.of(), List.of(), List.of(), 0, 0, 0, List.of(), List.of(), limitations
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

    private record ClassInfo(String name, int start, int openBrace, int closeBrace, String baseType) {
    }

    private record Member(String declaringType, String name, MemberKind kind, int start, int end, String header) {
    }

    private record ClassifiedMember(String name, MemberKind kind) {
    }

    private record ParsedSource(List<ImportStatement> imports, List<ClassInfo> classes, List<Member> members) {
    }

    private record TemplateContext(
            boolean requested,
            boolean complete,
            String path,
            String content,
            List<GitLabTypeScriptTemplateBinding> bindings
    ) {
        private TemplateContext {
            content = content != null ? content : "";
            bindings = bindings != null ? List.copyOf(bindings) : List.of();
        }

        private static TemplateContext notRequested() {
            return new TemplateContext(false, true, null, "", List.of());
        }
    }
}
