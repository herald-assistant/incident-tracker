package pl.mkn.tdw.shared.ai;

import java.time.Instant;
import java.util.List;

public final class AnalysisAiTestFixtures {
    private AnalysisAiTestFixtures() {}
    public static AnalysisAiInvocation invocation(String prompt, String raw, AnalysisAiUsage usage) {
        return new AnalysisAiInvocation("call-1", "ASSESSMENT", raw != null ? "RAW_RESPONSE" : "RUNNING",
                1, 1, List.of("CRM/customers!1#file:0:src/Customer.java"), 100, 1000, 10,
                prompt, raw, List.of(), List.of(), "crm-session", usage, null, Instant.parse("2026-07-01T10:00:00Z"), null);
    }
}
