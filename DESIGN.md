# DESIGN.md — Finance App UI Design System

> Trạng thái: **UI source of truth** cho toàn bộ Android app.
>
> Mục tiêu: xây dựng giao diện quản lý thu chi cá nhân hiện đại, dễ dùng, lấy **bố cục và ngôn ngữ tương tác** từ các màn hình MISA MoneyKeeper do chủ dự án cung cấp làm tham chiếu, nhưng **không sao chép thương hiệu, logo, asset hoặc giao diện 1:1**.
>
> Mọi Agent sửa UI phải đọc file này trước khi code.

---

## 1. Design intent

Ứng dụng phải tạo cảm giác:

- Rõ ràng như một ứng dụng tài chính thật, không giống màn debug/kỹ thuật.
- Nhìn nhanh 3–5 giây là biết: **đang có bao nhiêu tiền, tháng này thu bao nhiêu, chi bao nhiêu, giao dịch gần đây là gì**.
- Các thao tác phổ biến nằm trong 1–2 chạm.
- Ưu tiên số tiền, danh mục, trạng thái giao dịch; giảm chữ kỹ thuật.
- Bo tròn, sáng, thoáng, hiện đại; màu xanh là màu nhận diện chính.
- Icon trực quan, đồng bộ, không dùng emoji trong UI production.
- Không phô bày webhook, eventId, worker, SQLite, secret hoặc mã lỗi kỹ thuật ở màn hình người dùng thông thường.

### 1.1. Điều học từ MISA

Các ảnh tham chiếu cho thấy các pattern nên giữ:

1. **Header tối giản**, tiêu đề lớn, không quá nhiều action.
2. **Hero financial card** nằm đầu màn hình, hiển thị số tiền nổi bật nhất.
3. **Card trắng trên nền xám/xanh rất nhạt**, khoảng cách rõ, bo góc lớn.
4. **Danh mục trực quan bằng icon màu**, label ngắn phía dưới.
5. **Nhóm chức năng dạng grid/card**, dễ quét bằng mắt.
6. Màn **Ghi chép** tập trung vào số tiền trước, sau đó mới tới danh mục/ngày/diễn giải/đối tượng.
7. Thanh điều hướng dưới gồm **5 mục cố định**, nút Ghi chép ở giữa nổi bật.
8. Màn **Báo cáo** dùng số liệu tổng quan trước, biểu đồ/phân tích sau.
9. Màu thu và chi phải phân biệt ngay lập tức.

### 1.2. Điều KHÔNG sao chép

- Không dùng logo MISA.
- Không dùng asset/icon độc quyền của MISA.
- Không bê nguyên tỷ lệ/kích thước/màu từng pixel.
- Không tạo giao diện khiến người dùng hiểu nhầm đây là sản phẩm MISA.
- Dùng Material Symbols / Material Icons hoặc vector asset tự tạo, cùng phong cách line/rounded.

---

## 2. Information architecture

Bottom navigation cố định gồm đúng 5 mục:

| Tab | Icon gợi ý | Vai trò |
|---|---|---|
| **Tổng quan** | `HomeRounded` | Dashboard tài chính |
| **Tài khoản** | `AccountBalanceWalletRounded` | Ngân hàng, tiền mặt, tài sản/cho vay |
| **Ghi chép** | `AddRounded` | Tạo giao dịch thủ công / điều chỉnh |
| **Báo cáo** | `PieChartRounded` | Thu chi, danh mục, xu hướng |
| **Khác** | `GridViewRounded` | Cấu hình, trạng thái đồng bộ, tiện ích |

### 2.1. Bottom navigation

- Luôn nằm dưới cùng ở các màn top-level.
- Container dạng pill lớn, bo góc 28–32dp.
- Margin ngang 16dp, margin đáy theo system inset.
- Chiều cao mục tiêu 72–80dp.
- Tab đang chọn: icon + label màu trắng/primary-contrast.
- Tab chưa chọn: xanh nhạt hoặc trắng 70–80% tùy nền.
- **Ghi chép** ở giữa dùng nút tròn trắng 52–58dp, icon `+` màu primary.
- Không để quảng cáo/banner dưới bottom bar.

---

## 3. Visual system

