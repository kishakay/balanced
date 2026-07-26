package com.balanced.ledger.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes to index.html when the UI is packaged into the JAR.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/accounts",
            "/accounts/**",
            "/transfers",
            "/journal",
            "/journal/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
