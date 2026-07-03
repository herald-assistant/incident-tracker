package pl.mkn.tdw.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
class FrontendRouteController {

    @GetMapping({
            "/{route:^(?!api$|assets$|actuator$|error$|mcp$|sse$)[^.]+$}",
            "/{route:^(?!api$|assets$|actuator$|error$|mcp$|sse$)[^.]+$}/{*remaining}"
    })
    String forwardFrontendRoute(HttpServletRequest request) {
        if (request.getRequestURI().contains(".")) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        return "forward:/index.html";
    }

}