### 3.1. Color tokens

Dùng token, không hard-code màu rải rác.

```text
Primary            #08A9D6
PrimaryStrong      #078CC5
PrimaryLight       #DDF6FD
HeroStart          #177CF2
HeroEnd            #18C8D9

Background         #F4F6F8
Surface            #FFFFFF
SurfaceAlt         #F8FAFC
BorderSoft         #E7EBEF

TextPrimary        #151A23
TextSecondary      #687386
TextMuted          #98A1AE

Income             #28A765
IncomeSoft         #E4F6EC
Expense            #EF5B63
ExpenseSoft        #FDE9EB
Warning            #F59E0B
Info               #159BD7
Error               #D64545
```

Quy tắc:

- Không dùng gradient cho mọi thứ. Gradient chỉ dùng cho **hero financial card** hoặc điểm nhấn thật sự quan trọng.
- Không dùng quá 1 màu accent mạnh trong cùng một card.
- Thu: xanh lá. Chi: đỏ/coral. Chuyển khoản: xanh dương. Điều chỉnh: cam.

### 3.2. Typography

Ưu tiên font Android dễ triển khai và hiển thị tiếng Việt tốt:

1. **Be Vietnam Pro** nếu bundle font trong app.
2. Nếu chưa bundle: dùng system sans-serif/Material typography trước, không chặn tiến độ UI.

Scale khuyến nghị:

| Token | Size | Weight | Dùng cho |
|---|---:|---:|---|
| DisplayMoney | 32–36sp | 700 | Số tiền hero |
| H1 | 26–28sp | 700 | Tiêu đề màn |
| H2 | 20–22sp | 700 | Tiêu đề section |
| Title | 17–18sp | 600 | Card title |
| Body | 15–16sp | 400/500 | Nội dung chính |
| Caption | 12–13sp | 400/500 | Metadata |

- Số tiền phải dùng `FontWeight.Bold` hoặc `SemiBold`.
- Không viết HOA toàn bộ label dài.
- Dùng định dạng Việt Nam: `2.500.000 đ`, không dùng `2,500,000 VND` trong UI thường.

### 3.3. Shape

```text
Hero card radius      22–24dp
Main card radius      18–20dp
Small card radius     14–16dp
Bottom nav radius     28–32dp
Icon tile radius      12–14dp
Button radius         14–18dp
```

Không dùng góc vuông cho card chính.

### 3.4. Elevation & border

- Nền app: `Background`.
- Card trắng: shadow rất nhẹ hoặc border 1dp `BorderSoft`; không dùng shadow đậm.
- Elevation mặc định 0–2dp.
- Bottom navigation có elevation cao hơn nội dung.

### 3.5. Spacing

Dùng hệ 4dp:

```text
xs  = 4dp
sm  = 8dp
md  = 12dp
lg  = 16dp
xl  = 20dp
2xl = 24dp
3xl = 32dp
```

- Padding ngang màn: 16–20dp.
- Khoảng giữa card lớn: 12–16dp.
- Không nhồi nhiều section vào một card.

---

## 4. Icon system

### 4.1. Nguyên tắc

- Dùng **Material Symbols Rounded / Material Icons Rounded** làm mặc định.
- Stroke/visual weight thống nhất.
- Icon chức năng chính: 24dp.
- Icon danh mục: 28–32dp đặt trong tile 48–56dp.
- Icon trong bottom nav: 24–28dp.
- Không trộn icon filled, outline, emoji và clipart không cùng phong cách trong một màn.

### 4.2. Mapping gợi ý

