package pl.mkn.tdw.features.operationalcontextassistance.draft;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptSanitizer;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.Basis;
import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.Confidence;
import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.FieldChange;
import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.Operation;
import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.Proposal;

/** Parses model output as untrusted data. Full candidate validation belongs to maintenance preview. */
@Component
@RequiredArgsConstructor
public class OperationalContextAssistanceDraftParser {

    private static final int MAX_RESPONSE_LENGTH = 262_144;
    private static final int MAX_PROPOSALS = 12;
    private static final int MAX_CHANGES = 24;
    private static final int MAX_LIST = 24;
    private static final int MAX_TEXT = 4_000;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern TOP_LEVEL_FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final Pattern SINGLE_JSON_FENCE = Pattern.compile(
            "\\A[ \\t\\r\\n]*```(?:json)?[ \\t]*\\r?\\n(.*?)\\r?\\n```[ \\t\\r\\n]*\\z",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Set<String> ROOT_FIELDS = Set.of("proposals", "questions", "visibilityLimits");
    private static final Set<String> PROPOSAL_FIELDS = Set.of(
            "operation", "entityType", "entityId", "changes", "confidence", "requiresConfirmation",
            "questions", "visibilityLimits"
    );
    private static final Set<String> CHANGE_FIELDS = Set.of(
            "path", "before", "after", "reason", "basis", "sourceRefs", "confidence", "requiresConfirmation"
    );

    private final ObjectMapper objectMapper;
    private final OperationalContextCatalogMaintenanceService maintenanceService;

    public OperationalContextAssistanceDraft parse(String content, OperationalContextAssistanceDraftScope scope) {
        if (content == null || content.isBlank() || content.length() > MAX_RESPONSE_LENGTH) {
            throw invalid("Response is empty or exceeds the draft limit");
        }
        if (scope == null) {
            throw invalid("Draft scope is required");
        }
        var sourceRefs = scope.allowedSourceRefs();
        JsonNode root;
        try {
            var fence = SINGLE_JSON_FENCE.matcher(content);
            var json = fence.matches() ? fence.group(1) : content;
            root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(json);
        } catch (JsonProcessingException exception) {
            throw new OperationalContextAssistanceDraftParseException("Response is not strict JSON", exception);
        }
        if (OperationalContextAssistancePromptSanitizer.containsSensitiveContent(root, objectMapper)) {
            throw invalid("Draft contains sensitive content");
        }
        requireObject(root, "$", ROOT_FIELDS);
        var proposalsNode = requireArray(root, "proposals", "$", MAX_PROPOSALS);
        var proposals = new ArrayList<Proposal>();
        for (var index = 0; index < proposalsNode.size(); index++) {
            proposals.add(proposal(proposalsNode.get(index), "$.proposals[" + index + "]", sourceRefs));
        }
        validateScope(proposals, scope);
        return new OperationalContextAssistanceDraft(
                proposals,
                textList(root, "questions", "$", MAX_LIST),
                textList(root, "visibilityLimits", "$", MAX_LIST)
        );
    }

    private void validateScope(List<Proposal> proposals, OperationalContextAssistanceDraftScope scope) {
        for (var proposal : proposals) {
            validateSelectedRepositoryIdentity(proposal, scope);
        }
        if (scope.mode() == OperationalContextAssistanceMode.CREATE_AREA) {
            if (scope.repositoryFacts() != null
                    && scope.repositoryFacts().usage() != OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN) {
                validateRepositoryOnboarding(proposals, scope);
            } else {
                validateCatalogRevision(proposals, scope);
            }
            return;
        }
        if (!proposals.isEmpty() && proposals.stream().noneMatch(proposal ->
                proposal.operation() == Operation.UPDATE
                        && proposal.entityType().equals(scope.targetType())
                        && proposal.entityId().equals(scope.targetId()))) {
            throw invalid(scope.mode() + " must update the selected entity when it proposes changes");
        }
        validateCatalogRevision(proposals, scope);
    }

    private void validateCatalogRevision(List<Proposal> proposals, OperationalContextAssistanceDraftScope scope) {
        var uniqueTargets = new HashSet<String>();
        for (var proposal : proposals) {
            if (!uniqueTargets.add(proposal.entityType() + "/" + proposal.entityId())) {
                throw invalid("A catalog entity may appear only once in a draft");
            }
            if (proposal.operation() == Operation.CREATE && "repository".equals(proposal.entityType())
                    && !scope.hasSelectedSource()) {
                throw invalid("A new repository requires a read file from the selected GitLab project");
            }
            if (proposal.operation() == Operation.CREATE && "code-search-scope".equals(proposal.entityType())
                    && !scope.hasSelectedSource()) {
                throw invalid("A new code-search scope requires a read file from the selected GitLab project");
            }
        }
    }

    private void validateRepositoryOnboarding(List<Proposal> proposals, OperationalContextAssistanceDraftScope scope) {
        validateCatalogRevision(proposals, scope);
        var usage = scope.repositoryUsage();
        var phase = 0;
        String createdSystemId = null;
        String createdRepositoryId = null;
        var updatedScopes = new HashSet<String>();
        for (var proposal : proposals) {
            if (proposal.operation() == Operation.CREATE && "system".equals(proposal.entityType())) {
                if (usage != OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM
                        || phase != 0 || scope.repositoryFacts().systemName() == null) {
                    throw invalid("Selected repository usage does not permit a new system");
                }
                requireSelectedSystemName(proposal, scope.repositoryFacts().systemName());
                validateRuntimeServiceName(proposal, scope.repositoryFacts().runtimeServiceName());
                createdSystemId = proposal.entityId();
                phase = 1;
            } else if (proposal.operation() == Operation.CREATE && "repository".equals(proposal.entityType())) {
                if (createdRepositoryId != null || phase > 1 || !scope.hasSelectedSource()) {
                    throw invalid("Repository proposal order or selected GitLab source is invalid");
                }
                validateRepositoryRole(proposal, scope, createdSystemId);
                createdRepositoryId = proposal.entityId();
                phase = 2;
            } else if (proposal.operation() == Operation.CREATE && "code-search-scope".equals(proposal.entityType())) {
                if (usage != OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM
                        || createdSystemId == null || createdRepositoryId == null || phase != 2
                        || !scope.hasSelectedSource()) {
                    throw invalid("New code-search scope requires the selected deployed system and repository");
                }
                validateNewScopeTarget(proposal, createdSystemId, createdRepositoryId);
                phase = 3;
            } else if (proposal.operation() == Operation.UPDATE && "code-search-scope".equals(proposal.entityType())) {
                if ((usage != OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY
                        && usage != OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM)
                        || createdRepositoryId == null || !scope.selectedScopeIds().contains(proposal.entityId())
                        || !updatedScopes.add(proposal.entityId()) || !scope.hasSelectedSource()) {
                    throw invalid("Only a selected system code-search scope may be updated after repository creation");
                }
                validateScopeRepositoryAppend(proposal, createdRepositoryId, usage);
                phase = 3;
            } else if (proposal.operation() == Operation.UPDATE && "system".equals(proposal.entityType())
                    && scope.selectedSystemIds().contains(proposal.entityId())) {
                // The selected existing consumer may need a semantic update in the same reviewed batch.
            } else if (!Set.of("system", "repository", "code-search-scope")
                    .contains(proposal.entityType())) {
                // Other catalog entities can be revised alongside onboarding; final references are validated as a batch.
            } else {
                throw invalid("Selected repository usage does not permit this proposal");
            }
        }
        if (createdRepositoryId != null
                && (usage == OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM
                || (usage == OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY
                && !scope.selectedSystemIds().isEmpty()))) {
            if (scope.selectedScopeIds().size() != scope.selectedSystemIds().size()
                    || !updatedScopes.containsAll(scope.selectedScopeIds())) {
                throw invalid("Repository onboarding must update every selected system code-search scope");
            }
        }
    }

    private void requireSelectedSystemName(Proposal proposal, String selectedName) {
        var names = proposal.changes().stream().filter(change -> "name".equals(change.path())).toList();
        if (names.size() != 1 || !selectedName.equals(names.get(0).after())) {
            throw invalid("New system name must match the operator's selected name");
        }
    }

    private void validateRuntimeServiceName(Proposal proposal, String selectedServiceName) {
        for (var change : proposal.changes()) {
            if (!"matchSignals".equals(change.path()) || !(change.after() instanceof Map<?, ?> signals)) {
                continue;
            }
            if (signals.containsKey("serviceNames")) {
                throw invalid("Service names require the operator's exact runtime service name");
            }
            for (var entry : signals.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> bucket) || !bucket.containsKey("serviceNames")) {
                    continue;
                }
                if (!"exact".equals(entry.getKey()) || selectedServiceName == null
                        || !List.of(selectedServiceName).equals(bucket.get("serviceNames"))) {
                    throw invalid("Service names must match only the operator's exact runtime service name");
                }
            }
        }
    }

    private void validateRepositoryRole(
            Proposal proposal, OperationalContextAssistanceDraftScope scope, String createdSystemId
    ) {
        var usage = scope.repositoryUsage();
        if (usage == OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY
                && proposal.changes().stream().noneMatch(change -> "repositoryType".equals(change.path())
                && "shared-library".equals(change.after()))) {
            throw invalid("Shared library repository must declare shared-library type");
        }
        for (var change : proposal.changes()) {
            if ("relations".equals(change.path())) {
                throw invalid("Repository onboarding cannot add unselected catalog relations");
            }
            if ("repositoryType".equals(change.path())
                    && usage == OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY
                    && !"shared-library".equals(change.after())) {
                throw invalid("Shared library must use shared-library repository type");
            }
            if (!"references".equals(change.path())) {
                continue;
            }
            if (!(change.after() instanceof Map<?, ?> references)) {
                throw invalid("Repository references must be an object");
            }
            if (references.keySet().stream().anyMatch(key -> !"systems".equals(key))) {
                throw invalid("Repository onboarding may reference only selected systems");
            }
            var systems = references.get("systems");
            if (systems == null) {
                continue;
            }
            if (!(systems instanceof List<?> ids)) {
                throw invalid("Repository system references must be a list");
            }
            var allowed = createdSystemId == null ? scope.selectedSystemIds() : Set.of(createdSystemId);
            if (!allowed.containsAll(ids) || usage == OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN
                    && !ids.isEmpty()) {
                throw invalid("Repository references an unselected system");
            }
        }
    }

    private void validateNewScopeTarget(Proposal proposal, String systemId, String repositoryId) {
        var hasTarget = false;
        var hasRepositories = false;
        for (var change : proposal.changes()) {
            if ("target".equals(change.path())) {
                hasTarget = true;
                if (!Map.of("type", "system", "id", systemId).equals(change.after())) {
                    throw invalid("New code-search scope must target the selected new system");
                }
            }
            if ("repositories".equals(change.path())) {
                hasRepositories = true;
                if (!(change.after() instanceof List<?> repositories) || repositories.size() != 1
                        || !(repositories.get(0) instanceof Map<?, ?> repository)
                        || !repositoryId.equals(repository.get("repoId"))) {
                    throw invalid("New code-search scope must include only the selected repository");
                }
            }
        }
        if (!hasTarget || !hasRepositories) {
            throw invalid("New code-search scope needs its selected system and repository");
        }
    }

    private void validateScopeRepositoryAppend(
            Proposal proposal, String repositoryId, OperationalContextAssistanceRepositoryFacts.Usage usage
    ) {
        if (proposal.changes().size() != 1 || !"repositories".equals(proposal.changes().get(0).path())) {
            throw invalid("Selected scope update may only append a repository");
        }
        var change = proposal.changes().get(0);
        if (!(change.before() instanceof List<?> before) || !(change.after() instanceof List<?> after)
                || after.size() != before.size() + 1 || !after.subList(0, before.size()).equals(before)
                || !(after.get(after.size() - 1) instanceof Map<?, ?> added)
                || !repositoryId.equals(added.get("repoId"))) {
            throw invalid("Selected scope update must preserve existing repositories and append the new one");
        }
        if ("primary".equals(added.get("role")) || !(added.get("priority") instanceof Number priority)
                || priority.intValue() <= 1 || before.stream().anyMatch(item -> item instanceof Map<?, ?> repo
                && repo.get("priority") instanceof Number existing && priority.intValue() <= existing.intValue())) {
            throw invalid("Selected scope update must keep the existing primary and use a lower-priority repository");
        }
        if (usage == OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY
                && !"library".equals(added.get("role"))) {
            throw invalid("Shared library scope reference must use library role");
        }
    }

    private void validateSelectedRepositoryIdentity(
            Proposal proposal, OperationalContextAssistanceDraftScope scope
    ) {
        if (!"repository".equals(proposal.entityType()) || scope.selectedRepositoryGit() == null) {
            return;
        }
        var gitChange = proposal.changes().stream()
                .filter(change -> "git".equals(change.path()))
                .findFirst();
        if (proposal.operation() == Operation.CREATE && gitChange.isEmpty()) {
            throw invalid("Repository proposal must include the selected source git identity");
        }
        if (gitChange.isEmpty()) {
            return;
        }
        if (gitChange.get().sourceRefs().stream().noneMatch(scope::isSelectedGitLabFileRef)) {
            throw invalid("Repository git identity must cite a read GitLab file from the selected project");
        }
        if (!(gitChange.get().after() instanceof Map<?, ?> git)) {
            throw invalid("Repository git identity must be an object");
        }
        var selected = scope.selectedRepositoryGit();
        if (!selected.provider().equals(git.get("provider"))
                || !selected.group().equals(git.get("group"))
                || !selected.project().equals(git.get("project"))
                || !selected.projectPath().equals(git.get("projectPath"))
                || git.containsKey("url") && !Objects.equals(selected.url(), git.get("url"))) {
            throw invalid("Repository git identity differs from the selected GitLab source");
        }
    }

    private Proposal proposal(JsonNode node, String location, Set<String> allowedSourceRefs) {
        requireObject(node, location, PROPOSAL_FIELDS);
        var operation = enumValue(Operation.class, text(node, "operation", location), location + ".operation");
        var entityType = text(node, "entityType", location);
        OperationalContextCatalogEntityType type;
        try {
            type = OperationalContextCatalogEntityType.fromExternalName(entityType);
        } catch (OperationalContextCatalogMaintenanceException exception) {
            throw invalid(location + ".entityType is unsupported");
        }
        if (!entityType.equals(type.externalName())) {
            throw invalid(location + ".entityType must use the canonical name");
        }
        var entityId = text(node, "entityId", location);
        if (!ID.matcher(entityId).matches()) {
            throw invalid(location + ".entityId is invalid");
        }
        var changesNode = requireArray(node, "changes", location, MAX_CHANGES);
        if (changesNode.isEmpty()) {
            throw invalid(location + ".changes must not be empty");
        }
        var changes = new ArrayList<FieldChange>();
        var paths = new HashSet<String>();
        for (var index = 0; index < changesNode.size(); index++) {
            var change = change(changesNode.get(index), location + ".changes[" + index + "]",
                    operation, entityType, allowedSourceRefs);
            if (!paths.add(change.path())) {
                throw invalid(location + ".changes contains duplicate path " + change.path());
            }
            changes.add(change);
        }
        return new Proposal(
                operation,
                entityType,
                entityId,
                changes,
                enumValue(Confidence.class, text(node, "confidence", location), location + ".confidence"),
                booleanValue(node, "requiresConfirmation", location),
                textList(node, "questions", location, MAX_LIST),
                textList(node, "visibilityLimits", location, MAX_LIST)
        );
    }

    private FieldChange change(
            JsonNode node,
            String location,
            Operation operation,
            String entityType,
            Set<String> allowedSourceRefs
    ) {
        requireObject(node, location, CHANGE_FIELDS);
        var path = text(node, "path", location);
        if (!TOP_LEVEL_FIELD.matcher(path).matches() || "id".equals(path)) {
            throw invalid(location + ".path must be a writable top-level field other than id");
        }
        if (operation == Operation.UPDATE && !node.has("before")) {
            throw invalid(location + ".before is required for UPDATE");
        }
        var afterNode = node.get("after");
        if (afterNode == null || afterNode.isNull() || afterNode.isNumber() || afterNode.isBoolean()) {
            throw invalid(location + ".after must be a non-null catalog value");
        }
        var after = value(afterNode);
        var fieldErrors = maintenanceService.validatePartialEditablePayload(entityType, Map.of(path, after));
        if (!fieldErrors.isEmpty()) {
            throw invalid(location + ".after is not writable: " + fieldErrors.get(0).pointer());
        }
        var beforeNode = node.get("before");
        Object before = beforeNode == null || beforeNode.isNull() ? null : value(beforeNode);
        if (before != null && !maintenanceService.validatePartialEditablePayload(entityType, Map.of(path, before)).isEmpty()) {
            throw invalid(location + ".before has an invalid catalog shape");
        }
        if ("system".equals(entityType) && "systemSubtype".equals(path)
                && after instanceof String subtype && "frontend".equalsIgnoreCase(subtype)) {
            throw invalid(location + " cannot confirm frontend subtype");
        }
        if ("ownership".equals(path) && after instanceof Map<?, ?> ownership
                && ("explicit".equalsIgnoreCase(String.valueOf(ownership.get("ownershipStatus")))
                || ownership.containsKey("ownerTeamIds") || ownership.containsKey("ownerLabel"))) {
            throw invalid(location + " cannot confirm ownership");
        }
        if ("repository".equals(entityType) && "repositoryType".equals(path)
                && after instanceof String repositoryType && "frontend".equalsIgnoreCase(repositoryType)) {
            throw invalid(location + " cannot confirm frontend repository type");
        }
        var basis = enumValue(Basis.class, text(node, "basis", location), location + ".basis");
        var reason = text(node, "reason", location);
        var sourceRefs = textList(node, "sourceRefs", location, MAX_LIST);
        if (sourceRefs.isEmpty() || sourceRefs.stream().anyMatch(ref -> !allowedSourceRefs.contains(ref))) {
            throw invalid(location + ".sourceRefs must be collected sources");
        }
        if (basis == Basis.USER_STATEMENT && sourceRefs.stream().noneMatch(ref -> ref.startsWith("operator:"))) {
            throw invalid(location + ".sourceRefs must include an operator statement for USER_STATEMENT");
        }
        if (basis == Basis.SOURCE_OBSERVATION && sourceRefs.stream().noneMatch(ref ->
                ref.startsWith("gitlab:") || ref.startsWith("opctx:"))) {
            throw invalid(location + ".sourceRefs needs a catalog document or GitLab file for SOURCE_OBSERVATION");
        }
        return new FieldChange(
                path,
                before,
                after,
                reason,
                basis,
                sourceRefs,
                enumValue(Confidence.class, text(node, "confidence", location), location + ".confidence"),
                booleanValue(node, "requiresConfirmation", location)
        );
    }

    private void requireObject(JsonNode node, String location, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw invalid(location + " must be an object");
        }
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!allowedFields.contains(field)) {
                throw invalid(location + " contains unknown field " + field);
            }
        }
    }

    private JsonNode requireArray(JsonNode owner, String field, String location, int maxSize) {
        var node = owner.get(field);
        if (node == null || !node.isArray() || node.size() > maxSize) {
            throw invalid(location + "." + field + " must be an array within the limit");
        }
        return node;
    }

    private List<String> textList(JsonNode owner, String field, String location, int maxSize) {
        var node = requireArray(owner, field, location, maxSize);
        var result = new ArrayList<String>();
        for (var index = 0; index < node.size(); index++) {
            if (!node.get(index).isTextual()) {
                throw invalid(location + "." + field + " must contain text");
            }
            var text = node.get(index).asText();
            if (text.isBlank() || text.length() > MAX_TEXT) {
                throw invalid(location + "." + field + " contains blank or oversized text");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private String text(JsonNode owner, String field, String location) {
        var node = owner.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank() || node.asText().length() > MAX_TEXT) {
            throw invalid(location + "." + field + " must contain bounded text");
        }
        return node.asText();
    }

    private boolean booleanValue(JsonNode owner, String field, String location) {
        var node = owner.get(field);
        if (node == null || !node.isBoolean()) {
            throw invalid(location + "." + field + " must be boolean");
        }
        return node.asBoolean();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String location) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalid(location + " is unsupported");
        }
    }

    private Object value(JsonNode node) {
        if (node.isObject()) {
            var values = new LinkedHashMap<String, Object>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), value(entry.getValue())));
            return Collections.unmodifiableMap(values);
        }
        if (node.isArray()) {
            var values = new ArrayList<Object>();
            node.forEach(item -> values.add(value(item)));
            return Collections.unmodifiableList(values);
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return null;
    }

    private OperationalContextAssistanceDraftParseException invalid(String message) {
        return new OperationalContextAssistanceDraftParseException(message);
    }
}
