package com.grabfood.menu.controller;

import com.grabfood.menu.entity.Menu;
import com.grabfood.menu.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * API xem thông tin món ăn: GET /menu/{id}
     */
    @GetMapping("/menu/{id}")
    public ResponseEntity<Menu> getMenuById(@PathVariable Long id) {
        Menu menu = menuService.getMenuById(id);
        return ResponseEntity.ok(menu);
    }

    /**
     * API cập nhật món ăn: PUT /menu
     * Nhận body JSON dạng:
     * {
     *   "id": 1,
     *   "dish_name": "Phở Bò Đặc Biệt",
     *   "price": 75000
     * }
     */
    @PutMapping("/menu")
    public ResponseEntity<Menu> updateMenu(@RequestBody Menu menu) {
        Menu updated = menuService.updateMenu(menu);
        return ResponseEntity.ok(updated);
    }

    /**
     * Hỗ trợ thêm method POST cho các client / curl đơn giản
     */
    @PostMapping("/menu/update")
    public ResponseEntity<Menu> updateMenuPost(@RequestBody Menu menu) {
        Menu updated = menuService.updateMenu(menu);
        return ResponseEntity.ok(updated);
    }
}
