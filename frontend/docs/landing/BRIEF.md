# BRIEF: Trang giới thiệu DRWP

> Self-authored under explicit creative delegation. Người dùng giao: "sử dụng scroll-craft cho giao diện để đẹp mắt người dùng".
> Phạm vi đã chốt: scroll-craft chỉ áp dụng cho trang giới thiệu `/` và trang đăng nhập; ứng dụng giữ UI gọn, cùng nhận diện.

## 1. Vibe
Giấy tờ hành chính được làm lại cho tử tế. Bìa hồ sơ, giấy kẻ dòng, con dấu đỏ.
Tham chiếu: bìa sách Penguin cổ điển (một màu nhấn), sổ công văn đóng dấu, bản in typo Thuỵ Sĩ.

## 2. Hành trình (tác giả quyết định)
1. Hero nhiều lớp: bìa hồ sơ trên giấy kẻ dòng, giao diện thật của ứng dụng phía sau.
2. Biểu mẫu tự dựng lên từ khai báo khi cuộn.
3. Đường duyệt nằm ngang: hồ sơ đi qua từng bàn, rẽ nhánh khi trên 5 ngày, rồi được đóng dấu.
4. Nhiều đơn vị, cách ly dữ liệu, phân quyền chuẩn (nền tối).
5. Kết: mời đăng nhập, nền trở lại màu giấy.

## 3. Đường năng lượng
Yên (hero) → tăng nhẹ (biểu mẫu) → cao nhất (đường duyệt + con dấu) → trầm, chắc (bảo mật) → mở (kết).

## 4. Đường cảm xúc
| Act | Cảm xúc | Nguyên nhân trên màn hình |
|---|---|---|
| 1 Hero | Quen thuộc, tin cậy | Bìa hồ sơ giấy thật, con dấu, giao diện thật phía sau |
| 2 Biểu mẫu | "À, mình tự làm được" | Từng trường xuất hiện theo cuộn, bộ đếm 12 kiểu trường |
| 3 Đường duyệt | Hồi hộp rồi nhẹ nhõm | Hồ sơ đi qua các bàn, rẽ nhánh, dấu ĐÃ DUYỆT đập xuống |
| 4 Đơn vị | Yên tâm | Nền chuyển tối, các cam kết ngắn, số thật |
| 5 Kết | Sẵn sàng | Nền trở lại giấy, một nút đỏ |

**Đỉnh (peak):** Act 3. "Cuộn tới cuối thì con dấu đỏ ĐÃ DUYỆT đập xuống hồ sơ."
**Signature move:** hồ sơ đứng yên giữa sân khấu trong khi đường duyệt trượt qua bên dưới; ở bàn cuối, dấu đóng xuống (scale + opacity theo `--sc-p`).
**Câu kể lại:** "Đó là trang mà mình nhìn thấy hồ sơ của mình đi qua từng bàn rồi được đóng dấu."

## 5. Điều không trang nào khác làm
Con dấu được đóng bằng chính thao tác cuộn của người xem.

## 6. Khoảng cách với premium-minimal
Editorial ấm, không tối giản lạnh. Một màu nhấn đỏ son `#c8361f` chỉ dùng cho con dấu và nút chính.

## 7. Một thế giới hay nhiều cảnh?
Nhiều cảnh (acts), không worldflight.

## 8. Tài sản
Không có ảnh/video; không dùng kie.ai. Các lớp hero là giao diện thật của sản phẩm dựng bằng HTML/CSS (bìa hồ sơ, bảng hồ sơ, con dấu, kẹp giấy).

## Token
canvas `#f3efe6`, surface `#fbf8f1`, ink `#14161c`, ink-soft `#5b5a55`, accent `#c8361f`, accent-ink `#fff8f0`.
Font: Instrument Sans (display), Inter (text), JetBrains Mono (nhãn).

## Quy tắc cứng đã áp dụng
Không gạch dài trong chữ hiển thị; không "cuộn để khám phá" hay bộ đếm 01/06; tối đa một eyebrow (Act 4);
căn lề thay đổi (trái, phải, trái, chia đôi, giữa); không chữ gradient hay glow; chỉ số thật (12 quyền chuẩn,
12 kiểu trường, 3 kiểu bước); chỉ animate transform/opacity; reduced-motion: trạng thái tĩnh đã ổn định;
mobile có bố cục riêng cho hero và đường duyệt.

## Khoảng lặng có chủ đích
Sau dấu đóng (p > 0.95) đường duyệt giữ yên khoảng 0.1 màn hình trước khi nhả ghim.
