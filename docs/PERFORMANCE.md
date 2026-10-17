# Trạm Sách - Kiểm chứng tải trang và cache

Thực thi 17/10/2026 trên https://shop-sach-java.vercel.app. Giữ Vercel, Java 21, Spring Boot và Neon; không đổi gói hoặc thêm dịch vụ trả phí.

## Số đo thực tế

Edge/Playwright desktop 1440px, bốn browser context mới mỗi đợt trên cùng máy/mạng. Trước sửa có ba lần chờ 19-25 giây; đợt sau đo CDN đã được tạo cache. Đây là mẫu đo nhỏ, không phải SLA hay kiểm thử tải.

| Chỉ số | Trước | Sau |
|---|---:|---:|
| Median first contentful paint | 19456 ms | 452 ms |
| Tải xong lâu nhất trong 4 lần | 25364 ms | 621 ms |
| CDN trang chủ | MISS cả 4 lần | HIT cả 4 lần |
| CSS/JS/font/ảnh | no-store | URL hash và cache immutable |

Sau **360 giây không gửi yêu cầu vào app**: HTTP đầu tiên trả 200, cache=STALE, TTFB=479 ms, nhận xong HTML=480 ms, không Set-Cookie. Browser tiếp theo có FCP=860 ms. Proof: `artifacts/performance/after-six-minute-idle.json`.

Benchmark JVM cục bộ, cùng JAR/development DB, hai lượt thường và hai lượt TieredStopAtLevel=1: khởi động trung bình 12689 -> 10334 ms, giảm 18.6%. Median request khi đã chạy trung bình 252.0 -> 260.5 ms. Giữ cờ trong Dockerfile.vercel; không suy diễn thành mức cải thiện trên mọi máy chủ.

## Cache và API

- `/`, `/about`: stateless security chain, cùng HTML giữa các người dùng, không token/giỏ/tài khoản/Set-Cookie. Vercel-CDN-Cache-Control: public, s-maxage=60, stale-while-revalidate=600; browser revalidate.
- GET `/storefront/context`: JSON signedIn, isAdmin, cartCount; đọc session hiện có, không tạo session cho khách mới; private no-store.
- GET `/storefront/csrf`: JSON parameterName, headerName, token của đúng phiên; private no-store. Form thẻ sách/đăng xuất trên trang cache lấy token lúc bấm rồi POST bình thường. Không tự gửi lại POST.
- Trang riêng, danh mục/chi tiết động, API, redirects và errors giữ no-store. Static có hash dùng public max-age=31536000 immutable; URL không có hash phải revalidate. ResourceUrlEncodingFilter, VersionResourceResolver và CssLinkResourceTransformer đổi URL trong template/CSS.
- Header lấy trạng thái riêng sau khi HTML đã hiện. Back/BFCache khôi phục nút, đọc lại context và xóa token đã giữ từ trang cũ. Điều hướng chậm có thông báo; nút thử tải chỉ dành cho GET.
- Trang chủ đọc thể loại một lần và truy vấn JOIN với LIMIT 8; catalog JOIN thể loại thay cho N+1. Không thay luồng khóa tồn và ghi đơn.

## Kiểm thử và bàn giao

- **58/58 Java PASS**: 32 unit/ArchUnit + 26 integration trên Neon test. Sáu test mới kiểm tra public HTML, session, hai người dùng, CSRF thật, cache/version và số SELECT. Log: `artifacts/performance-tests.log`.
- Local và Vercel: 375/768/1440px, menu/tìm kiếm, đăng ký/đăng nhập, coupon, COD, admin và doanh thu; không lỗi JavaScript.
- Browser bổ sung: ngắt lấy CSRF không phát sinh POST; bấm thử lại mới gửi; Back khôi phục nút/giỏ; đăng xuất rồi Back dùng token mới; trạng thái điều hướng chậm.
- Stripe sandbox Visa 4242 lưu sẵn: Pay thành công, webhook thật được lưu, duplicate signed event bỏ qua và tồn không đổi khi replay. Proof: `artifacts/performance-production/stripe-proof.json`.
- JAR đối chiếu toàn bộ resources hiện tại; ZIP kiểm tra integrity và loại secrets.

## Giới hạn và vận hành

HTML công khai có thể cũ tối đa khoảng 11 phút; giá/tồn luôn kiểm tra lại lúc thêm/đặt hàng. Cache HIT/STALE giúp hiện trang trong lúc Java thức dậy. Cache MISS ở POP mới, sau thời gian dài hoặc trang động vẫn có cold start; không cam kết loại bỏ hoàn toàn trên Vercel Hobby. Không dùng cron/keepalive để làm sai phép đo hoặc giữ app thức.

Đo lại: `node scripts/measure-load.cjs https://shop-sach-java.vercel.app after`; sau nghỉ: `node scripts/measure-idle.cjs`, không truy cập app trong sáu phút; tạo báo cáo: `python scripts/report-performance.py`.

Deployment trước thay đổi vẫn giữ: `dpl_GdLa4zGhaqYU3H6hw3tfkWx9WveL` (`shop-sach-java-ixmfh28an-nghia138.vercel.app`). Có thể chọn lại trong Vercel để rollback; không cần migration hay khôi phục database. Rollback không được thực thi trong đợt kiểm chứng này.