| Chức năng | Icon |
|---|---|
| Ăn uống | `RestaurantRounded` |
| Xăng xe | `LocalGasStationRounded` |
| Con cái | `ChildCareRounded` |
| Trang phục | `CheckroomRounded` |
| Mua sắm | `ShoppingBagRounded` |
| Du lịch | `TravelExploreRounded` |
| Dịch vụ | `HomeRepairServiceRounded` |
| Nhà ở | `HomeRounded` |
| Sức khỏe | `MedicalServicesRounded` |
| Học tập | `SchoolRounded` |
| Giải trí | `MovieRounded` |
| Lương/Thu nhập | `PaymentsRounded` |
| Cho vay | `HandshakeRounded` |
| Đi vay | `RequestQuoteRounded` |
| Chuyển khoản | `SyncAltRounded` |
| Điều chỉnh số dư | `EditNoteRounded` |
| Báo cáo chi | `TrendingDownRounded` |
| Báo cáo thu | `TrendingUpRounded` |
| Đối tượng | `PersonRounded` |
| Chuyến đi/Sự kiện | `EventRounded` |
| Phân tích tài chính | `AnalyticsRounded` |
| Đồng bộ | `CloudSyncRounded` |
| Cài đặt | `SettingsRounded` |
| Quyền thông báo | `NotificationsActiveRounded` |
| Pin | `BatterySaverRounded` |

Danh mục có thể dùng nền pastel khác nhau nhưng icon vẫn phải cùng bộ.

---

## 5. Screen specification

## 5.1. Tổng quan

Mục tiêu: đây phải là màn người dùng muốn mở mỗi ngày.

### Bố cục từ trên xuống

1. **Top bar**
   - Trái: lời chào ngắn hoặc tên app.
   - Phải: icon đồng bộ/trạng thái nếu cần.
   - Không hiển thị thông tin kỹ thuật.

2. **Hero tài chính hiện tại**
   - Gradient `HeroStart -> HeroEnd`.
   - Label: `Tài chính hiện tại`.
   - Số tiền lớn nhất màn.
   - Hàng dưới chia 2 vùng:
     - `Tổng có`
     - `Tổng nợ`
   - Mỗi vùng có action chevron nhẹ.

3. **Tình hình tháng này**
   - 3 chỉ số: `Tổng thu`, `Tổng chi`, `Chênh lệch`.
   - Thu màu xanh lá, chi màu đỏ, chênh lệch dùng TextPrimary.
   - Có mini chart nếu dữ liệu hỗ trợ.

4. **Giao dịch gần đây**
   - Hiển thị tối đa 5 giao dịch.
   - Mỗi row:
     - icon danh mục
     - tên danh mục / merchant
     - tài khoản/ngân hàng + thời gian
     - số tiền ở phải
   - Chi: `-350.000 đ` màu Expense.
   - Thu: `+5.000.000 đ` màu Income.
   - Không hiển thị `eventId` cho người dùng.

5. **Trạng thái đồng bộ nhỏ**
   - Chỉ hiện nếu có vấn đề hoặc có giao dịch đang chờ.
   - Ví dụ: `2 giao dịch đang chờ đồng bộ`.
   - Nhấn vào mở `Khác > Kết nối & hệ thống`.

### Empty state

Nếu backend đọc dữ liệu chưa được triển khai:

- Không bịa số dư.
- Hero có thể hiển thị `—` và CTA `Chưa có dữ liệu tổng hợp`.
- Giao dịch local đã nhận vẫn có thể hiển thị trạng thái nếu an toàn.

---

## 5.2. Tài khoản

Mục tiêu: quản lý nơi tiền đang nằm và các khoản cho vay/nợ.

### Section 1 — Tổng tài sản

- Card tổng số dư.
- Hai chip/metric: `Tiền có` và `Nợ phải trả`.

### Section 2 — Tài khoản ngân hàng

Card từng ngân hàng:

- Logo/initial tự tạo, không dùng asset thương hiệu nếu không có quyền.
- `Vietcombank`, `VietinBank`, `BIDV`.
- Số dư nếu dữ liệu có.
- Dòng phụ: `Đồng bộ tự động` / `Chưa có dữ liệu`.

### Section 3 — Tài sản/Cho vay

Có entry rõ: **Theo dõi tiền lãi**.

Màn `Theo dõi tiền lãi` lấy cảm hứng trực tiếp từ ảnh tham chiếu riêng của chủ dự án:

1. Hero xanh đậm/gradient:
   - `Tổng gốc còn lại`
   - số tiền rất lớn
   - `Lãi hôm nay`
   - `Lãi còn thu`
2. Hai metric card trắng:
   - `Lãi lũy kế`
   - `Lãi đã thu`
3. Section `Cần theo dõi`
   - số khoản đang mở ở phải
