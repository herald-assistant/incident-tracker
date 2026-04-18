package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

final class OperationalContextMatchScore {

    private int score;
    private final List<String> reasons = new ArrayList<>();

    void add(int value, String reason) {
        score += value;
        if (StringUtils.hasText(reason) && reasons.size() < 8) {
            reasons.add(reason);
        }
    }

    int score() {
        return score;
    }

    List<String> reasons() {
        return List.copyOf(reasons);
    }

}
