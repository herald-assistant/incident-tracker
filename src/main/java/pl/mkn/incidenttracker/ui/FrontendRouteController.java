package pl.mkn.incidenttracker.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class FrontendRouteController {

    @GetMapping("/evidence")
    String forwardEvidence() {
        return "forward:/index.html";
    }

}
