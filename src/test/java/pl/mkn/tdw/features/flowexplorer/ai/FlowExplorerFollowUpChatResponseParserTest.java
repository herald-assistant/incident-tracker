package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerFollowUpChatResponseParserTest {

    private final FlowExplorerFollowUpChatResponseParser parser =
            new FlowExplorerFollowUpChatResponseParser(new ObjectMapper());

    @Test
    void shouldParseMessageOnlyResponse() {
        var response = parser.parse("""
                {
                  "message": "Walidacja jest w CustomerService.validate."
                }
                """);

        assertEquals("Walidacja jest w CustomerService.validate.", response.message());
        assertFalse(response.hasResultUpdate());
        assertNull(response.resultUpdate());
    }

    @Test
    void shouldParsePartialResultUpdateResponse() {
        var response = parser.parse("""
                {
                  "message": "Zaktualizowalem Flow i Persistence.",
                  "resultUpdate": {
                    "overview": {
                      "markdown": "Endpoint pobiera profil klienta i sprawdza status.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                    },
                    "sections": [
                      {
                        "id": "FUNCTIONAL_FLOW",
                        "markdown": "Controller przyjmuje request i deleguje do serwisu.",
                        "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                        "visibilityLimits": [],
                        "openQuestions": ["Czy status inactive ma byc widoczny dla operatora?"]
                      },
                      {
                        "id": "PERSISTENCE",
                        "markdown": "Repository odczytuje profil klienta po id."
                      }
                    ],
                    "globalVisibilityLimits": [],
                    "confidence": "medium"
                  }
                }
                """);

        assertEquals("Zaktualizowalem Flow i Persistence.", response.message());
        assertTrue(response.hasResultUpdate());
        assertNotNull(response.resultUpdate());
        assertEquals(
                "Endpoint pobiera profil klienta i sprawdza status.",
                response.resultUpdate().get("overview").get("markdown").asText()
        );
        assertEquals(2, response.resultUpdate().get("sections").size());
        assertEquals(0, response.resultUpdate().get("globalVisibilityLimits").size());
        assertFalse(response.resultUpdate().has("globalOpenQuestions"));
    }

    @Test
    void shouldRejectMissingMessage() {
        var exception = assertThrows(
                FlowExplorerFollowUpChatResponseParseException.class,
                () -> parser.parse("""
                        {
                          "resultUpdate": {
                            "overview": {
                              "markdown": "Nowy opis."
                            }
                          }
                        }
                        """)
        );

        assertTrue(exception.getMessage().contains("message is required"));
    }

    @Test
    void shouldRejectUnknownSection() {
        var exception = assertThrows(
                FlowExplorerFollowUpChatResponseParseException.class,
                () -> parser.parse("""
                        {
                          "message": "Zaktualizowalem sekcje.",
                          "resultUpdate": {
                            "sections": [
                              {
                                "id": "UNKNOWN_SECTION",
                                "markdown": "Nieznana sekcja."
                              }
                            ]
                          }
                        }
                        """)
        );

        assertTrue(exception.getMessage().contains("section.id must be one of"));
    }

    @Test
    void shouldRejectInvalidConfidence() {
        var exception = assertThrows(
                FlowExplorerFollowUpChatResponseParseException.class,
                () -> parser.parse("""
                        {
                          "message": "Zaktualizowalem wynik.",
                          "resultUpdate": {
                            "confidence": "certain"
                          }
                        }
                        """)
        );

        assertTrue(exception.getMessage().contains("confidence must be high, medium or low"));
    }

    @Test
    void shouldRejectMalformedJsonOrExtraText() {
        assertThrows(
                FlowExplorerFollowUpChatResponseParseException.class,
                () -> parser.parse("Niepoprawny tekst poza JSON")
        );
        assertThrows(
                FlowExplorerFollowUpChatResponseParseException.class,
                () -> parser.parse("""
                        {
                          "message": "OK"
                        }
                        trailing text
                        """)
        );
    }
}
