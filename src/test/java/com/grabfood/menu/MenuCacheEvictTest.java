package com.grabfood.menu;

import com.grabfood.menu.entity.Menu;
import com.grabfood.menu.service.MenuService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

@SpringBootTest
class MenuCacheEvictTest {

    @Autowired
    private MenuService menuService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Kiểm tra toàn bộ quy trình: Đọc ban đầu -> Nạp Cache -> Cập nhật DB -> @CacheEvict xóa cache -> Đọc lại ra giá mới")
    void testCacheEvictWorkflow() {
        Long dishId = 1L;
        String cacheKey = "menuCache::" + dishId;

        // Reset ban đầu về 55000 nếu có
        Menu initialSetup = new Menu(dishId, "Phở Bò", new BigDecimal("55000"));
        menuService.updateMenu(initialSetup);

        // BƯỚC 1: Lần đầu đọc món ăn -> Cache Miss, nạp vào Redis
        Menu initial = menuService.getMenuById(dishId);
        Assertions.assertEquals(0, new BigDecimal("55000").compareTo(initial.getPrice()));
        Assertions.assertEquals("Phở Bò", initial.getDishName());

        // Kiểm tra Redis: key phải tồn tại
        String cacheBeforeUpdate = stringRedisTemplate.opsForValue().get(cacheKey);
        Assertions.assertNotNull(cacheBeforeUpdate, "Key " + cacheKey + " phải tồn tại trong Redis");
        Assertions.assertTrue(cacheBeforeUpdate.contains("55000"), "Cache phải chứa giá cũ 55000");
        System.out.println(">>> [TRƯỚC KHI UPDATE] Cache trong Redis: " + cacheBeforeUpdate);

        // BƯỚC 2 & 3: Cập nhật DB và @CacheEvict xóa cache
        Menu updatePayload = new Menu(dishId, "Phở Bò Đặc Biệt", new BigDecimal("75000"));
        Menu updatedResult = menuService.updateMenu(updatePayload);
        Assertions.assertEquals(0, new BigDecimal("75000").compareTo(updatedResult.getPrice()));

        // BƯỚC 4: Kiểm tra Redis sau khi update: Key "menuCache::1" PHẢI BỊ XÓA HOÀN TOÀN!
        String cacheAfterUpdate = stringRedisTemplate.opsForValue().get(cacheKey);
        Assertions.assertNull(cacheAfterUpdate, "Sau update, key " + cacheKey + " phải bị xóa khỏi Redis (null)");
        System.out.println(">>> [SAU KHI UPDATE] Cache trong Redis: " + cacheAfterUpdate + " (ĐÃ BỊ XÓA THÀNH CÔNG)");

        // BƯỚC 5: Gọi lại getMenuById(1) -> Cache Miss, đọc giá mới từ DB và nạp lại vào Redis
        Menu reloaded = menuService.getMenuById(dishId);
        Assertions.assertEquals(0, new BigDecimal("75000").compareTo(reloaded.getPrice()), "Giá mới phải là 75000");
        Assertions.assertEquals("Phở Bò Đặc Biệt", reloaded.getDishName());

        // Kiểm tra Redis đã được nạp lại dữ liệu mới
        String cacheReloaded = stringRedisTemplate.opsForValue().get(cacheKey);
        Assertions.assertNotNull(cacheReloaded, "Redis phải được nạp lại dữ liệu mới");
        Assertions.assertTrue(cacheReloaded.contains("75000"), "Cache mới phải chứa giá 75000");
        System.out.println(">>> [NẠP LẠI CACHE] Cache mới trong Redis: " + cacheReloaded);
    }
}
