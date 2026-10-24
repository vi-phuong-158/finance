# Finance Bridge — Đánh giá và đề xuất triển khai

Ngày đánh giá: 24/10/2026. Phạm vi: mã nguồn Finance Bridge, README và SPEC_V3_1.md.

> Các mục 1–8 bên dưới là nhật ký đánh giá trước hardening PR #1. Hợp đồng hiện tại ở README/SPEC: eventId SHA-256 đầy đủ trên `bank:postedAt:normalizedText`, dedup toàn lịch sử hai tab, masking dùng chung cho Raw_Capture/description/counterparty, lockfile npm và Gradle Wrapper 8.7. Đã sửa lỗi compile Kotlin ở WebhookClient, TransactionOutbox và tham chiếu URI trong Gradle. Backend cục bộ: `npm ci` thành công, 33/33 test, typecheck exit 0. Build cục bộ Android dừng vì thiếu JDK/SDK; workflow CI chuẩn bị JDK 17/SDK 34 và chạy `assembleDebug testDebugUnitTest` từ checkout sạch. Kết quả CI đúng HEAD được cập nhật trên PR; không suy ra nghiệm thu điện thoại hoặc Google Sheets thật từ unit test.

## 1. Kết luận

Dự án phù hợp làm công cụ ghi thu chi cá nhân cho một người dùng: Android nhận thông báo ngân hàng, Vercel xử lý, Google Sheets lưu dữ liệu và phục vụ báo cáo. Nên giữ kiến trúc này cho MVP.

Mã nguồn hiện là bản triển khai ban đầu, chưa đủ điều kiện xác nhận “Production Ready”. Lỗi quan trọng nhất đã tái hiện: parser VCB không nhận giao dịch chi tiền ngay cả với mẫu kiểm thử có sẵn. Bản ghi bị chuyển sang Raw_Capture thay vì Transactions, dẫn đến báo cáo chi tiêu bị thiếu.

Thứ tự đề xuất: sửa tính đúng đắn và bảo vệ dữ liệu → hoàn thiện Android → nghiệm thu VCB trên thiết bị thật → bổ sung BIDV/VietinBank → báo cáo và đối soát.

Đợt nghiên cứu này không thay đổi mã nguồn ứng dụng, cài phụ thuộc, deploy hoặc truy cập dữ liệu ngân hàng thật. Chỉ bổ sung tài liệu đề xuất này.

## 2. Hiện trạng đối chiếu mã nguồn

| Thành phần | Đã có | Khoảng trống chính |
| --- | --- | --- |
| Android listener | Whitelist ba package, bỏ group summary/ongoing, chuẩn hóa, băm eventId | EXTRA_BIG_TEXT rỗng chưa fallback; chưa có kiểm thử listener |
| SensitiveFilter | Blacklist OTP/bảo mật, kiểm tra dấu hiệu giao dịch | Chưa có bộ ca kiểm thử; cần kiểm tra giao dịch hợp lệ có từ trùng blacklist |
| WorkManager | Unique work, điều kiện có mạng, retry exponential | Chưa theo dõi kết quả trên UI; chưa có phục hồi rõ ràng cho lỗi cấu hình/xác thực |
| API | POST, kiểm tra secret, blacklist dự phòng, ghi hai tab | Thiếu schema chặt, xác minh bank/package/eventId, positive signal, xử lý lỗi có cấu trúc |
| Parser VCB | Có regex, bóc tách tiền/thời gian/đối tác | Lỗi giao dịch CHI; nhận ngày không hợp lệ; có đường lưu nguyên số tài khoản |
| BIDV/VietinBank | Có hàm và đường đưa vào Raw_Capture | Hai parser đều trả null; chưa phải hỗ trợ tự động phân tích giao dịch |
| Danh mục | Có danh mục nội bộ và từ khóa thương hiệu | Không bỏ dấu; khớp chuỗi con gây phân loại sai; nội bộ dựa tên còn dễ nhầm |
| Kiểm thử/build | Hai mẫu VCB trong một file test; cấu hình Android/TypeScript | Test runner lỗi; thiếu lockfile, Gradle Wrapper; chưa có test API/Android/E2E |

