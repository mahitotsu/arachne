package com.mahitotsu.arachne.samples.delivery.menuservice.api;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.MenuRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/menu", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Menu Catalog", description = "menu-service が公開する読み取り専用のメニュー catalog エンドポイントです。")
public class MenuCatalogController {

    private final MenuRepository repository;

    MenuCatalogController(MenuRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/catalog")
    @Operation(summary = "List current menu catalog", description = "現在のメニュー catalog を決定論的な menu item 一覧として返します。")
    List<MenuItem> catalog() {
        return repository.findAll();
    }
}