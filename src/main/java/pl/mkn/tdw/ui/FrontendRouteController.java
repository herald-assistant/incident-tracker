package pl.mkn.tdw.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class FrontendRouteController {

    @GetMapping({
            "/evidence",
            "/elastic",
            "/gitlab"
    })
    String forwardIntegrationConsoles() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/incident-analysis",
            "/incident-analysis/**"
    })
    String forwardIncidentAnalysis() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/analysis-history",
            "/analysis-history/**"
    })
    String forwardAnalysisHistory() {
        return "forward:/index.html";
    }

    @GetMapping("/database")
    String forwardDatabase() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/flow-explorer",
            "/flow-explorer/**"
    })
    String forwardFlowExplorer() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/operational-context",
            "/operational-context/**"
    })
    String forwardOperationalContext() {
        return "forward:/index.html";
    }

}