4. Mỗi khoản vay là card trắng:
   - tên người vay
   - mã khoản
   - số gốc bên phải
   - lãi còn lại
   - `x%/năm · đáo hạn dd/MM/yyyy`
   - status chip `Đang vay`
5. Ưu tiên scan nhanh; không đưa biểu mẫu dài vào màn danh sách.

---

## 5.3. Ghi chép

Đây là màn có cấu trúc gần MISA nhất.

### Header

- Background xanh cực nhạt `PrimaryLight`.
- Trái: lịch sử/undo nếu cần.
- Giữa: segmented/dropdown loại giao dịch.
- Phải: dấu check xác nhận.

### Loại giao dịch

Dropdown/modal gồm:

- `Chi tiền` — icon minus, màu Expense.
- `Thu tiền` — icon plus, màu Income.
- `Cho vay` — icon handshake.
- `Đi vay` — icon request quote.
- `Chuyển khoản` — icon sync-alt.
- `Điều chỉnh số dư` — icon edit.

### Form order

1. **Số tiền** — field/card lớn nhất.
2. **Danh mục**.
3. **Mục hay dùng** — grid tối đa 8 item.
4. **Tài khoản**.
5. **Ngày giờ**.
6. **Diễn giải**.
7. **Chuyến đi/Sự kiện** — optional.
8. **Đối tượng** — optional.
9. Save/check action.

### Amount field

- Hiển thị số lớn 30–34sp.
- Màu theo loại giao dịch.
- Không yêu cầu nhập `đ` thủ công.
- Bàn phím số.

### Category quick grid

- 4 cột.
- Icon tile + label.
- Label tối đa 2 dòng.
- Mục yêu thích có dấu sao nhỏ hoặc pin indicator, không làm rối icon chính.

### Manual-entry scope guard

Backend V3.1 hiện tập trung vào giao dịch từ notification. Nếu API ghi tay chưa có:

- Có thể dựng UI trước.
- Không được giả vờ `Lưu thành công` nếu chưa có persistence thật.
- CTA phải disabled hoặc ghi rõ trạng thái dev cho đến khi luồng lưu được triển khai.

---

## 5.4. Báo cáo

### Hero

- Card `Tài chính hiện tại` tương tự Tổng quan nhưng có thể nhỏ hơn.

### Section `Tình hình thu chi`

- Label `5 tháng gần nhất`.
- Mini bar chart 5 tháng.
- Có `Xem chi tiết`.

### Month calendar summary

- Header tháng + trái/phải.
- Hàng metric:
  - `Tổng thu`
  - `Tổng chi`
  - `Chênh lệch`
- Calendar chỉ dùng nếu có nghiệp vụ theo ngày thực sự; nếu chưa cần, thay bằng chart/list để tránh code giả.

### Grid báo cáo

2 cột, card trắng, icon primary:

- Phân tích chi tiêu
- Phân tích thu
- Theo dõi vay nợ
- Đối tượng thu/chi
- Chuyến đi/Sự kiện
- Phân tích tài chính

Mỗi card:

- chiều cao 112–128dp
- icon ở trên trái
- title ở dưới
- không có mô tả dài

---

## 5.5. Khác

Đây là nơi chuyển toàn bộ UI kỹ thuật hiện tại vào, nhưng phải viết lại cho người dùng phổ thông.

### Menu chính

1. `Kết nối & hệ thống`
2. `Trạng thái đồng bộ`
3. `Danh mục`
4. `Cài đặt hiển thị`
5. `Thông tin ứng dụng`

### Kết nối & hệ thống

Chuyển các chức năng hiện có từ `MainActivity.kt` vào đây:

- Cấu hình mã kết nối.
- Kiểm tra kết nối.
- Quyền đọc thông báo.
- Tối ưu pin.
- Số giao dịch đang chờ.
- Số giao dịch lỗi/cần gửi lại.
- Retry giao dịch lỗi.
- Phiên bản app.

#### Cách viết lại text

Không dùng:

- `Webhook secret`
- `WorkManager`
- `SQLite outbox`
- `BLOCKED`
- event ID dài

Dùng:

- `Mã kết nối`
- `Kết nối máy chủ`
- `Giao dịch đang chờ`
- `Cần gửi lại`
- `Đã đồng bộ`
- `Chưa thể đồng bộ`