Thư mục hiện không có `.git`; không thể xác nhận nhánh, commit hay lịch sử thay đổi. Nhận định bên trên áp dụng cho bộ file đang có, không chứng minh tình trạng của một deployment từ xa.

## 3. Phát hiện cần xử lý

### 3.1. Chặn nghiệm thu: giao dịch VCB chi tiền không được parse

Vị trí: `vercel/parsers/vcb.ts:26–27`.

Biểu thức `[+-,.\s]` diễn giải dấu gạch ngang thành khoảng ký tự giữa `+` và `,`, nên không loại được dấu `-`. Chuỗi tiền âm vẫn mang dấu âm khi qua parseInt, sau đó bị điều kiện `amount <= 0` loại bỏ.

Đã tái hiện: giao dịch CHI trả null, cùng cấu trúc với dấu cộng parse được; bộ test hiện có dừng ngay tại assertion đầu tiên. Cần sửa xử lý dấu và bổ sung kiểm thử cả THU/CHI trước các hạng mục mở rộng.

### 3.2. Bảo vệ dữ liệu phải áp dụng cho mọi đường ghi

- `vercel/parsers/vcb.ts:38`: khi không có Ref, description lấy nguyên thông báo. Thử bằng dữ liệu giả xác nhận description còn số tài khoản đầy đủ. Việc che dữ liệu hiện chỉ nằm ở nhánh Raw_Capture.
- `vercel/api/transaction.ts:71,96`: cả hai nhánh dùng USER_ENTERED. Nội dung bên ngoài bắt đầu bằng dấu `=` có thể trở thành công thức. Đề xuất dùng RAW với giá trị số truyền bằng number, đồng thời thiết kế định dạng ngày rõ ràng. Đây là rủi ro từ cách ghi hiện tại, chưa thực hiện thử ghi công thức lên Sheet thật. Google mô tả trực tiếp sự khác biệt này trong [hướng dẫn ghi dữ liệu](https://developers.google.com/workspace/sheets/api/guides/values).
- `android/app/build.gradle.kts:15–16`: có URL dự phòng và secret mặc định trong cấu hình build. Nên buộc cấu hình hợp lệ, chỉ cho HTTPS và dừng build khi thiếu secret. Nếu từng dùng secret mặc định thực tế thì cần thay mới. Không chép giá trị secret vào tài liệu hoặc log.
- Secret nhúng trong APK cá nhân cần được coi là thông tin có thể trích xuất; cần quy trình thay và thu hồi. Google private key chỉ nằm ở phía server, Sheet giữ quyền truy cập riêng tư.

### 3.3. Chống trùng hiện tại có giới hạn rõ ràng

Vị trí: `vercel/api/transaction.ts:49–59,68–85`.

- API tải toàn bộ hai cột rồi mới slice 1.000 dòng trong bộ nhớ; chưa phải chỉ đọc 1.000 dòng từ Sheets.
- Đọc eventId và append là hai thao tác tách rời. Suy luận từ mã nguồn: hai request đồng thời có thể cùng không thấy eventId rồi cùng ghi. Chưa chạy thử đồng thời với Sheets thật.
- KEEP chỉ bỏ qua công việc trùng đang chưa hoàn tất, không phải sổ chống phát lại vĩnh viễn. [Android xác định phạm vi KEEP như vậy](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy).
- Sau khi bản ghi ra ngoài cửa sổ 1.000 dòng, replay cũ có thể được ghi lại.
- eventId chỉ phụ thuộc nội dung: hai giao dịch thật có thông báo hoàn toàn giống nhau sẽ có cùng khóa; cần mẫu thật để kiểm tra giả định nội dung luôn đủ phân biệt.

Đề xuất MVP: giữ một thiết bị, dùng hàng đợi upload tuần tự, xác minh eventId phía server, có công cụ kiểm tra trùng toàn lịch sử và ghi rõ đây là chống trùng ở mức cố gắng tốt nhất. Tuần tự ở một thiết bị không loại trừ mọi request đồng thời trên server. Nếu yêu cầu bắt buộc “mỗi event chỉ ghi đúng một lần” trong mọi tình huống đồng thời, cần thay đổi ràng buộc kiến trúc để có cơ chế ghi nguyên tử/khóa bền vững; không dùng khóa trong RAM serverless để tuyên bố đã giải quyết.

### 3.4. API chưa kiểm soát chặt dữ liệu đầu vào

Vị trí: `vercel/api/transaction.ts:26–35`.

Hiện mới kiểm tra ba trường có giá trị. Đề xuất kiểm tra kiểu, độ dài và ngân hàng hợp lệ; kiểm tra packageName khớp bank; xác minh eventId bằng cùng quy tắc chuẩn hóa; kiểm tra postedAt và giới hạn payload. Chạy lại positive transaction signal phía server trước khi cho vào Raw_Capture. Khi không xác định được dữ liệu hợp lệ thì không ghi.

Chuẩn hóa phản hồi theo kết quả lưu/trùng/loại bỏ/lỗi đầu vào/lỗi hạ tầng. Chỉ báo đã lưu sau khi Sheets xác nhận; lỗi Sheets tạm thời cần phản hồi có thể retry, không trả thành công giả. Log chỉ chứa mã lỗi và eventId, không chứa secret hay toàn văn thông báo.

### 3.5. Android chưa phản ánh kết quả đồng bộ thật

- `BankNotificationListener.kt:47`: bigText có tồn tại nhưng rỗng sẽ che mất EXTRA_TEXT hợp lệ.
- `MainActivity.kt:34–36,63,88–93`: trạng thái quyền chỉ cập nhật khi bấm Gửi thử; trạng thái máy chủ chỉ là đã xếp hàng, chưa theo dõi phản hồi.
- `UploadWorker.kt:51–60`: mọi HTTP 2xx đều thành công mà chưa đọc status nghiệp vụ; lỗi 401/403 kết thúc tác vụ nhưng UI chưa cho biết và chưa có cách gửi lại sau sửa cấu hình.
- Mẫu Gửi thử cố định tạo cùng eventId và có thể xuất hiện trong sổ giao dịch thật. Nên cho chạy kiểm tra trên Sheet thử riêng, với giao diện phân biệt đã lưu và đã có bản ghi.
- Chưa giới hạn dung lượng WorkData. Android nêu Data là vùng chứa nhỏ có giới hạn kích thước serialize, vượt giới hạn gây lỗi; cần kiểm tra trước khi enqueue và báo lỗi có thể phục hồi, không cắt mất dữ liệu tiền/ngày. Xem [Data API](https://developer.android.com/reference/androidx/work/Data).

### 3.6. Chất lượng parser và phân loại

Đã kiểm tra bằng dữ liệu giả:

| Tình huống | Kết quả hiện tại | Mong muốn |
| --- | --- | --- |
| VCB CHI | null | Parse đúng số tiền dương và type CHI |
| Ngày 99-99-2026 | Vẫn được nhận | Loại khỏi giao dịch chuẩn, chuyển xử lý mẫu |
| Không có Ref | description còn số tài khoản | Không lưu số tài khoản đầy đủ |
| NHÀ THUỐC LONG CHÂU | Chưa phân loại | Y tế |
| LOTTERIA | Siêu thị do khớp LOTTE trước | Ăn uống |

Nên chuẩn hóa dấu và ranh giới từ, ưu tiên từ khóa cụ thể, kiểm tra ngày giờ/số tiền hữu hạn và giới hạn số nguyên an toàn. Nhận diện chuyển nội bộ phải cho phép đối soát thủ công; cùng tên chủ tài khoản chưa đủ chứng minh là chuyển giữa tài khoản của chính mình.

## 4. Các gói công việc đề xuất

| Gói | Nội dung triển khai | Đầu ra | Điều kiện nghiệm thu |
| --- | --- | --- | --- |
| A — Nền kiểm thử và parser | Sửa runner, xử lý dấu tiền, ngày giờ, thiếu Ref, chuẩn hóa và từ khóa | Test chạy tái lập, bộ mẫu giả đã che dữ liệu | Test hiện có và ca hồi quy đều chạy thành công; amount/type/ngày/category đúng |
| B — API và dữ liệu | Schema, xác minh eventId, lọc hai lớp, RAW, che dữ liệu mọi nhánh, xử lý lỗi, chống trùng có giới hạn công khai | API được kiểm thử với Sheets giả lập; kịch bản thử tích hợp | Sai secret không ghi; payload sai không ghi; OTP không ghi; thử lặp tuần tự chỉ một dòng; thử đồng thời có kết quả đo và kết luận rõ |
| C — Android tin cậy | Sửa fallback, theo dõi WorkInfo, cập nhật quyền khi quay lại app, trạng thái lỗi và gửi lại, cấu hình bắt buộc, hàng đợi tuần tự | APK cá nhân và màn hình trạng thái thực | Phân biệt đang chờ/đã lưu/trùng/lỗi; qua thử mất mạng, reboot và retry |
| D — Pilot VCB | Cấu hình môi trường thử, kiểm tra schema/quyền Sheet, cài APK, đối soát thiết bị thật | Biên bản nghiệm thu và hướng dẫn vận hành | Đối chiếu từng giao dịch thử với nguồn; không thiếu/trùng trong tập thử; không lưu OTP/số tài khoản đầy đủ |
| E — BIDV/VietinBank | Thu mẫu đã che dữ liệu, triển khai parser riêng, backfill có dry-run | Ba parser và công cụ xử lý mẫu cũ | Mẫu vào/ra, các biến thể thực tế đạt; replay/backfill không ghi trùng |
| F — Báo cáo | Tổng thu/chi tháng, danh mục, ngân hàng, chuyển nội bộ, chưa phân loại, đối soát | Pivot/QUERY/SUMIFS hoặc API tổng hợp có xác thực ở giai đoạn sau | Tổng khớp bộ dữ liệu chuẩn; chuyển nội bộ bị loại khỏi thu/chi tiêu dùng |

Đề nghị chọn A–D làm phạm vi bản dùng thử đầu tiên. E và F triển khai sau khi luồng ghi VCB ổn định. Chi phí “0 đồng/tháng” nên là mục tiêu trong hạn mức tài khoản thực tế, không phải cam kết kỹ thuật vô điều kiện.

## 5. Nội dung chi tiết để thi công

### Gói A

- Chọn một test runner rõ ràng, không dùng lệnh fallback có thể tự tải công cụ sau bất kỳ lỗi test nào.
- Thêm lệnh typecheck; chốt runtime/module/import nhất quán. Tạo lockfile khi việc cài phụ thuộc được cho phép.
- Bổ sung ca THU/CHI, dấu chấm/phẩy, NFC/NFD/NBSP, số dư ẩn, thiếu Ref, ngày sai và danh mục dễ khớp nhầm.
- Xác lập hợp đồng thời gian, số dư không có giá trị và thứ tự chín cột Transactions.

### Gói B

- Tách xử lý nghiệp vụ, parser và truy cập Sheets để kiểm thử không cần dữ liệu thật.
- Kiểm tra cấu hình server; bảo đảm thiếu cấu hình không mở cửa xác thực hay ghi dữ liệu sai nơi.
- Kiểm tra schema hai tab trước rollout; giữ chín cột Transactions và bốn cột Raw_Capture.
- Quy định rõ chống trùng theo cửa sổ, đối soát lịch sử và giới hạn đồng thời. Không tuyên bố đọc có giới hạn khi vẫn tải toàn cột.
- Lưu raw time từ thời điểm nhận thông báo nếu hợp lệ; tách rõ với thời điểm server xử lý để gửi bù không làm sai ngày nhận mẫu.
- Kiểm thử timeout sau khi append thành công nhưng chưa trả response: lần retry phải được xử lý như phát lại.

### Gói C–D

- Bổ sung Gradle Wrapper với phiên bản tương thích, kiểm tra cấu hình release và file ProGuard được tham chiếu; chưa tự nâng thư viện khi không cần thiết.
- Loại secret mặc định; thêm quy tắc bỏ qua local.properties, .env và khóa ký khi thiết lập quản lý mã nguồn.
- Quan sát WorkInfo và kết quả nghiệp vụ từ server; thông tin lỗi đủ để khôi phục mà không hiện toàn văn thông báo.
- Kiểm tra quyền khi app trở lại foreground; hiển thị lần đồng bộ thành công gần nhất và số tác vụ đang chờ/lỗi.
- Dùng Sheet thử riêng cho nút Gửi thử và nghiệm thu; kiểm thử trên điện thoại mục tiêu thay vì suy ra hành vi OEM từ mã nguồn.
- WorkManager có lưu công việc qua reboot, nhưng thời điểm chạy phụ thuộc điều kiện và tối ưu hệ thống. Không hứa đồng bộ tức thời hoặc khôi phục thông báo mà ứng dụng chưa từng nhận. Xem [cơ chế lưu công việc](https://developer.android.com/develop/background-work/background-tasks/persistent) và [constraints/retry](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

### Gói E–F

- Mỗi ngân hàng cần mẫu thật đã che thông tin, gồm nhận tiền, chuyển tiền và những biến thể thực tế như số dư ẩn; không suy đoán format rồi tuyên bố hỗ trợ.
- Backfill Raw_Capture cần chế độ xem trước và giữ nguồn. Kiểm tra chống trùng ở Transactions độc lập, vì API hiện bỏ qua eventId nếu đã thấy trong Raw_Capture; gửi lại qua endpoint hiện tại sẽ không tự chuyển thành giao dịch chuẩn.
- Báo cáo tổng hợp bằng phép tính xác định; AI diễn giải trên số đã tính. Tách số dư tại thời điểm giao dịch khỏi số dư hiện tại.
- Nếu thêm vùng báo cáo/tab thứ ba hay API đọc, cập nhật đặc tả hai tab và quy định quyền truy cập trước khi thi công phần đó.

## 6. Ma trận nghiệm thu tối thiểu

1. Test/typecheck backend: parser, normalization, filter, category, schema và HTTP response.
2. API với Sheets giả lập: lưu thành công, lưu raw, lọc nhạy cảm, trùng, sai secret, input sai, Sheets 429/5xx/timeout.
3. Tích hợp Sheet thử: đúng kiểu số, chín/bốn cột, chuỗi bắt đầu `=` vẫn là văn bản, không có số tài khoản đầy đủ trong mọi trường ghi.
4. Chống trùng: request lặp tuần tự; request đồng thời; timeout sau ghi; phát lại sau worker hoàn tất; phát lại ngoài cửa sổ 1.000 dòng. Ghi kết quả thực tế và giới hạn chấp nhận.
5. Android: unit test filter/normalization/hash; build APK; kiểm tra bigText rỗng và quyền khi quay lại app.
6. Thiết bị thật: mất mạng khi đã enqueue rồi kết nối lại; khởi động lại; 401 do cấu hình sai rồi khôi phục; thông báo ngoài whitelist; OTP; listener kết nối lại.
7. Pilot: đối soát số lượng, số tiền, chiều thu/chi, ngày giao dịch và danh mục với tập giao dịch nguồn trong khoảng thử thống nhất.

Chỉ chuyển sang giai đoạn tiếp theo khi các gate tương ứng đã chạy và có bằng chứng. Hai request tuần tự thành công không chứng minh chống trùng nguyên tử.

## 7. Kiểm chứng trong đợt nghiên cứu

- Đã đọc README, toàn bộ SPEC, các file TypeScript/Kotlin và cấu hình build; không có AGENTS.md/CLAUDE.md riêng trong dự án. Đã đọc CLAUDE.md và TOOLBOX.md ở cấp workspace.
- Node sẵn có: v24.11.0. Không có node_modules hay package-lock trong phần backend.
- Chạy trực tiếp nhánh Node trong script test: lỗi ERR_MODULE_NOT_FOUND do import không có phần mở rộng.
- Dùng resolver tạm trong bộ nhớ để ánh xạ import tương đối sang .ts, không sửa file và không cài công cụ: test hiện có chạy đến assertion đầu và thất bại vì parser trả null cho mẫu CHI.
- Chạy thêm các phép gọi trực tiếp trên parser/category với dữ liệu giả: xác nhận các kết quả tại mục 3.6. Đây là kiểm tra có mục tiêu, không thay thế toàn bộ kiểm thử dự án.
- Không chạy npm test đầy đủ vì fallback npx có thể tải công cụ; chưa có cho phép cài phụ thuộc. Chưa chạy typecheck/build backend.
- Chưa build APK: bộ file thiếu Gradle Wrapper; java/gradle/adb không được tìm thấy trên PATH trong phiên kiểm tra. Điều này không khẳng định máy hoàn toàn chưa cài Android Studio/JDK.
- Chưa chạy API với Google Sheets thật, kiểm tra đồng thời, Vercel deployment hoặc E2E điện thoại; chưa xác minh Sheet được README nêu đã có đúng schema và quyền chia sẻ.

Không xác nhận toàn dự án đạt nghiệm thu. Gói nên triển khai tiếp theo là A, sau đó B; chưa cần bổ sung nền tảng hoặc cơ sở dữ liệu mới để bắt đầu sửa các lỗi đã xác định.

## 8. Tiến độ sau khi bắt đầu triển khai

| Phase | Trạng thái | Bằng chứng / phần còn chờ |
| --- | --- | --- |
| P0 — Baseline | Hoàn tất cho phần mã cục bộ | Xác nhận thiếu Gradle Wrapper/JDK/SDK, không có `.git`, không có dependency backend cục bộ; không cài đặt hoặc deploy. |
| P1 — Parser/test runner | Hoàn tất mã và test | `npm test`: 17/17 đạt trên Node 24.11.0. |
| P2 — API/Sheets contract | Hoàn tất mã, xác minh bằng Sheets giả lập | Test 17/17 bao gồm auth, schema, filter, dedup tuần tự, RAW, dry-run và lỗi upstream. Chưa ghi lên Sheet thật. |
| P3 — Outbox Android | Có mã cốt lõi, chưa nghiệm thu build | SQLite, WorkManager payload theo eventId, retry, giao diện trạng thái và dry-run đã được viết. Còn build/lint/test thiết bị do thiếu JDK/Gradle Wrapper/Android SDK. |
| P4 — Pilot VCB / R1 | Chưa thực hiện | Cần toolchain Android, URL/secret cá nhân, Sheet thử đã kiểm tra quyền/schema, thiết bị Android và cửa sổ đối soát. Chưa deploy hoặc ghi dữ liệu thật. |
| P5 — BIDV/VietinBank | Đang chờ mẫu đã che | Chỉ mở parser sau khi có mẫu thật; chưa suy đoán nội dung ngân hàng. |
| P6 — Báo cáo R3 | Chưa thực hiện | Phụ thuộc dữ liệu đã nghiệm thu và quy tắc đối soát cuối cùng. |

`npm run typecheck` đã thử nhưng chưa chạy được vì `tsc` chưa có trong môi trường. Không cài dependency theo ràng buộc của workspace. Kết quả test hiện tại xác minh hành vi runtime Node bằng Sheet giả lập; chưa xác minh biên dịch TypeScript đầy đủ.

