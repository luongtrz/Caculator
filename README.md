# CaculateApp - Ứng dụng quản lý cân gạo

Ứng dụng Android giúp quản lý và tính toán khối lượng gạo, hỗ trợ xuất hóa đơn PDF/hình ảnh.

## Tính năng

- **Đăng nhập xác thực** - Firebase Authentication
- **Quản lý bản ghi cân** - Thêm, sửa, xoá các phiên cân gạo
- **Tính toán tự động** - Tổng khối lượng và thành tiền theo đơn giá
- **Xuất hóa đơn** - Hỗ trợ xuất PDF và hình ảnh
- **Lịch sử** - Xem lại các bản ghi đã lưu trên Firestore
- **Offline** - Hoạt động không cần mạng, đồng bộ khi có kết nối

## Công nghệ

- **Kotlin** - Ngôn ngữ chính
- **Firebase** - Authentication, Firestore
- **ViewBinding** - Liên kết giao diện
- **ViewModel** - Quản lý trạng thái
- **PdfDocument / MediaStore** - Xuất file

## Cài đặt

1. Clone repository:
   ```bash
   git clone https://github.com/luongtrz/Caculator.git
   ```

2. Mở project bằng Android Studio

3. Cấu hình Firebase:
   - Tạo project Firebase tại [Firebase Console](https://console.firebase.google.com/)
   - Thêm file `google-services.json` vào thư mục `app/`
   - Kích hoạt Authentication (Email/Password) và Firestore

4. Build và chạy ứng dụng

## Cấu trúc project

```
app/src/main/java/com/example/caculateapp/
├── auth/           # Xác thực (Login, AuthManager)
├── data/           # FirebaseService, RiceRecord
├── adapter/        # RecyclerView adapters
├── utils/          # ExportManager (PDF, Image)
├── viewmodel/      # ViewModel (Main, History)
├── MainActivity    # Màn hình chính (nhập cân)
└── HistoryActivity # Màn hình lịch sử
```

## License

Private project - All rights reserved.