Mã lỗi kỹ thuật chỉ hiện trong phần `Chi tiết kỹ thuật` có thể mở rộng khi cần support.

---

## 6. Components bắt buộc tái sử dụng

Agent không được mỗi màn tự vẽ một kiểu. Tạo component dùng chung.

```text
FinanceTopBar
FinanceBottomBar
FinanceHeroCard
MetricCard
SectionHeader
TransactionRow
AccountCard
CategoryIconTile
QuickCategoryGrid
ReportTile
LoanCard
StatusChip
EmptyStateCard
SyncStatusBanner
MoneyText
```

### 6.1. `MoneyText`

Một formatter duy nhất cho toàn app.

Ví dụ:

```text
0 đ
350.000 đ
2.500.000 đ
1.190.000.000 đ
```

Với giao dịch:

```text
+10.000.000 đ
-350.000 đ
```

Không để mỗi màn tự format.

### 6.2. `StatusChip`

Các trạng thái:

- Success: xanh lá nhạt.
- Pending: xanh/amber nhạt.
- Error: đỏ nhạt.
- Neutral: xám.

Chip nhỏ, không dùng button-style nặng.

---

## 7. Compose implementation rules

App hiện dùng **Jetpack Compose + Material 3**; tiếp tục stack này.

### 7.1. Structure đề xuất

```text
android/app/src/main/java/vn/phuong/financebridge/ui/
├── theme/
│   ├── Color.kt
│   ├── Theme.kt
│   ├── Type.kt
│   └── Dimens.kt
├── components/
│   ├── FinanceBottomBar.kt
│   ├── FinanceHeroCard.kt
│   ├── TransactionRow.kt
│   ├── CategoryIconTile.kt
│   └── ...
├── navigation/
│   └── AppNavigation.kt
└── screens/
    ├── overview/
    ├── accounts/
    ├── entry/
    ├── reports/
    └── more/
```

Không tiếp tục nhồi toàn bộ UI vào một `MainActivity.kt`.

### 7.2. MainActivity target

`MainActivity` chỉ nên:

- `setContent`
- apply theme
- mở root navigation

Logic cấu hình/WorkManager hiện hữu phải được tách ra theo hướng an toàn, không viết lại nghiệp vụ chỉ để đổi UI.

### 7.3. State

- UI state tách khỏi service/worker.
- Không gọi thao tác nặng trực tiếp trong composable.
- Dùng ViewModel khi màn bắt đầu có nhiều state.
- UI phải chịu được null/empty/loading/error.

### 7.4. Insets

- Tôn trọng status bar/navigation bar.
- Không hard-code khoảng trống cho từng máy.
- Bottom navigation không đè gesture bar.

---

## 8. Interaction principles

- Tap target tối thiểu ~48dp.
- Một màn chỉ có một primary action thật sự nổi bật.
- Không popup liên tục.
- Dropdown loại giao dịch dùng modal/menu rõ ràng.
- Có haptic nhẹ cho save/confirm nếu phù hợp.
- Với thao tác retry hoặc cấu hình, hiển thị trạng thái rõ: loading → success/error.
- Không làm animation dài; 150–250ms là đủ cho chuyển trạng thái nhỏ.

---

## 9. Accessibility

- Contrast đủ đọc dưới ánh sáng mạnh.
- Không truyền đạt trạng thái chỉ bằng màu.
- Icon quan trọng phải có content description.
- Text có thể scale mà không vỡ card nghiêm trọng.
- Không dùng font dưới 12sp cho nội dung cần đọc.

---

## 10. Data truth & security guardrails

Đây là app tài chính nên UI không được làm người dùng hiểu sai dữ liệu.

1. Không bịa số dư/tổng tài sản nếu backend chưa cung cấp.
2. Không hiển thị OTP hoặc notification nhạy cảm.
3. Không log secret trong UI/debug panel production.
4. Không vì làm dashboard mà thay đổi `SensitiveFilter`, event identity, SQLite outbox, retry semantics hoặc webhook contract nếu task chỉ là UI.
5. Không xóa màn trạng thái hệ thống trước khi chức năng tương đương đã được chuyển sang `Khác`.
6. Giao dịch pending phải phân biệt với giao dịch đã ghi thành công.
7. Empty state phải nói đúng `Chưa có dữ liệu`, không hiển thị `0 đ` nếu `0` không phải dữ liệu thật.

