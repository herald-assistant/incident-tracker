package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseResultLimiter {

    private final DatabaseToolProperties properties;

    public LimitedRowsResult limitRows(List<Map<String, Object>> rows, Integer requestedMaxRows) {
        var effectiveMaxRows = normalizePositive(requestedMaxRows, properties.getMaxRows());
        var limitedRows = new ArrayList<Map<String, Object>>();
        var warnings = new ArrayList<String>();
        var characterCount = 0;
        var truncated = false;

        for (var originalRow : rows != null ? rows : List.<Map<String, Object>>of()) {
            if (limitedRows.size() >= effectiveMaxRows) {
                truncated = true;
                warnings.add("Row limit reached; additional rows were truncated.");
                break;
            }

            var limitedRow = new LinkedHashMap<String, Object>();
            var columnIndex = 0;
            for (var entry : originalRow.entrySet()) {
                if (columnIndex >= properties.getMaxColumns()) {
                    truncated = true;
                    if (!warnings.contains("Column limit reached; additional columns were omitted.")) {
                        warnings.add("Column limit reached; additional columns were omitted.");
                    }
                    break;
                }

                limitedRow.put(entry.getKey(), entry.getValue());
                characterCount += safeLength(entry.getKey()) + safeLength(String.valueOf(entry.getValue()));
                columnIndex++;
            }

            if (characterCount > properties.getMaxResultCharacters()) {
                truncated = true;
                warnings.add("Maximum serialized result size reached; additional rows were truncated.");
                break;
            }

            limitedRows.add(limitedRow);
        }

        return new LimitedRowsResult(
                List.copyOf(limitedRows),
                truncated,
                List.copyOf(warnings)
        );
    }

    private int normalizePositive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private int safeLength(String value) {
        return value != null ? value.length() : 0;
    }

    public record LimitedRowsResult(
            List<Map<String, Object>> rows,
            boolean truncated,
            List<String> warnings
    ) {
    }
}
