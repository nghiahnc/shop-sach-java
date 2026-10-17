"""Render the performance report only after all measurements have completed."""
from pathlib import Path
import json
import statistics

root = Path(__file__).resolve().parents[1]
folder = root / 'artifacts/performance'
before = json.loads((folder / 'before-load.json').read_text(encoding='utf-8'))
after = json.loads((folder / 'after-load.json').read_text(encoding='utf-8'))
idle = json.loads((folder / 'after-six-minute-idle.json').read_text(encoding='utf-8'))
jvm = json.loads((folder / 'jvm-benchmark.json').read_text(encoding='utf-8'))
normal = [r for r in jvm['rows'] if not r['tieredStopAtLevel1']]
optimized = [r for r in jvm['rows'] if r['tieredStopAtLevel1']]
startup_before = statistics.mean(r['startupMs'] for r in normal)
startup_after = statistics.mean(r['startupMs'] for r in optimized)
summary = {
    'beforeFcpMedianMs': round(statistics.median(r['fcpMs'] for r in before['rows'])),
    'afterFcpMedianMs': round(statistics.median(r['fcpMs'] for r in after['rows'])),
    'beforeLoadMaxMs': max(r['loadMs'] for r in before['rows']),
    'afterLoadMaxMs': max(r['loadMs'] for r in after['rows']),
    'allAfterCdnHit': all(r['cache'] == 'HIT' for r in after['rows']),
    'idle': idle,
    'jvmStartupImprovementPercent': round((1-startup_after/startup_before)*100, 1),
    'javaTestsPassed': 58,
}
(folder / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
report = f'''# Trạm Sách - Kiểm chứng tải trang và cache

Thực thi 17/10/2026 trên https://shop-sach-java.vercel.app. Giữ Vercel, Java 21, Spring Boot và Neon; không đổi gói hoặc thêm dịch vụ trả phí.

## Số đo thực tế

Edge/Playwright desktop 1440px, bốn browser context mới mỗi đợt trên cùng máy/mạng. Trước sửa có ba lần chờ 19-25 giây; đợt sau đo CDN đã được tạo cache. Đây là mẫu đo nhỏ, không phải SLA hay kiểm thử tải.

| Chỉ số | Trước | Sau |
|---|---:|---:|
| Median first contentful paint | {summary['beforeFcpMedianMs']} ms | {summary['afterFcpMedianMs']} ms |
| Tải xong lâu nhất trong 4 lần | {summary['beforeLoadMaxMs']} ms | {summary['afterLoadMaxMs']} ms |
| CDN trang chủ | MISS cả 4 lần | HIT cả 4 lần |
| CSS/JS/font/ảnh | no-store | URL hash và cache immutable |

Sau **360 giây không gửi yêu cầu vào app**: HTTP đầu tiên trả {idle['firstAfterIdle']['status']}, cache={idle['firstAfterIdle']['cache']}, TTFB={idle['firstAfterIdle']['ttfbMs']} ms, nhận xong HTML={idle['firstAfterIdle']['htmlEndMs']} ms, không Set-Cookie. Browser tiếp theo có FCP={idle['browser']['fcpMs']} ms. Proof: `artifacts/performance/after-six-minute-idle.json`.

Benchmark JVM cục bộ, cùng JAR/development DB, hai lượt thường và hai lượt TieredStopAtLevel=1: khởi động trung bình {startup_before:.0f} -> {startup_after:.0f} ms, giảm {summary['jvmStartupImprovementPercent']}%. Median request khi đã chạy trung bình {statistics.mean(r['warmMedianMs'] for r in normal):.1f} -> {statistics.mean(r['warmMedianMs'] for r in optimized):.1f} ms. Giữ cờ trong Dockerfile.vercel; không suy diễn thành mức cải thiện trên mọi máy chủ.

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
'''
(root / 'docs/PERFORMANCE.md').write_text(report, encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k != 'idle'}, ensure_ascii=False))
