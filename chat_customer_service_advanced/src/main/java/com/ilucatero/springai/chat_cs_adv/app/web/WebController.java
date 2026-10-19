package com.ilucatero.springai.chat_cs_adv.app.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web controller for serving Thymeleaf views.
 * Uses client-side JavaScript to call REST APIs.
 */
@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
