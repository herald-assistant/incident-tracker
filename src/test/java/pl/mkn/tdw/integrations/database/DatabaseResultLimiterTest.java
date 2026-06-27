package pl.mkn.tdw.integrations.database;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseResultLimiterTest {

    @Test
    void shouldLimitRowsColumnsAndCharacterBudget() {
        var properties = new DatabaseToolProperties();
        properties.setMaxRows(2);
        properties.setMaxColumns(1);
        properties.setMaxResultCharacters(15);
        var limiter = new DatabaseResultLimiter(properties);

        var firstRow = new LinkedHashMap<String, Object>();
        firstRow.put("CUSTOMER_ID", "1");
        firstRow.put("STATUS", "ACTIVE");
        var secondRow = new LinkedHashMap<String, Object>();
        secondRow.put("CUSTOMER_ID", "2");
        secondRow.put("STATUS", "ACTIVE");
        var thirdRow = new LinkedHashMap<String, Object>();
        thirdRow.put("CUSTOMER_ID", "3");

        var result = limiter.limitRows(List.of(firstRow, secondRow, thirdRow), 3);

        assertTrue(result.truncated());
        assertEquals(1, result.rows().size());
        assertEquals(List.of("CUSTOMER_ID"), result.rows().get(0).keySet().stream().toList());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Column limit")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Maximum serialized result size")));
    }
}
