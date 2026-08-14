# TikTok Gesture — Vuốt TikTok bằng cử chỉ vẫy tay

App Android dùng camera trước để nhận diện cử chỉ **vẫy tay lên/xuống**,
sau đó tự động thực hiện thao tác vuốt màn hình trên app TikTok thật
(giống hệt như bạn dùng ngón tay vuốt).

## Cách hoạt động

1. **GestureForegroundService** chạy nền, giữ camera trước hoạt động liên tục
   (kể cả khi bạn đang mở TikTok).
2. **MotionAnalyzer** so sánh độ sáng giữa các khung hình liên tiếp để tìm
   hướng di chuyển của bàn tay (không dùng model AI nặng, chạy nhẹ).
3. Khi phát hiện vẫy tay **từ dưới lên** → gọi **GestureAccessibilityService**
   thực hiện vuốt màn hình lên (chuyển video tiếp theo).
   Vẫy tay **từ trên xuống** → vuốt xuống (video trước đó).
4. AccessibilityService là cách duy nhất Android cho phép một app "giả lập"
   thao tác chạm/vuốt lên app khác mà không cần root máy.

## Cách build

1. Cài **Android Studio** (bản mới nhất).
2. Mở thư mục `TikTokGesture/` này như một project có sẵn (Open existing project).
3. Đợi Gradle sync xong (lần đầu cần internet để tải các thư viện CameraX).
4. Cắm điện thoại Android (bật USB Debugging) hoặc dùng máy ảo, bấm **Run**.

Yêu cầu: Android 8.0 (API 26) trở lên, có camera trước.

## Cách dùng sau khi cài lên điện thoại

1. Mở app **TikTok Gesture**, bấm nút **"1. Cấp quyền Camera"**.
2. Bấm **"2. Mở cài đặt Accessibility"** → tìm mục **TikTok Gesture** trong
   danh sách dịch vụ trợ năng → bật lên. (Đây là bước Android bắt buộc phải
   làm thủ công, không thể tự động bật bằng code.)
3. Quay lại app, bấm **"3. Bật nhận diện cử chỉ"**.
4. Mở app **TikTok** lên như bình thường.
5. Đưa bàn tay vào trước camera trước, **vẫy tay từ dưới lên** để chuyển
   sang video tiếp theo, **vẫy từ trên xuống** để quay lại video trước.

## Tinh chỉnh độ nhạy

Nếu thấy gesture bị trigger nhầm hoặc khó trigger, chỉnh các thông số trong
`MotionAnalyzer.kt`:

- `motionPixelThreshold`: ngưỡng coi 1 điểm ảnh là "có chuyển động" (tăng nếu
  bị nhiễu do ánh sáng yếu).
- `minMotionRatio`: tỉ lệ tối thiểu vùng ảnh phải chuyển động mới tính là cử
  chỉ thật (tăng để tránh trigger nhầm khi chỉ có vật nhỏ di chuyển).
- `windowMs`: khoảng thời gian gom các khung hình lại để tính hướng vẫy tay.
- `cooldownMs`: thời gian chờ tối thiểu giữa 2 lần trigger liên tiếp.

## Giới hạn cần biết

- App chỉ **mô phỏng thao tác vuốt tay thật** trên màn hình đang hiển thị —
  nó không "biết" TikTok đang chạy, chỉ vuốt lên bất kỳ app nào đang mở.
  Nếu bạn không mở TikTok, thao tác vuốt vẫn xảy ra trên app khác đang hiển
  thị. Có thể giới hạn chỉ hoạt động khi TikTok đang foreground bằng cách
  kiểm tra `packageName` trong `onAccessibilityEvent` (đã để sẵn khung trong
  `GestureAccessibilityService`, chỉ cần bổ sung logic kiểm tra nếu muốn).
- Việc nhận diện chuyển động bằng cách so khung hình khá nhạy với ánh sáng —
  hoạt động tốt nhất ở nơi đủ sáng, ít vật thể di chuyển khác trong khung hình.
- Chạy camera nền liên tục sẽ tốn pin hơn bình thường.
