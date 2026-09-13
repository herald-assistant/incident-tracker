package pl.mkn.tdw.features.incidentanalysis.job.api;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.incidentanalysis.job.error.AnalysisJobInputException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJobStartRequestTest {

    @Test
    void shouldTreatBlankProblemDescriptionAsAbsent() {
        var request = request("  \n  ");

        assertNull(request.problemDescription());
        assertDoesNotThrow(request::validateForStart);
    }

    @Test
    void shouldAcceptProblemDescriptionAtLimitAndRejectLongerInput() {
        var atLimit = request("  " + "x".repeat(4000) + "  ");
        assertEquals(4000, atLimit.problemDescription().length());
        assertDoesNotThrow(atLimit::validateForStart);

        var overLimit = request("x".repeat(4001));
        var exception = assertThrows(AnalysisJobInputException.class, overLimit::validateForStart);
        assertEquals("VALIDATION_ERROR", exception.code());
        assertTrue(exception.getMessage().contains("problemDescription must not exceed 4000 characters"));
    }

    private AnalysisJobStartRequest request(String problemDescription) {
        return new AnalysisJobStartRequest(
                AnalysisJobLogSource.ELASTICSEARCH,
                "corr-crm-123",
                null,
                null,
                null,
                problemDescription
        );
    }
}
