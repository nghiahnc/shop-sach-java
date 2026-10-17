# Kiểm chứng bản giao diện và demo điền sẵn

Thực thi **17/10/2026**, Java 21.0.12.1, Maven 3.9.11, PostgreSQL 17/Neon, Edge/Playwright. URL: https://shop-sach-java.vercel.app. Báo cáo phiên bản trước nằm ở `QA-2026-10-16.md`.

## Java: 52/52 PASS

| Nhóm | Số test | Phạm vi |
|---|---:|---|
| Domain | 19 | Tiền, giỏ, giảm giá, ship, trạng thái và snapshot |
| Recovery | 2 | Khóa test, orphan vượt cửa sổ idempotency |
| Demo defaults/service | 5 | Mặc định tắt, 1.000 email không trùng, tái sử dụng profile, lưu canonical reference, lỗi provider |
| Saved card gateway | 2 | Customer/redisplay/idempotency được truyền; chặn khóa live trước khi cấp fixture |
| ArchUnit | 4 | Domain thuần và ranh giới tầng |
| Integration | 20 | Flyway, tranh chấp tồn/đơn/hủy, rollback, webhook, ownership/CSRF, hash, tất cả template, demo defaults và profile ON CONFLICT |

`artifacts/redesign-tests.log` kết thúc BUILD SUCCESS. Test integration chạy ở nhánh Neon test riêng; gateway Stripe được mock trong suite này. Maven build executable JAR thành công. V4/V5 đã chạy trên nhánh test trước production.

## Giao diện và form trên local / Vercel

- Kiểm tra 375/768/1440px: trang chủ, danh mục, chi tiết sách, đăng ký, đăng nhập, giới thiệu, checkout và các trang admin. Không tràn ngang; ảnh tải thành công.
- Trang chủ 8 sách chọn lọc; danh mục 12/trang, tổng 18 sách thật. Menu native dialog đóng bằng Escape; tìm Effective Java trả đúng sách.
- Đăng ký không gõ dữ liệu, email UUID khác giữa hai lần mở; đăng nhập giữ email vừa đăng ký. CUSTOMER bị 403 tại admin.
- Số lượng 1, coupon DOCSACH10, người nhận/điện thoại/địa chỉ và Stripe có sẵn. Mua COD, xóa giỏ khi lưu, quản trị chuyển giao/hoàn tất và ghi doanh thu.
- Admin thêm sách có dữ liệu hợp lệ; chỉnh sửa dùng dữ liệu đang lưu. Không công khai admin password trong fixture/HTML.
- Validation thực tế trên Vercel: email đăng ký trùng, điện thoại checkout sai, giá sách admin dưới mức tối thiểu, coupon % trên 100, tên thể loại quá dài. Tất cả giữ dữ liệu đã sửa và hiển thị lỗi; không tạo đơn/thay đổi dữ liệu khi không hợp lệ.
- Phiên đăng nhập và giỏ còn sau khởi động lại JVM, kiểm tra bằng storage state cũ không đăng nhập lại.

Minh chứng: `artifacts/redesign-development/`, `artifacts/redesign-production/`, `qa.json`, `validation-proof.json`, ảnh từng chiều rộng.

## Stripe sandbox thực tế trên bản mới

1. Đi từ đăng ký → đăng nhập → thêm Clean Code → áp coupon → checkout bằng toàn bộ dữ liệu điền sẵn. Hosted Checkout hiển thị Visa ••••4242; bấm Pay, **không gõ PAN/hạn/CVC**. Thành công và biên nhận đã thu.
2. Provider trả Checkout complete/paid, PaymentIntent succeeded, currency VND; webhook thật tồn tại trong payment_events trước bước replay. Replay sự kiện có chữ ký hợp lệ trả applied=false, tồn không đổi.
3. Đơn khác: chọn Change, dùng thẻ test từ chối 4000…0002; đơn vẫn PENDING_PAYMENT/unpaid. Chọn lại thẻ đã lưu 4242 và Pay thành công trên cùng đơn/phiên; tồn không bị trừ lần hai.
4. Chủ động expire Checkout sandbox qua Stripe API: webhook thật đổi đơn EXPIRED và hoàn đúng tồn đã giữ. Không chờ đủ 31 phút tự nhiên.
5. Đối chiếu các PaymentMethod trên Stripe: test-only, Visa last4=4242, attached đúng Customer, allow_redisplay=always. Một người dùng mở hai Checkout khác nhau vẫn dùng cùng Customer/profile.

Proof: `stripe-proof.json`, `saved-card-return.json`, `stripe-retry-proof.json`, `stripe-expiry-proof.json`, `saved-profile-reuse.json`, `catalog-and-profiles-proof.json` trong `artifacts/redesign-production/`. URL Checkout đầy đủ, cookie và secrets chỉ nằm ở `.local`, không đưa vào ZIP.

## Bảo toàn dữ liệu

Đã chụp baseline trước migration, so sánh 18 UUID/giá/tồn và snapshot đơn cũ sau migration. V4 chỉ UPDATE title/author/description, V5 chỉ tạo bảng reference. Sau các giao dịch QA, tồn thay đổi theo mua/hoàn tồn bình thường. Kiểm tra cuối xác nhận UUID/giá và mọi dòng đơn cũ vẫn giữ nguyên.

## Giới hạn

Chỉ sandbox, không giao hàng/thu tiền thật, chưa refund/tải lớn/Safari/WCAG audit. Kiểm chứng expired dùng API expire thay vì đợi hết hạn tự nhiên. Những ảnh/log ngày 16/10 thuộc phiên bản cũ; sử dụng thư mục `redesign-*` cho giao diện mới.

## Bàn giao

`target/tram-sach-1.0.0.jar`, `artifacts/TramSach-source.zip`, ảnh/proof ở `artifacts/redesign-production/`, nguồn ảnh trong `ASSET_SOURCES.md`, kịch bản 5 phút ở `DEMO.md`. Tài khoản CUSTOMER công khai đã điền sẵn; tài khoản ADMIN riêng chỉ có trong `.local/DEMO-ACCOUNTS.md` của chủ workspace.

## Bản tối ưu tải trang tiếp theo

Bộ Java hiện đạt **58/58**, bổ sung sáu test cache/session/CSRF/query. Đã kiểm tra lại local/Vercel, Stripe sandbox và việc đăng xuất rồi Back. Số đo trước/sau và sau sáu phút nghỉ ghi riêng trong [PERFORMANCE.md](PERFORMANCE.md); bằng chứng mới nằm trong `artifacts/performance*`.
