package com.locohiring.service.controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the hand-written OpenAPI spec (from the locohiring-api-spec jar), which Swagger UI renders. */
@RestController
public class SpecController {

    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public Resource spec() {
        return new ClassPathResource("locohiring-api.yaml");
    }
}
