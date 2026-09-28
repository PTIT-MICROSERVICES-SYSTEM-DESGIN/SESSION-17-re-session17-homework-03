package com.grabfood.menu.service;

import com.grabfood.menu.entity.Menu;
import com.grabfood.menu.repository.MenuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý thực đơn GrabFood áp dụng chiến lược Cache-Aside và Cache Eviction.
 *
 * =========================================================================================
 * GIẢI THÍCH: VÌ SAO CHỌN XÓA CACHE (@CacheEvict) THAY VÌ GHI ĐÈ CACHE (@CachePut)?
 * =========================================================================================
 * 1. PHÒNG TRÁNH RACE CONDITION TRONG MÔI TRƯỜNG ĐỒNG THỜI (CONCURRENT WRITES):
 *    - Giả sử có 2 request ghi đồng thời cùng món ăn:
 *      + Request A muốn đổi giá thành 70.000đ.
 *      + Request B muốn đổi giá thành 75.000đ.
 *    - Trình tự ghi vào DB: A ghi trước -> B ghi sau (Giá chuẩn cuối cùng trong DB là 75.000đ).
 *    - Nếu dùng @CachePut (ghi đè cache ngay lập tức): Do độ trễ mạng hoặc context-switching,
 *      B có thể ghi đè cache trước, rồi A mới ghi đè cache sau.
 *      HẬU QUẢ: Trong DB giá là 75.000đ, nhưng trong Redis lại là 70.000đ! Dữ liệu bị sai lệch
 *      vĩnh viễn (Stale Data) cho đến lần update tiếp theo.
 *    - Khi dùng @CacheEvict (Cache-Aside): Cả A và B đều chỉ XÓA cache. Cache trở về trạng thái rỗng.
 *      Lần đọc tiếp theo (GET) sẽ truy vấn DB (đang lưu giá 75.000đ chuẩn xác của B) và nạp lại vào cache.
 *      => Loại bỏ hoàn toàn rủi ro Race Condition giữa các tiến trình ghi!
 *
 * 2. TỐI ƯU HIỆU NĂNG VÀ BĂNG THÔNG (WRITE/READ RATIO - LAZY POPULATION):
 *    - Trong thực tế, có những dữ liệu được cập nhật nhiều lần liên tục nhưng chưa chắc đã có người đọc ngay.
 *      Nếu dùng @CachePut, hệ thống tốn CPU để serialize JSON và gửi qua Redis vô ích.
 *    - Với @CacheEvict, chỉ cần xóa key 1 lần (chi phí O(1) cực nhẹ). Chỉ khi nào có khách hàng truy cập,
 *      dữ liệu mới được tải và nạp vào cache theo nhu cầu (On-Demand).
 *
 * 3. THỨ TỰ THỰC THI: CẬP NHẬT DB TRƯỚC, XÓA CACHE SAU:
 *    - @CacheEvict với beforeInvocation = false (mặc định) đảm bảo câu lệnh menuRepository.save(menu)
 *      thành công vào DB trước. Nếu DB gặp lỗi, cache không bị xóa bậy.
 * =========================================================================================
 */
@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * Lấy thông tin món ăn theo ID.
     * Sử dụng @Cacheable: Lần đầu truy vấn DB và lưu vào cache "menuCache" với key là #id.
     * Các lần sau lấy trực tiếp từ Redis.
     */
    @Cacheable(value = "menuCache", key = "#id")
    public Menu getMenuById(Long id) {
        log.info(">>> [DATABASE QUERY] Đang truy vấn CSDL lấy thông tin món ăn id: {}", id);
        return menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));
    }

    /**
     * Cập nhật thông tin món ăn (giá, tên món).
     * Áp dụng Cache-Aside:
     * - Bước 2: Cập nhật Database trước qua menuRepository.save(menu).
     * - Bước 3: Đánh dấu @CacheEvict để xóa cache cũ của món ăn (key = "#menu.id"),
     *   đảm bảo lần đọc tiếp theo sẽ lấy dữ liệu mới nhất từ CSDL và nạp lại vào cache.
     *
     * @param menu Đối tượng menu chứa ID và thông tin cần cập nhật
     * @return Đối tượng Menu đã lưu thành công vào CSDL
     */
    @Transactional
    @CacheEvict(value = "menuCache", key = "#menu.id")
    public Menu updateMenu(Menu menu) {
        log.info("========================================================================");
        log.info(">>> [BƯỚC 1] Nhận yêu cầu cập nhật món ăn id: {}", menu.getId());
        log.info(">>> [BƯỚC 2] Đang cập nhật vào DATABASE trước...");

        Menu savedMenu = menuRepository.save(menu);

        log.info(">>> [BƯỚC 2 - XONG] Đã lưu vào CSDL thành công: Tên = '{}', Giá = {}",
                savedMenu.getDishName(), savedMenu.getPrice());
        log.info(">>> [BƯỚC 3] @CacheEvict kích hoạt: Xóa key 'menuCache::{}' khỏi Redis", menu.getId());
        log.info("========================================================================");

        return savedMenu;
    }
}