---

## 11. Responsive target

Ưu tiên điện thoại Android portrait:

- Width tham chiếu: 360–430dp.
- Thiết kế đẹp nhất ở khoảng 390–412dp.
- Không tối ưu tablet ở phase đầu.
- Grid 4 cột danh mục phải co giãn hợp lý ở 360dp.
- Grid report 2 cột.

---

## 12. Screen acceptance checklist

Một màn chỉ được coi là đạt UI khi:

- [ ] Dùng đúng token màu/theme.
- [ ] Không có màu hard-code tùy tiện ngoài category palette.
- [ ] Typography đúng hierarchy.
- [ ] Card radius/spacing nhất quán.
- [ ] Icon cùng phong cách.
- [ ] Không có emoji thay icon production.
- [ ] Không lộ thuật ngữ kỹ thuật cho người dùng phổ thông.
- [ ] Có loading/empty/error state.
- [ ] Không bịa dữ liệu tài chính.
- [ ] Tôn trọng system insets.
- [ ] Bottom nav đúng 5 mục ở top-level.
- [ ] Screenshot 360dp và ~412dp không tràn/cắt chữ.
- [ ] Luồng bắt notification và upload hiện hữu không regression.

---

## 13. Implementation order

Triển khai theo thứ tự sau để giảm rủi ro:

### Phase UI-1 — Foundation

- Theme tokens.
- Typography.
- Shape/dimens.
- Icon mapping.
- Root navigation.
- Bottom navigation.
- Reusable components.

### Phase UI-2 — Move current technical screen

- Chuyển `StatusScreen` hiện tại thành `Khác > Kết nối & hệ thống`.
- Giữ nguyên hành vi cấu hình, permission, battery, queue, retry.
- Chỉ đổi presentation/text.

### Phase UI-3 — Tổng quan

- Hero card.
- Thu/chi/chênh lệch.
- Recent transactions.
- Sync banner.

### Phase UI-4 — Ghi chép

- Transaction type selector.
- Amount input.
- Category quick grid.
- Form fields.
- Chưa nối persistence thì giữ đúng trạng thái chưa khả dụng.

### Phase UI-5 — Báo cáo

- Monthly summary.
- Mini chart.
- Report tiles.

### Phase UI-6 — Tài khoản & Theo dõi tiền lãi

- Account cards.
- Loan/interest dashboard theo mẫu chủ dự án cung cấp.

---

## 14. Final visual direction

Tóm tắt bằng một câu:

> **MISA-inspired information architecture + Android Material 3 implementation + nhận diện riêng của Finance App: xanh sáng, card trắng bo tròn, số tiền nổi bật, icon rounded đồng bộ, ít chữ kỹ thuật, thao tác nhanh.**

Khi phải lựa chọn giữa “giống MISA hơn” và “dễ dùng + đúng nghiệp vụ dự án hơn”, ưu tiên **đúng nghiệp vụ dự án**.

Khi phải lựa chọn giữa hiệu ứng đẹp và độ tin cậy dữ liệu, ưu tiên **độ tin cậy dữ liệu**.


## Bổ sung 26/10/2026: kỳ lãi tháng
Màn Theo dõi tiền lãi dùng Lãi kỳ hiện tại · chưa chốt, Lãi đã chốt, Lãi đến hạn còn thu, kỳ 25–25 và lịch sử từng đợt/thu tiền. Nhãn Lãi hôm nay ở mô tả cũ được thay thế. Thiếu kết nối hiển thị lỗi thật, không dùng dữ liệu mẫu. Giữ font Be Vietnam Pro, theme và component hiện có.

## Bổ sung 29/10/2026: lãi Actual/365
Thay quy tắc tháng bằng dư nợ cuối ngày × số ngày thực tế × lãi suất năm / 365; vẫn chốt ngày 25. Lãi kỳ hiện tại là dự kiến đến cuối kỳ, giả định không có giao dịch mới. Giải ngân/thu gốc có hiệu lực ngay ngày thực tế.
