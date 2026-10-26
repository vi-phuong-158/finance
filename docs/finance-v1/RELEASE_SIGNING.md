# APK v1.0: signing và provenance

**Chưa cho phép build/phát hành release.** Chỉ làm sau khi release checklist hoàn tất, chủ dự án duyệt SHA và quyền môi trường, nghiệm thu Android thật.

Hiện `android/app/build.gradle.kts` đặt versionName `1.0`, versionCode `1`, chưa có signingConfig release. Không tự tạo hoặc commit keystore/password.

1. Chủ dự án giữ keystore ở thư mục riêng ngoài repository, không đồng bộ công khai; sao lưu khóa và password theo kênh riêng. Nếu đã có khóa đã dùng để phát hành, phải dùng đúng khóa đó để cập nhật ứng dụng.
2. Khi đủ gate, tạo/cấu hình signing bằng công cụ Android Studio Generate Signed Bundle/APK hoặc `keytool` trong JDK đã cài. Nhập password tương tác; không đặt password trong dòng lệnh, chat, Git hoặc log CI. Không thay khóa khi đang nâng bản cũ.
3. Cấu hình release signing từ file private/env ở runner riêng. CI công khai không nhận khóa trong workflow PR. `.jks`, `.keystore`, `.key`, `.env*` đã được ignore nhưng vẫn phải kiểm staged files trước commit.
4. Chốt versionCode lớn hơn mọi APK đã phân phối trước đó; chốt versionName. Build trên checkout sạch của **đúng SHA đã nghiệm thu**, không sửa source giữa acceptance và build.
5. Chạy toàn bộ test/policy/security/E2E đã quy định trước khi assembleRelease. Dùng `apksigner verify --print-certs` của SDK kiểm chữ ký trên APK cuối, đối chiếu fingerprint ngoài Git; không in khóa riêng.
6. Ghi vào biên bản phát hành: branch/SHA, versionName/versionCode, giờ build Việt Nam, CI run/jobs, APK SHA256, chứng cứ E2E và quyết định của chủ dự án. Không coi APK debug hoặc CI cũ là release acceptance.

Debug hiện có được build/test riêng, chứa URL backend và runtime Keystore; người thử phải dùng mã kết nối và môi trường phù hợp. Chưa cấu hình migration V2 thì ghi tay giữ trạng thái cần gửi lại, không báo đã ghi lên máy chủ.
