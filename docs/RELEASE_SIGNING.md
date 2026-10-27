# Ký APK release và cập nhật đè

Android chỉ cho cài bản mới đè lên bản cũ khi hai bản **cùng chữ ký** và bản mới có `versionCode` **lớn hơn**. Nếu phải gỡ ứng dụng, mọi dữ liệu trong máy mất theo (`allowBackup=false`): hàng đợi giao dịch chưa gửi, hàng đợi ghi tay/sửa, mã kết nối trong Android Keystore. Vì vậy APK phát hành luôn được ký bằng **một khóa release cố định**, giữ ngoài repo.

- Khóa ký chỉ đi vào build qua biến môi trường `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Thiếu biến nào thì build release dừng ở `validateReleaseSigning` (log chỉ in tên biến, không in giá trị).
- `versionCode` lấy từ `-PversionCode` (mặc định 1 cho build local). Workflow dùng `1000 + run_number`. `versionName` hiển thị dạng `1.0 (<versionCode>)` để biết máy đang cài bản nào.
- `*.jks`, `*.keystore` đã nằm trong `.gitignore`. Không bao giờ commit khóa hay mật khẩu.

## 1. Tạo khóa (chủ dự án làm một lần)

```bash
keytool -genkeypair -keystore finance-bridge-release.jks -storetype PKCS12 -alias finance-bridge -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Finance Bridge"
```

`keytool` hỏi mật khẩu khi chạy, không đặt mật khẩu trên dòng lệnh. Với PKCS12, mật khẩu khóa bằng mật khẩu kho.

**Sao lưu ngay** file `.jks` và mật khẩu vào ít nhất hai nơi ngoài máy (vd. trình quản lý mật khẩu + USB cất riêng). Mất khóa thì không bao giờ cập nhật đè được nữa; mọi máy phải gỡ và cài lại.

## 2. Đặt khóa lên GitHub

1. Repo → Settings → Environments → tạo `release-signing`. Bật **Required reviewers** (chính chủ dự án) để không job nào lấy được khóa khi chưa duyệt.
2. Thêm 4 secret vào **environment** đó (không phải repository secret):

| Secret | Giá trị |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | nội dung file `.jks` mã hóa base64, một dòng |
| `ANDROID_KEYSTORE_PASSWORD` | mật khẩu kho |
| `ANDROID_KEY_ALIAS` | `finance-bridge` (alias đã chọn) |
| `ANDROID_KEY_PASSWORD` | mật khẩu khóa (PKCS12: như mật khẩu kho) |

Dùng `gh` để giá trị đi thẳng từ stdin, không hiện trên màn hình hay lịch sử shell:

```bash
base64 -w0 finance-bridge-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --env release-signing --repo vi-phuong-158/finance
gh secret set ANDROID_KEYSTORE_PASSWORD --env release-signing --repo vi-phuong-158/finance
gh secret set ANDROID_KEY_ALIAS --env release-signing --repo vi-phuong-158/finance
gh secret set ANDROID_KEY_PASSWORD --env release-signing --repo vi-phuong-158/finance
```

Trên Windows PowerShell, thay dòng đầu bằng `[Convert]::ToBase64String([IO.File]::ReadAllBytes("finance-bridge-release.jks")) | gh secret set ANDROID_KEYSTORE_BASE64 --env release-signing --repo vi-phuong-158/finance`.

## 3. Build APK

Actions → **Build signed release APK** → Run workflow trên `main` (hoặc nhánh `codex/*`), nhập đúng SHA đầy đủ của commit đã review. Job chờ duyệt environment rồi:

- build `assembleRelease` kèm unit test và kiểm tra cấu hình webhook;
- chặn APK ký bằng khóa debug, in **SHA-256 chứng chỉ** và `versionCode` vào Summary;
- xóa keystore khỏi runner, quét APK không chứa `WEBHOOK_SECRET`;
- upload artifact `finance-bridge-release-apk-<versionCode>` (giữ 7 ngày).

Ghi lại SHA-256 chứng chỉ của lần build đầu tiên. Mọi lần sau phải **giống hệt**; khác là sai khóa, đừng cài.

Build local (nếu cần): đặt 4 biến môi trường ở trên rồi chạy `./gradlew assembleRelease -PversionCode=<số lớn hơn bản đang cài>`.

## 4. Chuyển một lần từ bản debug đang cài

Bản đang có trên điện thoại ký bằng khóa debug của runner, nên **lần đầu** sang khóa release bắt buộc phải gỡ:

1. Mở app, đồng bộ đến khi mọi hàng đợi về 0: dòng "… đang chờ đồng bộ · … cần xử lý" ở màn Ghi chép và hàng đợi thông báo ngân hàng đều 0.
2. Gỡ app, cài APK release, nhập lại mã kết nối, cấp lại quyền đọc thông báo và pin không hạn chế.
3. Từ đây các bản sau cài đè trực tiếp, không gỡ nữa.

## 5. Thử cập nhật đè (nghiệm thu trước phát hành)

1. Cài bản release N.
2. Bật chế độ máy bay. Tạo 1 giao dịch ghi tay (và 1 thông báo ngân hàng nếu có thể). Xác nhận bộ đếm "đang chờ" > 0.
3. Cài bản N+1 đè lên (`adb install -r app-release.apk` hoặc mở file APK). Không gỡ.
4. Mở app: bộ đếm "đang chờ" vẫn như trước, mã kết nối vẫn còn (không phải nhập lại).
5. Tắt chế độ máy bay: các mục chờ đồng bộ thành công, xuất hiện trong sổ.

Cài đè bị từ chối ("ứng dụng không được cài đặt", xung đột gói) nghĩa là khác chữ ký hoặc `versionCode` không tăng: dừng lại, **không gỡ** khi hàng đợi còn dữ liệu.
