package pl.mkn.incidenttracker.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Incident Tracker")))
                .andExpect(content().string(containsString("<html lang=\"pl\"")))
                .andExpect(content().string(containsString("<app-root>")))
                .andExpect(content().string(containsString("main-")))
                .andExpect(content().string(containsString("styles-")));
    }

    @Test
    void shouldServeEvidenceRoute() throws Exception {
        mockMvc.perform(get("/evidence"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

}
