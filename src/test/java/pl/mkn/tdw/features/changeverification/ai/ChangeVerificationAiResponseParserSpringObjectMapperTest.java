package pl.mkn.tdw.features.changeverification.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ChangeVerificationAiResponseParserSpringObjectMapperTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldParseFencedLedgerWithRuntimeObjectMapper() {
        var parser = new ChangeVerificationAiResponseParser(objectMapper);

        var response = parser.parse("```json\n" + ChangeVerificationAiResponseParserTest.validLedger() + "\n```");

        assertThat(response.rules()).hasSize(1);
        assertThat(response.additionalChecks()).hasSize(1);
    }
}
