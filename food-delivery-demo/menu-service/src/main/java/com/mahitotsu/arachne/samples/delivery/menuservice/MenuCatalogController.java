package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/menu", produces = MediaType.APPLICATION_JSON_VALUE)
class MenuCatalogController {

    private final MenuRepository repository;

    MenuCatalogController(MenuRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/catalog")
    List<MenuItem> catalog() {
        return repository.findAll();
    }
}