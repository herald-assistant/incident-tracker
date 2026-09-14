package pl.mkn.tdw.features.operationalcontextassistance.draft;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalBatchCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.List;
import java.util.Objects;

/** Read-only validation shared by the AI preflight tool and the final job response. */
@Component
@RequiredArgsConstructor
public class OperationalContextAssistanceDraftPreflight {

    private static final int MAX_ISSUES = 12;
    private final OperationalContextAssistanceDraftParser parser;
    private final OperationalContextCatalogMaintenanceService maintenanceService;
    private final OperationalContextPort catalogPort;

    public Result validate(String content, OperationalContextAssistanceDraftScope scope, String expectedDigest) {
        try {
            return validateParsed(parser.parse(content, scope), expectedDigest);
        } catch (OperationalContextAssistanceDraftParseException exception) {
            return invalid(List.of(new Issue("/draft", exception.getMessage())));
        }
    }

    public Result validateParsed(OperationalContextAssistanceDraft draft, String expectedDigest) {
        if (draft == null || expectedDigest == null || expectedDigest.isBlank()) {
            return invalid(List.of(new Issue("/draft", "Draft and pinned catalog digest are required")));
        }
        if (!Objects.equals(expectedDigest, catalogPort.currentSnapshot().contentDigest())) {
            return new Result(false, true, List.of(new Issue("/expectedDigest", "Catalog changed during analysis")));
        }
        if (draft.proposals().isEmpty()) {
            return new Result(true, false, List.of());
        }
        var mutations = draft.proposals().stream().map(proposal ->
                new OperationalContextCatalogConditionalBatchCommand.Mutation(
                        proposal.entityType(), proposal.entityId(),
                        OperationalContextCatalogConditionalMutationCommand.Operation.valueOf(proposal.operation().name()),
                        proposal.changes().stream().map(change ->
                                new OperationalContextCatalogConditionalMutationCommand.FieldChange(
                                        change.path(), change.before(), change.after())).toList()))
                .toList();
        try {
            var preview = maintenanceService.previewAcceptedBatch(
                    new OperationalContextCatalogConditionalBatchCommand(expectedDigest, mutations));
            return new Result(preview.valid(), false, preview.violations().stream()
                    .limit(MAX_ISSUES)
                    .map(violation -> new Issue("/catalog", violation.ruleCode() + ": " + violation.fingerprint()))
                    .toList());
        } catch (OperationalContextCatalogMaintenanceException exception) {
            return new Result(false,
                    exception.code() == OperationalContextCatalogMaintenanceException.Code.STALE_PROPOSAL,
                    exception.fieldErrors().isEmpty()
                            ? List.of(new Issue("/draft", exception.getMessage()))
                            : exception.fieldErrors().stream().limit(MAX_ISSUES)
                                    .map(error -> new Issue(error.pointer(), error.message())).toList());
        }
    }

    private Result invalid(List<Issue> issues) {
        return new Result(false, false, issues);
    }

    public record Result(boolean valid, boolean stale, List<Issue> issues) {
        public Result {
            issues = issues != null ? List.copyOf(issues) : List.of();
        }
    }

    public record Issue(String pointer, String message) {
        public Issue {
            pointer = abbreviate(pointer, 160);
            message = abbreviate(message, 512);
        }

        private static String abbreviate(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
        }
    }
}
