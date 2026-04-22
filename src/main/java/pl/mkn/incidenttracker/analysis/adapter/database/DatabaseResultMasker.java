package pl.mkn.incidenttracker.analysis.adapter.database;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "analysis.database", name = "enabled", havingValue = "true")
public class DatabaseResultMasker {

    private static final List<String> SENSITIVE_TOKENS = List.of(
            "PASSWORD",
            "PASS",
            "TOKEN",
            "SECRET",
            "API_KEY",
            "SESSION",
            "AUTH",
            "CREDENTIAL"
    );

    public List<Map<String, Object>> maskRows(List<Map<String, Object>> rows) {
        var maskedRows = new ArrayList<Map<String, Object>>();

        for (var row : rows != null ? rows : List.<Map<String, Object>>of()) {
            maskedRows.add(maskRow(row));
        }

        return List.copyOf(maskedRows);
    }

    public Map<String, Object> maskRow(Map<String, Object> row) {
        var maskedRow = new LinkedHashMap<String, Object>();
        if (row == null) {
            return maskedRow;
        }

        for (var entry : row.entrySet()) {
            maskedRow.put(entry.getKey(), shouldMask(entry.getKey()) ? "<masked>" : entry.getValue());
        }

        return maskedRow;
    }

    private boolean shouldMask(String columnName) {
        if (columnName == null) {
            return false;
        }

        var normalized = columnName.toUpperCase(Locale.ROOT);
        return SENSITIVE_TOKENS.stream().anyMatch(normalized::contains);
    }
}
