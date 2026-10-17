# Kịch bản phỏng vấn — khoảng 5 phút

Chuẩn bị trang chủ, trang đăng ký và sơ đồ kiến trúc. Bản online bật DEMO_MODE; dữ liệu giả lập có thể sửa, chỉ gửi khi bấm xác nhận. Admin dùng tài khoản riêng trong `.local/DEMO-ACCOUNTS.md`.

| Thời gian | Trình diễn | Điểm giải thích |
|---|---|---|
| 0:00–0:40 | Mở trang chủ, menu, tìm `Effective Java`, chuyển sang mobile | Header giữa, ảnh thực tế và lưới tối giản; ảnh cục bộ, đúng ISBN |
| 0:40–1:15 | Đăng ký với dữ liệu có sẵn; bấm Đăng nhập | Email demo duy nhất; BCrypt; tài khoản CUSTOMER |
| 1:15–1:50 | Thêm sách số lượng 1, bấm Áp dụng DOCSACH10, tới checkout | Giỏ đóng gói; giá/tổng tính ở máy chủ; phí ship sau giảm |
| 1:50–2:30 | Giữ địa chỉ và Stripe đã chọn; xác nhận đặt hàng; Stripe hiển thị Visa 4242, bấm Pay | Sandbox; không lưu PAN/CVC; Customer/PaymentMethod tái sử dụng qua port |
| 2:30–3:00 | Xem biên nhận đã thu và lịch sử | Webhook chữ ký, event ID và transaction; redirect không tự chứng minh paid |
| 3:00–3:40 | Mở admin; đơn COD khác → giao → hoàn tất | Quyền ADMIN; state machine; doanh thu chỉ tiền đã thu |
| 3:40–4:30 | Mở kiến trúc, UML, DiscountPolicy/Order/PaymentGateway | Modular Monolith + Layered; kế thừa, đa hình, composition và invariant |
| 4:30–5:00 | Mở báo cáo test và test tranh chấp cuốn cuối | Unit, ArchUnit, Neon test riêng; giới hạn demo |

## Nhánh lỗi tùy chọn

Trên Stripe chọn dùng thẻ khác, điền `4000 0000 0000 0002`, hạn tương lai và CVC mẫu. Bấm Pay để nhận từ chối; sau đó chọn lại Visa 4242 đã lưu hoặc dùng `4242 4242 4242 4242`, thử lại trên cùng phiên. Đơn chưa thu tiền vẫn giữ tồn, không tạo đơn mới. Phiên hết hạn qua Stripe/webhook sẽ hoàn tồn đúng một lần.

Nếu mạng Stripe lỗi, mở lại chi tiết đơn và kiểm tra/tiếp tục thanh toán; không tạo đơn khác để né lỗi. Có thể trình diễn COD và mở ảnh bằng chứng sandbox đã lưu, nói rõ đâu là ảnh và đâu là giao dịch đang chạy.

## Câu hỏi kiến trúc

- Modular monolith giữ transaction đơn/tồn kho trong một database; ranh giới module được ArchUnit kiểm tra.
- OOP thể hiện ở invariant Cart/Order, DiscountPolicy Template Method và gateway Strategy; không chỉ số lượng class.
- Nhấn lặp: khóa người đặt + unique checkout key; Stripe idempotency theo order UUID; webhook claim event trong transaction.
- Container tắt: Neon lưu session/giỏ và trạng thái; đối soát Stripe trước hoàn tồn, không dựa timer RAM.
- Đây là portfolio cá nhân đã triển khai và kiểm chứng, không mô tả là kinh nghiệm vận hành thương mại.
