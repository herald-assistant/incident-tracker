package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDeleteImpact.InboundReference;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OperationalContextCatalogMaintenanceService {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern SAFE_CONFIGURATION_DIRECTORY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");
    private static final Set<String> DERIVED_FIELDS = Set.of(
            "rawSourcePreview", "resolvedOwnership", "validationFindings", "sourceReferences",
            "openQuestions", "overviewSections", "relatedEntities", "recognitionSignals", "explainabilitySections"
    );

    private final OperationalContextSnapshotStore snapshotStore;
    private final OperationalContextYamlWriter yamlWriter;
    private final OperationalContextRelationIndexBuilder relationIndexBuilder =
            new OperationalContextRelationIndexBuilder();

    public OperationalContextEditableEntity entity(String typeName, String id) {
        var type = OperationalContextCatalogEntityType.fromExternalName(typeName);
        var stored = snapshotStore.currentStoredSnapshot();
        return editableEntity(stored, type, id);
    }

    public synchronized OperationalContextCatalogMutationResult create(OperationalContextCatalogMutationCommand command) {
        var type = validateEnvelope(command);
        var stored = snapshotStore.currentStoredSnapshot();
        var entities = mutableEntities(stored, type);
        if (findIndex(entities, command.id()) >= 0) {
            throw new OperationalContextCatalogMaintenanceException(
                    OperationalContextCatalogMaintenanceException.Code.DUPLICATE_ID,
                    "Operational context entity already exists: " + command.id(),
                    List.of(new OperationalContextCatalogFieldError("/id", "Entity ID already exists"))
            );
        }
        var payload = canonicalPayload(stored, type, command, null);
        entities.add(payload);
        return publish(stored, type, entities, command.id());
    }

    public synchronized OperationalContextCatalogMutationResult update(OperationalContextCatalogMutationCommand command) {
        var type = validateEnvelope(command);
        var stored = snapshotStore.currentStoredSnapshot();
        var entities = mutableEntities(stored, type);
        var index = findIndex(entities, command.id());
        if (index < 0) {
            throw OperationalContextCatalogMaintenanceException.notFound(type, command.id());
        }
        var payload = canonicalPayload(stored, type, command, entities.get(index));
        entities.set(index, payload);
        return publish(stored, type, entities, command.id());
    }

    public OperationalContextDeleteImpact deleteImpact(String typeName, String id) {
        var type = OperationalContextCatalogEntityType.fromExternalName(typeName);
        var stored = snapshotStore.currentStoredSnapshot();
        editableEntity(stored, type, id);
        var key = new EntityKey(relationEntityType(type), id);
        var index = relationIndexBuilder.build(stored.readSnapshot().catalog());
        var inbound = index.incomingRelations().getOrDefault(key, List.of()).stream()
                .filter(relation -> !relation.source().equals(key))
                .flatMap(relation -> {
                    var refs = relation.provenance().sourceRefs();
                    if (refs.isEmpty()) {
                        return java.util.stream.Stream.of(new InboundReference(
                                maintenanceEntityType(relation.source().type()), relation.source().id(), relation.relationType(), null, null
                        ));
                    }
                    return refs.stream().map(ref -> new InboundReference(
                            maintenanceEntityType(relation.source().type()), relation.source().id(), relation.relationType(),
                            logicalDocument(ref.file()), ref.fieldPath()
                    ));
                })
                .distinct()
                .toList();
        return new OperationalContextDeleteImpact(
                type.externalName(), id, type.logicalDocument(), inbound.isEmpty(), inbound
        );
    }

    private String relationEntityType(OperationalContextCatalogEntityType type) {
        return type == OperationalContextCatalogEntityType.GLOSSARY_TERM ? "term" : type.externalName();
    }

    private String maintenanceEntityType(String relationType) {
        return "term".equals(relationType) ? OperationalContextCatalogEntityType.GLOSSARY_TERM.externalName() : relationType;
    }

    public synchronized OperationalContextSnapshot delete(String typeName, String id) {
        var type = OperationalContextCatalogEntityType.fromExternalName(typeName);
        var stored = snapshotStore.currentStoredSnapshot();
        var entities = mutableEntities(stored, type);
        var index = findIndex(entities, id);
        if (index < 0) {
            throw OperationalContextCatalogMaintenanceException.notFound(type, id);
        }
        var impact = deleteImpact(type.externalName(), id);
        if (!impact.allowed()) {
            throw new OperationalContextCatalogMaintenanceException(
                    OperationalContextCatalogMaintenanceException.Code.DELETE_RESTRICTED,
                    "Operational context entity is still referenced and cannot be deleted",
                    impact.inboundReferences().stream()
                            .map(reference -> new OperationalContextCatalogFieldError(
                                    reference.fieldPath() != null ? reference.fieldPath() : "/payload",
                                    "Referenced by " + reference.sourceType() + ":" + reference.sourceId()
                            ))
                            .toList()
            );
        }
        entities.remove(index);
        var candidate = candidateDocuments(stored, type, entities);
        return snapshotStore.publishCandidate(candidate);
    }

    private OperationalContextCatalogEntityType validateEnvelope(OperationalContextCatalogMutationCommand command) {
        if (command == null) {
            throw OperationalContextCatalogMaintenanceException.validation(
                    "Mutation request is required",
                    List.of(new OperationalContextCatalogFieldError("/", "Request is required"))
            );
        }
        var type = OperationalContextCatalogEntityType.fromExternalName(command.type());
        var errors = new ArrayList<OperationalContextCatalogFieldError>();
        if (!StringUtils.hasText(command.id())) {
            errors.add(new OperationalContextCatalogFieldError("/id", "ID is required"));
        } else if (!ID_PATTERN.matcher(command.id().trim()).matches()) {
            errors.add(new OperationalContextCatalogFieldError("/id", "ID must use lowercase letters, digits and hyphens"));
        }
        if (command.payload() == null || command.payload().isEmpty()) {
            errors.add(new OperationalContextCatalogFieldError("/payload", "Complete payload is required"));
        }
        if (!errors.isEmpty()) {
            throw OperationalContextCatalogMaintenanceException.validation("Request validation failed", errors);
        }
        return type;
    }

    private Map<String, Object> canonicalPayload(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type,
            OperationalContextCatalogMutationCommand command,
            Map<String, Object> existing
    ) {
        var payload = mutableMap(command.payload());
        canonicalizeAliases(type, payload);
        var errors = new ArrayList<OperationalContextCatalogFieldError>();
        validateValue(payload, "/payload", errors);
        validateFields(type, payload, errors);
        validateIdentity(type, command.id(), payload, errors);
        validateTypeRules(type, payload, errors);
        validateReferences(stored, type, command.id(), payload, errors);
        if (!errors.isEmpty()) {
            throw OperationalContextCatalogMaintenanceException.validation("Entity validation failed", errors);
        }
        if (existing != null) {
            var canonicalExisting = mutableMap(existing);
            canonicalizeAliases(type, canonicalExisting);
            preserveServerOwned(type, canonicalExisting, payload);
        }
        return payload;
    }

    private void validateFields(
            OperationalContextCatalogEntityType type,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        for (var field : payload.keySet()) {
            if (DERIVED_FIELDS.contains(field)) {
                errors.add(new OperationalContextCatalogFieldError("/payload/" + pointer(field), "Read projection is not writable"));
            } else if (OperationalContextCatalogEntitySchema.preserveOnly(type, field)) {
                errors.add(new OperationalContextCatalogFieldError("/payload/" + pointer(field), "Field is preserve-only"));
            } else if (!OperationalContextCatalogEntitySchema.editable(type, field)) {
                errors.add(new OperationalContextCatalogFieldError("/payload/" + pointer(field), "Unknown field is not writable"));
            }
        }
        rejectNestedPreserveOnly(type, payload, errors);
    }

    private void rejectNestedPreserveOnly(
            OperationalContextCatalogEntityType type,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (type == OperationalContextCatalogEntityType.SYSTEM) {
            rejectMapKeys(payload.get("references"), "/payload/references", Set.of("repositories", "systems"), errors);
        }
        if (type == OperationalContextCatalogEntityType.REPOSITORY) {
            rejectMapKeys(payload.get("git"), "/payload/git", Set.of("inferred"), errors);
        }
        if (type == OperationalContextCatalogEntityType.PROCESS) {
            rejectListMapKeys(payload.get("steps"), "/payload/steps", Set.of("match"), errors);
        }
        if (type == OperationalContextCatalogEntityType.INTEGRATION) {
            rejectMapKeys(payload.get("references"), "/payload/references", Set.of("systems"), errors);
            var participants = asMap(payload.get("participants"));
            if (participants != null) {
                rejectMapKeys(participants.get("source"), "/payload/participants/source", Set.of("repositories"), errors);
                for (var field : List.of("targets", "intermediaries", "finalTargets")) {
                    rejectListMapKeys(participants.get(field), "/payload/participants/" + field, Set.of("repositories"), errors);
                }
            }
        }
        if (type == OperationalContextCatalogEntityType.BOUNDED_CONTEXT) {
            validateBoundedContext(payload, errors);
        }
    }

    private void validateIdentity(
            OperationalContextCatalogEntityType type,
            String envelopeId,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var payloadId = text(payload.get("id"));
        if (!StringUtils.hasText(payloadId)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/id", "ID is required"));
        } else if (!payloadId.equals(envelopeId)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/id", "Payload ID must match request ID"));
        }
        var displayField = type == OperationalContextCatalogEntityType.GLOSSARY_TERM
                ? "term"
                : type == OperationalContextCatalogEntityType.HANDOFF_RULE ? "title" : "name";
        if (!StringUtils.hasText(text(payload.get(displayField)))) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/" + displayField,
                    displayField.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + displayField.substring(1) + " is required"
            ));
        }
    }

    private void validateTypeRules(
            OperationalContextCatalogEntityType type,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        validateMatchSignals(payload.get("matchSignals"), errors);
        validateSourceCoverage(payload.get("sourceCoverage"), errors);
        validateGaps(payload.get("gaps"), errors);
        if (type == OperationalContextCatalogEntityType.SYSTEM) {
            validateSystemParticipants(payload.get("participants"), errors);
            validateSystemRuntime(payload.get("runtime"), errors);
        }
        if (type == OperationalContextCatalogEntityType.REPOSITORY) {
            var git = asMap(payload.get("git"));
            if (git == null || (!StringUtils.hasText(text(git.get("project"))) && !StringUtils.hasText(text(git.get("projectPath"))))) {
                errors.add(new OperationalContextCatalogFieldError(
                        "/payload/git/projectPath", "Git project or projectPath is required"
                ));
            }
            validateRepositoryEvidence(payload.get("evidence"), errors);
            validateRepositoryToolHints(payload.get("llmToolHints"), errors);
        }
        if (type == OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE) {
            validateCodeSearchScope(payload, errors);
        }
        if (type == OperationalContextCatalogEntityType.PROCESS) {
            validateProcessSteps(payload, errors);
            validateProcessBoundary(payload.get("processBoundary"), errors);
            validateProcessLifecycle(payload.get("lifecycle"), errors);
            validateCompletionSignals(payload.get("completionSignals"), errors);
            validateFailureModes(type, payload, errors);
            validateDataAndArtifacts(payload.get("dataAndArtifacts"), errors);
        }
        if (type == OperationalContextCatalogEntityType.INTEGRATION) {
            validateFailureModes(type, payload, errors);
            var participants = asMap(payload.get("participants"));
            if (participants == null || asMap(participants.get("source")) == null) {
                errors.add(new OperationalContextCatalogFieldError("/payload/participants/source", "Source participant is required"));
            }
            var targets = participants == null ? List.of() : mapList(participants.get("targets"));
            var finalTargets = participants == null ? List.of() : mapList(participants.get("finalTargets"));
            if (targets.isEmpty() && finalTargets.isEmpty()) {
                errors.add(new OperationalContextCatalogFieldError(
                        "/payload/participants/targets", "At least one target or finalTarget is required"
                ));
            }
        }
        if (type == OperationalContextCatalogEntityType.GLOSSARY_TERM
                && !StringUtils.hasText(text(payload.get("category")))) {
            errors.add(new OperationalContextCatalogFieldError("/payload/category", "Category is required"));
        }
        validateOwnership(payload, errors);
    }

    private void validateSystemParticipants(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        var participants = asMap(value);
        if (participants == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/participants", "System participants must be an object"
            ));
            return;
        }
        validateOptionalText(participants.get("externalOwner"), "/payload/participants/externalOwner", errors);
    }

    private void validateSystemRuntime(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        var runtime = asMap(value);
        if (runtime == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/runtime", "System runtime must be an object"
            ));
            return;
        }
        var directoryValue = runtime.get("configurationDirectory");
        if (directoryValue == null) {
            return;
        }
        if (!(directoryValue instanceof String directory) || !safeConfigurationDirectory(directory)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/runtime/configurationDirectory",
                    "Configuration directory must be a safe repository-relative path"
            ));
        }
    }

    private boolean safeConfigurationDirectory(String value) {
        var directory = value == null ? "" : value.trim();
        return SAFE_CONFIGURATION_DIRECTORY.matcher(directory).matches()
                && !directory.startsWith("/")
                && !directory.endsWith("/")
                && !directory.contains("//")
                && !directory.contains("..")
                && !directory.contains("@{");
    }

    private void validateRepositoryEvidence(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> evidence)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/evidence", "Repository evidence must be a list"
            ));
            return;
        }
        var index = 0;
        for (var item : evidence) {
            var base = "/payload/evidence/" + index;
            var card = asMap(item);
            if (card == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Repository evidence must be an object"));
                index++;
                continue;
            }
            validateRequiredText(card.get("sourceRef"), base + "/sourceRef", "Repository evidence sourceRef is required", errors);
            validateRequiredText(card.get("evidenceType"), base + "/evidenceType", "Repository evidence type is required", errors);
            validateOptionalText(card.get("note"), base + "/note", errors);
            index++;
        }
    }

    private void validateRepositoryToolHints(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        var hints = asMap(value);
        if (hints == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/llmToolHints", "Repository AI exploration guidance must be an object"
            ));
            return;
        }
        validateOptionalTextList(
                hints.get("answerWhenUserMentions"),
                "/payload/llmToolHints/answerWhenUserMentions",
                errors
        );
        validateOptionalTextList(
                hints.get("disambiguateFrom"),
                "/payload/llmToolHints/disambiguateFrom",
                errors
        );
    }

    private void validateBoundedContext(
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        validateLegacyTextList(payload.get("localLanguageSummary"), "/payload/localLanguageSummary", errors);
        validateBoundedContextLists(
                payload.get("scope"),
                "/payload/scope",
                List.of("includes", "excludes", "businessCapabilities", "coreEntities", "keyDecisions"),
                errors
        );
        validateBoundedContextLists(
                payload.get("semanticBoundary"),
                "/payload/semanticBoundary",
                List.of(
                        "coreConcepts", "localConcepts", "canonicalEntities", "commands",
                        "events", "invariants", "ownsLanguage", "doesNotOwn"
                ),
                errors
        );
        validateBoundedContextEvidence(payload.get("evidence"), errors);
        validateBoundedContextToolHints(payload.get("llmToolHints"), errors);
    }

    private void validateLegacyTextList(
            Object value,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            if (!StringUtils.hasText(string)) {
                errors.add(new OperationalContextCatalogFieldError(path, "Value must be non-blank text"));
            }
            return;
        }
        validateOptionalTextList(value, path, errors);
    }

    private void validateBoundedContextLists(
            Object value,
            String path,
            List<String> fields,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        var object = asMap(value);
        if (object == null) {
            errors.add(new OperationalContextCatalogFieldError(path, "Value must be an object of guided lists"));
            return;
        }
        for (var field : fields) {
            validateOptionalTextList(object.get(field), path + "/" + field, errors);
        }
    }

    private void validateBoundedContextEvidence(
            Object value,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> evidence)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/evidence", "Bounded-context evidence must be a list"
            ));
            return;
        }
        var index = 0;
        for (var item : evidence) {
            var base = "/payload/evidence/" + index;
            var card = asMap(item);
            if (card == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Bounded-context evidence must be an object"));
                index++;
                continue;
            }
            validateRequiredText(card.get("sourceRef"), base + "/sourceRef", "Bounded-context evidence sourceRef is required", errors);
            validateRequiredText(card.get("evidenceType"), base + "/evidenceType", "Bounded-context evidence type is required", errors);
            validateOptionalText(card.get("note"), base + "/note", errors);
            index++;
        }
    }

    private void validateBoundedContextToolHints(
            Object value,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        var hints = asMap(value);
        if (hints == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/llmToolHints", "Bounded-context AI guidance must be an object"
            ));
            return;
        }
        for (var field : List.of("answerWhenUserMentions", "disambiguateFrom", "usefulSearchKeywords")) {
            validateOptionalTextList(hints.get(field), "/payload/llmToolHints/" + field, errors);
        }
        validateOptionalText(hints.get("explanationStyle"), "/payload/llmToolHints/explanationStyle", errors);
    }

    private void validateCodeSearchScope(
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var target = asMap(payload.get("target"));
        var targetType = target == null ? null : text(target.get("type"));
        if (!"system".equals(targetType) && !"bounded-context".equals(targetType)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/target/type", "Target type must be system or bounded-context"
            ));
        }
        if (target == null || !StringUtils.hasText(text(target.get("id")))) {
            errors.add(new OperationalContextCatalogFieldError("/payload/target/id", "Target ID is required"));
        }
        var repositories = mapList(payload.get("repositories"));
        var seen = new LinkedHashSet<String>();
        var hasPrimary = false;
        for (var index = 0; index < repositories.size(); index++) {
            var repository = repositories.get(index);
            var base = "/payload/repositories/" + index;
            var repoId = text(repository.get("repoId"));
            if (!StringUtils.hasText(repoId)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/repoId", "Repository ID is required"));
            } else if (!seen.add(repoId)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/repoId", "Repository ID must be unique"));
            }
            var priority = integer(repository.get("priority"));
            if (priority == null || priority < 1) {
                errors.add(new OperationalContextCatalogFieldError(base + "/priority", "Priority must be positive"));
            }
            hasPrimary |= "primary".equals(text(repository.get("role"))) || Integer.valueOf(1).equals(priority);
            var mode = text(repository.get("searchMode"));
            var prefixes = textList(repository.get("pathPrefixes"));
            if ("path-prefixes".equals(mode)) {
                if (prefixes.isEmpty()) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/pathPrefixes", "Path prefixes are required"));
                }
                for (var prefixIndex = 0; prefixIndex < prefixes.size(); prefixIndex++) {
                    var prefix = prefixes.get(prefixIndex);
                    if (prefix.startsWith("/") || prefix.contains("..") || prefix.contains("\\")) {
                        errors.add(new OperationalContextCatalogFieldError(
                                base + "/pathPrefixes/" + prefixIndex, "Path prefix must be safe and relative"
                        ));
                    }
                }
            } else if ("whole-repository".equals(mode)) {
                if (!prefixes.isEmpty()) {
                    errors.add(new OperationalContextCatalogFieldError(
                            base + "/pathPrefixes", "Whole repository search must not define path prefixes"
                    ));
                }
            } else {
                errors.add(new OperationalContextCatalogFieldError(
                        base + "/searchMode", "Search mode must be whole-repository or path-prefixes"
                ));
            }
        }
        if (repositories.isEmpty() || !hasPrimary) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/repositories", "At least one primary or priority 1 repository is required"
            ));
        }
    }

    private void validateMatchSignals(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        var signals = asMap(value);
        if (signals == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/matchSignals", "Recognition signals must be an object"
            ));
            return;
        }
        var strengths = List.of("exact", "strong", "medium", "weak");
        var tiered = strengths.stream().anyMatch(signals::containsKey);
        if (!tiered) {
            validateSignalBucket(signals, "/payload/matchSignals", errors);
            return;
        }
        for (var strength : strengths) {
            if (!signals.containsKey(strength)) {
                continue;
            }
            var bucket = asMap(signals.get(strength));
            var path = "/payload/matchSignals/" + strength;
            if (bucket == null) {
                errors.add(new OperationalContextCatalogFieldError(path, "Signal bucket must be an object"));
            } else {
                validateSignalBucket(bucket, path, errors);
            }
        }
    }

    private void validateSignalBucket(
            Map<String, Object> bucket,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        for (var entry : bucket.entrySet()) {
            var fieldPath = path + "/" + pointer(entry.getKey());
            if (!StringUtils.hasText(entry.getKey())) {
                errors.add(new OperationalContextCatalogFieldError(fieldPath, "Signal key is required"));
            }
            if (!(entry.getValue() instanceof Collection<?> values) || values.isEmpty()) {
                errors.add(new OperationalContextCatalogFieldError(fieldPath, "Signal values must be a non-empty list"));
                continue;
            }
            var index = 0;
            for (var item : values) {
                if (!StringUtils.hasText(text(item))) {
                    errors.add(new OperationalContextCatalogFieldError(
                            fieldPath + "/" + index, "Signal value must be non-blank text"
                    ));
                }
                index++;
            }
        }
    }

    private void validateFailureModes(
            OperationalContextCatalogEntityType type,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var value = payload.get("failureModes");
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> collection)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/failureModes", "Failure modes must be a list"));
            return;
        }
        var ids = new LinkedHashSet<String>();
        var stepIds = mapList(payload.get("steps")).stream()
                .map(step -> text(step.get("id")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        var index = 0;
        for (var item : collection) {
            var base = "/payload/failureModes/" + index;
            if (item instanceof String string) {
                if (!StringUtils.hasText(string)) {
                    errors.add(new OperationalContextCatalogFieldError(base, "Legacy failure description must be non-blank"));
                }
                index++;
                continue;
            }
            var mode = asMap(item);
            if (mode == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Failure mode must be an object"));
                index++;
                continue;
            }
            if (type == OperationalContextCatalogEntityType.PROCESS) {
                var id = text(mode.get("id"));
                if (!StringUtils.hasText(id)) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process failure mode ID is required"));
                } else if (!ID_PATTERN.matcher(id).matches()) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process failure mode ID must use lowercase letters, digits and hyphens"));
                } else if (!ids.add(id)) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process failure mode ID must be unique"));
                }
                if (!StringUtils.hasText(text(mode.get("name")))) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/name", "Process failure mode name is required"));
                }
                if (!StringUtils.hasText(text(mode.get("summary")))) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/summary", "Process failure mode summary is required"));
                }
                var affectedStep = text(mode.get("affectedStep"));
                if (StringUtils.hasText(affectedStep) && !stepIds.contains(affectedStep)) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/affectedStep", "Affected process step does not exist"));
                }
                validateOptionalTextList(mode.get("signals"), base + "/signals", errors);
            } else {
                if (!StringUtils.hasText(text(mode.get("name")))) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/name", "Integration failure mode name is required"));
                }
                if (!StringUtils.hasText(text(mode.get("type")))) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/type", "Integration failure mode type is required"));
                }
                if (!StringUtils.hasText(text(mode.get("symptom"))) && !StringUtils.hasText(text(mode.get("impact")))) {
                    errors.add(new OperationalContextCatalogFieldError(base + "/symptom", "Integration failure mode requires a symptom or impact"));
                }
            }
            index++;
        }
    }

    private void validateProcessBoundary(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            if (!StringUtils.hasText(string)) {
                errors.add(new OperationalContextCatalogFieldError(
                        "/payload/processBoundary", "Legacy process boundary must be non-blank"
                ));
            }
            return;
        }
        if (value instanceof Collection<?>) {
            validateOptionalTextList(value, "/payload/processBoundary", errors);
            return;
        }
        var boundary = asMap(value);
        if (boundary == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/processBoundary", "Process boundary must be an object"
            ));
            return;
        }
        validateOptionalText(boundary.get("businessCapability"), "/payload/processBoundary/businessCapability", errors);
        for (var field : List.of("startsWhen", "endsWhen", "includes", "excludes", "assumptions")) {
            validateOptionalTextList(boundary.get(field), "/payload/processBoundary/" + field, errors);
        }
    }

    private void validateProcessLifecycle(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            if (!StringUtils.hasText(string)) {
                errors.add(new OperationalContextCatalogFieldError(
                        "/payload/lifecycle", "Legacy process lifecycle must be non-blank"
                ));
            }
            return;
        }
        if (value instanceof Collection<?>) {
            validateOptionalTextList(value, "/payload/lifecycle", errors);
            return;
        }
        var lifecycle = asMap(value);
        if (lifecycle == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/lifecycle", "Process lifecycle must be an object"
            ));
            return;
        }
        for (var field : List.of(
                "entryCriteria", "statuses", "terminalStates", "successOutcomes", "partialOutcomes",
                "failedOutcomes", "cancellationOutcomes"
        )) {
            validateOptionalTextList(lifecycle.get(field), "/payload/lifecycle/" + field, errors);
        }
        validateLifecycleTriggers(lifecycle.get("triggers"), errors);
        validateLifecycleTransitions(lifecycle.get("transitions"), errors);
    }

    private void validateLifecycleTriggers(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> triggers)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/lifecycle/triggers", "Lifecycle triggers must be a list"
            ));
            return;
        }
        var index = 0;
        for (var item : triggers) {
            var base = "/payload/lifecycle/triggers/" + index;
            var trigger = asMap(item);
            if (trigger == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Lifecycle trigger must be an object"));
                index++;
                continue;
            }
            validateRequiredText(trigger.get("type"), base + "/type", "Lifecycle trigger type is required", errors);
            validateRequiredText(trigger.get("name"), base + "/name", "Lifecycle trigger name is required", errors);
            validateOptionalText(trigger.get("exchange"), base + "/exchange", errors);
            index++;
        }
    }

    private void validateLifecycleTransitions(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> transitions)) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/lifecycle/transitions", "Lifecycle transitions must be a list"
            ));
            return;
        }
        var index = 0;
        for (var item : transitions) {
            var base = "/payload/lifecycle/transitions/" + index;
            var transition = asMap(item);
            if (transition == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Lifecycle transition must be an object"));
                index++;
                continue;
            }
            validateOptionalText(transition.get("from"), base + "/from", errors);
            validateRequiredText(transition.get("to"), base + "/to", "Lifecycle transition target is required", errors);
            validateRequiredText(transition.get("trigger"), base + "/trigger", "Lifecycle transition trigger is required", errors);
            index++;
        }
    }

    private void validateCompletionSignals(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            if (!StringUtils.hasText(string)) {
                errors.add(new OperationalContextCatalogFieldError(
                        "/payload/completionSignals", "Legacy completion signal must be non-blank"
                ));
            }
            return;
        }
        if (value instanceof Collection<?>) {
            validateOptionalTextList(value, "/payload/completionSignals", errors);
            return;
        }
        var signals = asMap(value);
        if (signals == null) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/completionSignals", "Completion signals must be an object"
            ));
            return;
        }
        for (var field : List.of("successful", "partial", "failed", "cancelled")) {
            validateOptionalTextList(signals.get(field), "/payload/completionSignals/" + field, errors);
        }
    }

    private void validateRequiredText(
            Object value,
            String path,
            String message,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (!(value instanceof String string) || !StringUtils.hasText(string)) {
            errors.add(new OperationalContextCatalogFieldError(path, message));
        }
    }

    private void validateOptionalText(
            Object value,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value != null && (!(value instanceof String string) || !StringUtils.hasText(string))) {
            errors.add(new OperationalContextCatalogFieldError(path, "Value must be non-blank text"));
        }
    }

    private void validateDataAndArtifacts(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        var artifacts = asMap(value);
        if (artifacts == null) {
            errors.add(new OperationalContextCatalogFieldError("/payload/dataAndArtifacts", "Data and artifacts must be an object"));
            return;
        }
        for (var field : List.of(
                "primaryObjects", "inputArtifacts", "outputArtifacts", "persistedEntities",
                "readModels", "auditArtifacts", "notes"
        )) {
            validateOptionalTextList(artifacts.get(field), "/payload/dataAndArtifacts/" + field, errors);
        }
    }

    private void validateSourceCoverage(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        Map<String, Object> coverage;
        if (value instanceof Collection<?> collection) {
            if (collection.size() != 1) {
                errors.add(new OperationalContextCatalogFieldError("/payload/sourceCoverage", "Source coverage must contain one object"));
                return;
            }
            coverage = asMap(collection.iterator().next());
        } else {
            coverage = asMap(value);
        }
        if (coverage == null) {
            errors.add(new OperationalContextCatalogFieldError("/payload/sourceCoverage", "Source coverage must be an object"));
            return;
        }
        var status = text(coverage.get("status"));
        if (StringUtils.hasText(status) && !Set.of(
                "complete", "partial", "unknown", "full", "scanned", "fully-scanned"
        ).contains(status)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/sourceCoverage/status", "Source coverage status is not supported"));
        }
        for (var field : List.of("scannedSources", "sources", "expectedSources", "limitations")) {
            validateOptionalTextList(coverage.get(field), "/payload/sourceCoverage/" + field, errors);
        }
    }

    private void validateGaps(Object value, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> collection)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/gaps", "Gaps must be a list"));
            return;
        }
        var ids = new LinkedHashSet<String>();
        var index = 0;
        for (var item : collection) {
            var base = "/payload/gaps/" + index;
            if (item instanceof String string) {
                if (!StringUtils.hasText(string)) {
                    errors.add(new OperationalContextCatalogFieldError(base, "Legacy gap description must be non-blank"));
                }
                index++;
                continue;
            }
            var gap = asMap(item);
            if (gap == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Gap must be an object"));
                index++;
                continue;
            }
            var id = text(gap.get("id"));
            if (StringUtils.hasText(id) && !ID_PATTERN.matcher(id).matches()) {
                errors.add(new OperationalContextCatalogFieldError(base + "/id", "Gap ID must use lowercase letters, digits and hyphens"));
            } else if (StringUtils.hasText(id) && !ids.add(id)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/id", "Gap ID must be unique"));
            }
            if (java.util.stream.Stream.of(
                    text(gap.get("summary")), text(gap.get("question")), text(gap.get("description")), text(gap.get("impact"))
            ).noneMatch(StringUtils::hasText)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/summary", "Gap requires an actionable summary"));
            }
            var severity = text(gap.get("severity"));
            if (StringUtils.hasText(severity) && !Set.of("error", "warning", "info").contains(severity)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/severity", "Gap severity must be error, warning or info"));
            }
            var status = text(gap.get("status"));
            if (StringUtils.hasText(status) && !Set.of("open", "resolved").contains(status)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/status", "Gap status must be open or resolved"));
            }
            validateOptionalTextList(gap.get("suggestedNextSources"), base + "/suggestedNextSources", errors);
            index++;
        }
    }

    private void validateOptionalTextList(
            Object value,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> collection)) {
            errors.add(new OperationalContextCatalogFieldError(path, "Value must be a list"));
            return;
        }
        var index = 0;
        for (var item : collection) {
            if (!(item instanceof String string) || !StringUtils.hasText(string)) {
                errors.add(new OperationalContextCatalogFieldError(path + "/" + index, "List value must be non-blank text"));
            }
            index++;
        }
    }

    private void validateProcessSteps(
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var value = payload.get("steps");
        if (value != null && !(value instanceof Collection<?>)) {
            errors.add(new OperationalContextCatalogFieldError("/payload/steps", "Process steps must be a list"));
            return;
        }
        var seenIds = new LinkedHashSet<String>();
        var index = 0;
        for (var item : value instanceof Collection<?> collection ? collection : List.of()) {
            var base = "/payload/steps/" + index;
            var step = asMap(item);
            if (step == null) {
                errors.add(new OperationalContextCatalogFieldError(base, "Process step must be an object"));
                index++;
                continue;
            }
            var id = text(step.get("id"));
            if (!StringUtils.hasText(id)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process step ID is required"));
            } else if (!ID_PATTERN.matcher(id).matches()) {
                errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process step ID must use lowercase letters, digits and hyphens"));
            } else if (!seenIds.add(id)) {
                errors.add(new OperationalContextCatalogFieldError(base + "/id", "Process step ID must be unique"));
            }
            if (!StringUtils.hasText(text(step.get("name")))) {
                errors.add(new OperationalContextCatalogFieldError(base + "/name", "Process step name is required"));
            }
            if (step.get("references") != null && asMap(step.get("references")) == null) {
                errors.add(new OperationalContextCatalogFieldError(base + "/references", "Process step references must be an object"));
            }
            index++;
        }
    }

    private void validateOwnership(Map<String, Object> payload, List<OperationalContextCatalogFieldError> errors) {
        var ownership = asMap(payload.get("ownership"));
        if (ownership == null || !"explicit".equals(text(ownership.get("ownershipStatus")))) {
            return;
        }
        if (textList(ownership.get("ownerTeamIds")).isEmpty()
                && !StringUtils.hasText(text(ownership.get("ownerLabel")))) {
            errors.add(new OperationalContextCatalogFieldError(
                    "/payload/ownership/ownerTeamIds", "Explicit ownership requires a team or owner label"
            ));
        }
    }

    private void validateReferences(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type,
            String entityId,
            Map<String, Object> payload,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var catalog = stored.readSnapshot().catalog();
        var ids = new java.util.EnumMap<OperationalContextCatalogEntityType, Set<String>>(OperationalContextCatalogEntityType.class);
        ids.put(OperationalContextCatalogEntityType.SYSTEM, catalog.systems().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.REPOSITORY, catalog.repositories().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE, catalog.codeSearchScopes().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.PROCESS, catalog.processes().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.INTEGRATION, catalog.integrations().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.BOUNDED_CONTEXT, catalog.boundedContexts().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.TEAM, catalog.teams().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.GLOSSARY_TERM, catalog.glossaryTerms().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));
        ids.put(OperationalContextCatalogEntityType.HANDOFF_RULE, catalog.handoffRules().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()));

        var references = asMap(payload.get("references"));
        if (references != null) {
            validateReferenceList(references.get("systems"), ids.get(OperationalContextCatalogEntityType.SYSTEM),
                    type == OperationalContextCatalogEntityType.SYSTEM ? entityId : null, "/payload/references/systems", errors);
            validateReferenceList(references.get("repositories"), ids.get(OperationalContextCatalogEntityType.REPOSITORY),
                    type == OperationalContextCatalogEntityType.REPOSITORY ? entityId : null, "/payload/references/repositories", errors);
            validateReferenceList(references.get("processes"), ids.get(OperationalContextCatalogEntityType.PROCESS),
                    type == OperationalContextCatalogEntityType.PROCESS ? entityId : null, "/payload/references/processes", errors);
            validateReferenceList(references.get("integrations"), ids.get(OperationalContextCatalogEntityType.INTEGRATION),
                    type == OperationalContextCatalogEntityType.INTEGRATION ? entityId : null, "/payload/references/integrations", errors);
            validateReferenceList(references.get("boundedContexts"), ids.get(OperationalContextCatalogEntityType.BOUNDED_CONTEXT),
                    type == OperationalContextCatalogEntityType.BOUNDED_CONTEXT ? entityId : null, "/payload/references/boundedContexts", errors);
            validateReferenceList(references.get("teams"), ids.get(OperationalContextCatalogEntityType.TEAM),
                    type == OperationalContextCatalogEntityType.TEAM ? entityId : null, "/payload/references/teams", errors);
            validateReferenceList(references.get("terms"), catalog.glossaryTerms().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()),
                    type == OperationalContextCatalogEntityType.GLOSSARY_TERM ? entityId : null, "/payload/references/terms", errors);
            validateReferenceList(references.get("handoffRules"), catalog.handoffRules().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet()),
                    type == OperationalContextCatalogEntityType.HANDOFF_RULE ? entityId : null, "/payload/references/handoffRules", errors);
        }

        var ownership = asMap(payload.get("ownership"));
        if (ownership != null) {
            validateReferenceList(ownership.get("ownerTeamIds"), ids.get(OperationalContextCatalogEntityType.TEAM),
                    null, "/payload/ownership/ownerTeamIds", errors);
        }

        validateRelations(type, entityId, payload.get("relations"), ids, errors);

        if (type == OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE) {
            var target = asMap(payload.get("target"));
            if (target != null) {
                var targetType = "system".equals(text(target.get("type")))
                        ? OperationalContextCatalogEntityType.SYSTEM
                        : OperationalContextCatalogEntityType.BOUNDED_CONTEXT;
                validateSingleReference(target.get("id"), ids.get(targetType), null, "/payload/target/id", errors);
            }
            var repositories = mapList(payload.get("repositories"));
            for (var index = 0; index < repositories.size(); index++) {
                validateSingleReference(repositories.get(index).get("repoId"), ids.get(OperationalContextCatalogEntityType.REPOSITORY),
                        null, "/payload/repositories/" + index + "/repoId", errors);
            }
        }

        if (type == OperationalContextCatalogEntityType.PROCESS) {
            var participants = asMap(payload.get("participants"));
            if (participants != null) {
                for (var field : List.of("primarySystems", "supportingSystems", "externalSystems", "platformComponents")) {
                    validateReferenceList(participants.get(field), ids.get(OperationalContextCatalogEntityType.SYSTEM),
                            null, "/payload/participants/" + field, errors);
                }
            }
            validateProcessStepReferences(payload, ids, errors);
        }

        if (type == OperationalContextCatalogEntityType.INTEGRATION) {
            var participants = asMap(payload.get("participants"));
            if (participants != null) {
                validateParticipant(participants.get("source"), "/payload/participants/source", ids, errors);
                for (var field : List.of("targets", "intermediaries", "finalTargets")) {
                    var values = mapList(participants.get(field));
                    for (var index = 0; index < values.size(); index++) {
                        validateParticipant(values.get(index), "/payload/participants/" + field + "/" + index, ids, errors);
                    }
                }
            }
        }

        if (type == OperationalContextCatalogEntityType.GLOSSARY_TERM) {
            validateReferenceList(
                    payload.get("relatedTerms"),
                    ids.get(OperationalContextCatalogEntityType.GLOSSARY_TERM),
                    entityId,
                    "/payload/relatedTerms",
                    errors
            );
            validateTypedReferences(payload.get("canonicalReferences"), ids, "/payload/canonicalReferences", errors);
        }
    }

    private void validateRelations(
            OperationalContextCatalogEntityType sourceType,
            String sourceId,
            Object value,
            Map<OperationalContextCatalogEntityType, Set<String>> ids,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var relations = mapList(value);
        var relationKeys = new LinkedHashSet<String>();
        for (var index = 0; index < relations.size(); index++) {
            var relation = relations.get(index);
            var basePath = "/payload/relations/" + index;
            if (!StringUtils.hasText(text(relation.get("type")))) {
                errors.add(new OperationalContextCatalogFieldError(basePath + "/type", "Relation type is required"));
            }

            var targetId = text(relation.get("target"));
            var targetTypeValue = text(relation.get("targetType"));
            var targetPointer = basePath + "/target";
            if (!StringUtils.hasText(targetId) && StringUtils.hasText(text(relation.get("targetContextId")))) {
                targetId = text(relation.get("targetContextId"));
                targetTypeValue = "bounded-context";
                targetPointer = basePath + "/targetContextId";
            } else if (!StringUtils.hasText(targetId) && StringUtils.hasText(text(relation.get("targetProcessId")))) {
                targetId = text(relation.get("targetProcessId"));
                targetTypeValue = "process";
                targetPointer = basePath + "/targetProcessId";
            } else if (StringUtils.hasText(targetId) && !StringUtils.hasText(targetTypeValue)) {
                targetTypeValue = "system";
            }

            if (StringUtils.hasText(targetId) || StringUtils.hasText(targetTypeValue)) {
                var targetType = relationTargetType(targetTypeValue);
                if (targetType == null) {
                    errors.add(new OperationalContextCatalogFieldError(
                            basePath + "/targetType", "Relation targetType must identify a supported catalogue entity type"
                    ));
                } else if (!StringUtils.hasText(targetId)) {
                    errors.add(new OperationalContextCatalogFieldError(targetPointer, "Relation target is required"));
                } else {
                    var relationKey = text(relation.get("type")) + "|" + targetType.externalName() + "|" + targetId;
                    if (!relationKeys.add(relationKey)) {
                        errors.add(new OperationalContextCatalogFieldError(basePath, "Duplicate semantic relation"));
                    }
                    validateSingleReference(
                            targetId,
                            ids.get(targetType),
                            targetType == sourceType ? sourceId : null,
                            targetPointer,
                            errors
                    );
                }
            } else if (!StringUtils.hasText(text(relation.get("externalSystem")))) {
                errors.add(new OperationalContextCatalogFieldError(
                        targetPointer, "Relation requires a catalogue target or externalSystem label"
                ));
            } else if (!relationKeys.add(text(relation.get("type")) + "|external|" + text(relation.get("externalSystem")))) {
                errors.add(new OperationalContextCatalogFieldError(basePath, "Duplicate semantic relation"));
            }

            validateReferenceList(relation.get("via"), ids.get(OperationalContextCatalogEntityType.INTEGRATION),
                    null, basePath + "/via", errors);
        }
    }

    private OperationalContextCatalogEntityType relationTargetType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.trim().replace('_', '-');
        normalized = switch (normalized) {
            case "systems" -> "system";
            case "repositories" -> "repository";
            case "codeSearchScope", "codeSearchScopes", "code-search-scopes" -> "code-search-scope";
            case "processes" -> "process";
            case "integrations" -> "integration";
            case "boundedContext", "boundedContexts", "bounded-contexts" -> "bounded-context";
            case "teams" -> "team";
            case "term", "terms", "glossary-terms" -> "glossary-term";
            case "handoffRule", "handoffRules", "handoff-rules" -> "handoff-rule";
            default -> normalized;
        };
        for (var type : OperationalContextCatalogEntityType.values()) {
            if (type.externalName().equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    private void validateTypedReferences(
            Object value,
            Map<OperationalContextCatalogEntityType, Set<String>> ids,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (!(value instanceof Collection<?> collection)) {
            if (value != null) {
                errors.add(new OperationalContextCatalogFieldError(path, "Canonical references must be a list"));
            }
            return;
        }
        var index = 0;
        for (var item : collection) {
            var reference = text(item);
            var separator = reference != null ? reference.indexOf(':') : -1;
            if (separator < 1 || separator == reference.length() - 1) {
                errors.add(new OperationalContextCatalogFieldError(path + "/" + index, "Reference must use type:id"));
                index++;
                continue;
            }
            var typeName = reference.substring(0, separator).trim();
            if ("term".equals(typeName)) {
                typeName = "glossary-term";
            }
            try {
                var referenceType = OperationalContextCatalogEntityType.fromExternalName(typeName);
                var referenceId = reference.substring(separator + 1).trim().replaceFirst("\\s+.*$", "");
                validateSingleReference(referenceId, ids.get(referenceType), null, path + "/" + index, errors);
            } catch (OperationalContextCatalogMaintenanceException exception) {
                errors.add(new OperationalContextCatalogFieldError(path + "/" + index, "Reference type is not supported"));
            }
            index++;
        }
    }

    private void validateProcessStepReferences(
            Map<String, Object> payload,
            Map<OperationalContextCatalogEntityType, Set<String>> ids,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var steps = mapList(payload.get("steps"));
        for (var index = 0; index < steps.size(); index++) {
            var base = "/payload/steps/" + index;
            var references = asMap(steps.get(index).get("references"));
            if (references != null) {
                validateReferenceList(references.get("systems"), ids.get(OperationalContextCatalogEntityType.SYSTEM),
                        null, base + "/references/systems", errors);
                validateReferenceList(references.get("repositories"), ids.get(OperationalContextCatalogEntityType.REPOSITORY),
                        null, base + "/references/repositories", errors);
                validateReferenceList(references.get("boundedContexts"), ids.get(OperationalContextCatalogEntityType.BOUNDED_CONTEXT),
                        null, base + "/references/boundedContexts", errors);
                validateReferenceList(references.get("integrations"), ids.get(OperationalContextCatalogEntityType.INTEGRATION),
                        null, base + "/references/integrations", errors);
                validateReferenceList(references.get("terms"), ids.get(OperationalContextCatalogEntityType.GLOSSARY_TERM),
                        null, base + "/references/terms", errors);
                validateReferenceList(references.get("handoffRules"), ids.get(OperationalContextCatalogEntityType.HANDOFF_RULE),
                        null, base + "/references/handoffRules", errors);
            }
            var legacyParticipants = asMap(steps.get(index).get("participants"));
            if (legacyParticipants != null) {
                validateReferenceList(legacyParticipants.get("systems"), ids.get(OperationalContextCatalogEntityType.SYSTEM),
                        null, base + "/participants/systems", errors);
                validateReferenceList(legacyParticipants.get("boundedContexts"), ids.get(OperationalContextCatalogEntityType.BOUNDED_CONTEXT),
                        null, base + "/participants/boundedContexts", errors);
                validateReferenceList(legacyParticipants.get("integrations"), ids.get(OperationalContextCatalogEntityType.INTEGRATION),
                        null, base + "/participants/integrations", errors);
            }
        }
    }

    private void validateParticipant(
            Object value,
            String path,
            Map<OperationalContextCatalogEntityType, Set<String>> ids,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var participant = asMap(value);
        if (participant == null) {
            return;
        }
        if (participant.get("system") != null) {
            validateSingleReference(participant.get("system"), ids.get(OperationalContextCatalogEntityType.SYSTEM),
                    null, path + "/system", errors);
        }
        if (participant.get("boundedContext") != null) {
            validateSingleReference(participant.get("boundedContext"), ids.get(OperationalContextCatalogEntityType.BOUNDED_CONTEXT),
                    null, path + "/boundedContext", errors);
        }
    }

    private void validateReferenceList(
            Object value,
            Set<String> allowed,
            String selfId,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Collection<?> collection)) {
            errors.add(new OperationalContextCatalogFieldError(path, "Reference field must be a list"));
            return;
        }
        var seen = new LinkedHashSet<String>();
        var index = 0;
        for (var item : collection) {
            var id = text(item);
            var pointer = path + "/" + index;
            if (!seen.add(id)) {
                errors.add(new OperationalContextCatalogFieldError(pointer, "Duplicate reference"));
            }
            validateSingleReference(item, allowed, selfId, pointer, errors);
            index++;
        }
    }

    private void validateSingleReference(
            Object value,
            Set<String> allowed,
            String selfId,
            String path,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var id = text(value);
        if (StringUtils.hasText(selfId) && selfId.equals(id)) {
            errors.add(new OperationalContextCatalogFieldError(path, "Self-reference is not allowed"));
        } else if (StringUtils.hasText(id) && (allowed == null || !allowed.contains(id))) {
            errors.add(new OperationalContextCatalogFieldError(path, "Referenced entity does not exist"));
        }
    }

    private void validateValue(Object value, String path, List<OperationalContextCatalogFieldError> errors) {
        if (value == null) {
            errors.add(new OperationalContextCatalogFieldError(path, "Null is not accepted; omit optional fields"));
            return;
        }
        if (value instanceof String string && !StringUtils.hasText(string)) {
            errors.add(new OperationalContextCatalogFieldError(path, "Blank strings are not accepted"));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                errors.add(new OperationalContextCatalogFieldError(path, "Empty objects are not accepted"));
                return;
            }
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                if (DERIVED_FIELDS.contains(key)) {
                    errors.add(new OperationalContextCatalogFieldError(path + "/" + pointer(key), "Read projection is not writable"));
                }
                validateValue(entry.getValue(), path + "/" + pointer(key), errors);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            var index = 0;
            for (var item : collection) {
                validateValue(item, path + "/" + index, errors);
                index++;
            }
        }
    }

    private void canonicalizeAliases(OperationalContextCatalogEntityType type, Map<String, Object> payload) {
        if (type != OperationalContextCatalogEntityType.SYSTEM) {
            return;
        }
        var systemType = firstValue(payload, "systemType", "type", "kind");
        payload.remove("type");
        payload.remove("kind");
        if (systemType != null) {
            payload.put("systemType", systemType);
        }
        var legacyMatch = payload.remove("match");
        if (legacyMatch != null && !payload.containsKey("matchSignals")) {
            var matchSignals = new LinkedHashMap<String, Object>();
            matchSignals.put("strong", legacyMatch);
            payload.put("matchSignals", matchSignals);
        }
    }

    private void preserveServerOwned(
            OperationalContextCatalogEntityType type,
            Map<String, Object> existing,
            Map<String, Object> payload
    ) {
        for (var entry : existing.entrySet()) {
            if (!OperationalContextCatalogEntitySchema.known(type, entry.getKey())) {
                payload.putIfAbsent(entry.getKey(), mutableValue(entry.getValue()));
            } else if (OperationalContextCatalogEntitySchema.preserveOnly(type, entry.getKey())) {
                payload.put(entry.getKey(), mutableValue(entry.getValue()));
            }
        }
        if (type == OperationalContextCatalogEntityType.SYSTEM) {
            preserveMapFields(existing, payload, "references", Set.of("repositories", "systems"));
        }
        if (type == OperationalContextCatalogEntityType.PROCESS) {
            preserveListFields(existing, payload, "steps", "id", Set.of("match"));
        }
        if (type == OperationalContextCatalogEntityType.INTEGRATION) {
            preserveMapFields(existing, payload, "references", Set.of("systems"));
            preserveParticipantRepositories(existing, payload);
        }
    }

    private void preserveParticipantRepositories(Map<String, Object> existing, Map<String, Object> payload) {
        var oldParticipants = asMap(existing.get("participants"));
        var newParticipants = asMap(payload.get("participants"));
        if (oldParticipants == null || newParticipants == null) {
            return;
        }
        preserveMapFields(oldParticipants, newParticipants, "source", Set.of("repositories"));
        for (var field : List.of("targets", "intermediaries", "finalTargets")) {
            preserveListFields(oldParticipants, newParticipants, field, "system", Set.of("repositories"));
        }
    }

    private void preserveMapFields(
            Map<String, Object> existing,
            Map<String, Object> payload,
            String section,
            Set<String> fields
    ) {
        var oldSection = asMap(existing.get(section));
        if (oldSection == null) {
            return;
        }
        var newSection = asMap(payload.get(section));
        if (newSection == null) {
            newSection = new LinkedHashMap<>();
            payload.put(section, newSection);
        }
        for (var field : fields) {
            if (oldSection.containsKey(field)) {
                newSection.put(field, mutableValue(oldSection.get(field)));
            }
        }
    }

    private void preserveListFields(
            Map<String, Object> existing,
            Map<String, Object> payload,
            String section,
            String identity,
            Set<String> fields
    ) {
        var oldValues = mapList(existing.get(section));
        var newValues = mutableMapList(payload.get(section));
        if (oldValues.isEmpty() || newValues.isEmpty()) {
            return;
        }
        var oldById = new LinkedHashMap<String, Map<String, Object>>();
        oldValues.forEach(value -> oldById.put(text(value.get(identity)), value));
        for (var value : newValues) {
            var old = oldById.get(text(value.get(identity)));
            if (old != null) {
                for (var field : fields) {
                    if (old.containsKey(field)) {
                        value.put(field, mutableValue(old.get(field)));
                    }
                }
            }
        }
        payload.put(section, newValues);
    }

    private OperationalContextCatalogMutationResult publish(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type,
            List<Map<String, Object>> entities,
            String entityId
    ) {
        var candidate = candidateDocuments(stored, type, entities);
        snapshotStore.publishCandidate(candidate);
        var published = snapshotStore.currentStoredSnapshot();
        return new OperationalContextCatalogMutationResult(editableEntity(published, type, entityId));
    }

    private Map<String, String> candidateDocuments(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type,
            List<Map<String, Object>> entities
    ) {
        var documents = new LinkedHashMap<>(stored.rawDocuments().contents());
        var document = mutableMap(stored.decodedDocuments().get(type.logicalDocument()));
        document.put(type.collectionName(), entities);
        documents.put(type.logicalDocument(), yamlWriter.write(document));
        return documents;
    }

    private OperationalContextEditableEntity editableEntity(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type,
            String id
    ) {
        var entities = mutableEntities(stored, type);
        var index = findIndex(entities, id);
        if (index < 0) {
            throw OperationalContextCatalogMaintenanceException.notFound(type, id);
        }
        var payload = mutableMap(entities.get(index));
        canonicalizeAliases(type, payload);
        return new OperationalContextEditableEntity(
                type.externalName(), id, type.logicalDocument(), payload
        );
    }

    private List<Map<String, Object>> mutableEntities(
            OperationalContextStoredSnapshot stored,
            OperationalContextCatalogEntityType type
    ) {
        var document = stored.decodedDocuments().get(type.logicalDocument());
        return mutableMapList(document == null ? null : document.get(type.collectionName()));
    }

    private int findIndex(List<Map<String, Object>> entities, String id) {
        for (var index = 0; index < entities.size(); index++) {
            if (java.util.Objects.equals(text(entities.get(index).get("id")), id)) {
                return index;
            }
        }
        return -1;
    }

    private void rejectMapKeys(
            Object value,
            String path,
            Set<String> rejected,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var map = asMap(value);
        if (map == null) {
            return;
        }
        for (var field : rejected) {
            if (map.containsKey(field)) {
                errors.add(new OperationalContextCatalogFieldError(path + "/" + field, "Field is preserve-only"));
            }
        }
    }

    private void rejectListMapKeys(
            Object value,
            String path,
            Set<String> rejected,
            List<OperationalContextCatalogFieldError> errors
    ) {
        var values = mapList(value);
        for (var index = 0; index < values.size(); index++) {
            rejectMapKeys(values.get(index), path + "/" + index, rejected, errors);
        }
    }

    private String logicalDocument(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var normalized = value.replace('\\', '/');
        var separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private Object firstValue(Map<String, Object> source, String... fields) {
        for (var field : fields) {
            if (source.containsKey(field)) {
                return source.get(field);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : collection) {
            var map = asMap(item);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }

    private List<Map<String, Object>> mutableMapList(Object value) {
        var result = new ArrayList<Map<String, Object>>();
        if (value instanceof Collection<?> collection) {
            for (var item : collection) {
                if (item instanceof Map<?, ?> map) {
                    result.add(mutableMap(map));
                }
            }
        }
        return result;
    }

    private Map<String, Object> mutableMap(Map<?, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        if (source != null) {
            source.forEach((key, value) -> result.put(String.valueOf(key), mutableValue(value)));
        }
        return result;
    }

    private Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mutableMap(map);
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>();
            collection.forEach(item -> result.add(mutableValue(item)));
            return result;
        }
        return value;
    }

    private String text(Object value) {
        return value instanceof String string ? string.trim() : value != null ? String.valueOf(value).trim() : null;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.valueOf(String.valueOf(value)) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> textList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(this::text).filter(StringUtils::hasText).toList();
    }

    private String pointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
