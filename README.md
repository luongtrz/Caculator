# CaculateApp - Ứng dụng quản lý cân lúa

Ứng dụng Android (100% Offline) giúp quản lý và tính toán khối lượng cân lúa, xuất hóa đơn PDF/hình ảnh và chia sẻ bản ghi qua mã QR.

## Tính năng

- **100% Offline** - Không cần mạng, không cần tài khoản, dữ liệu lưu hoàn toàn trên máy (Room Database SQLite)
- **Quản lý bản ghi cân** - Thêm, sửa, xoá các đợt cân lúa
- **Chia sẻ qua mã QR** - Chọn 1 hoặc nhiều đợt cân để tạo mã QR nén; máy khác quét nhận dữ liệu tức thì (không cần internet)
- **Tính toán tự động** - Tổng khối lượng, trừ bì, thành tiền theo đơn giá và tiền cọc
- **Xuất hóa đơn** - Hỗ trợ xuất PDF và hình ảnh biên lai
- **Lịch sử cân** - Quản lý, tìm kiếm và chọn nhiều đợt cân tiện lợi

## Công nghệ

- **Kotlin** - Ngôn ngữ chính
- **Room Database** - Lưu trữ dữ liệu SQLite cục bộ (Offline-first)
- **ZXing & Google Code Scanner** - Tạo và quét mã QR chia sẻ dữ liệu P2P
- **ViewBinding** - Liên kết giao diện
- **ViewModel & LiveData** - Quản lý trạng thái
- **PdfDocument / MediaStore** - Xuất file PDF / Ảnh

## Cài đặt & Sử dụng

1. Clone repository:
   ```bash
   git clone https://github.com/luongtrz/Caculator.git
   ```

2. Mở project bằng Android Studio

3. Build và cài đặt trực tiếp vào thiết bị Android (hoặc chạy `./gradlew assembleDebug`). Không cần cấu hình file key hay Firebase.

## Cấu trúc project

```
app/src/main/java/com/example/caculateapp/
├── data/           # Room Database, RiceDao, RiceRecord
├── adapter/        # RecyclerView adapters (HistoryAdapter, ColumnAdapter)
├── utils/          # ExportManager (PDF, Image), QrTransferManager (QR Encode/Decode)
├── viewmodel/      # ViewModel (MainViewModel, HistoryViewModel)
├── MainActivity    # Màn hình nhập cân lúa
└── HistoryActivity # Màn hình danh sách lịch sử & Quét/Tạo QR
```

## License

Private project - All rights reserved.
