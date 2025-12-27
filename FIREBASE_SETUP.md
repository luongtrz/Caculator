# Firebase Setup Guide - Phase 1

## ⚠️ QUAN TRỌNG: Bạn cần làm các bước này trước khi build app!

## Bước 1: Tạo Firebase Project

1. Truy cập https://console.firebase.google.com
2. Click "Add project" (hoặc "Thêm dự án")
3. Đặt tên project: **"Caculate App"** (hoặc tên bạn muốn)
4. Disable Google Analytics (không cần thiết cho app này)
5. Click "Create project"
6. Đợi Firebase tạo project (~30 giây)

## Bước 2: Thêm Android App vào Firebase

1. Trong Firebase Console, click icon **Android** (hình robot)
2. Điền thông tin:
   - **Android package name**: `com.example.caculateapp` (phải chính xác!)
   - **App nickname**: "Caculate App"
   - **Debug signing certificate SHA-1**: 
   
   Để lấy SHA-1, mở Terminal/Command Prompt và chạy:
   
   **Windows**:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore %USERPROFILE%\.android\debug.keystore
   ```
   
   **Mac/Linux**:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore
   ```
   
   - Password: `android`
   - Copy dòng **SHA1:** và paste vào Firebase
   
3. Click "Register app"

## Bước 3: Download google-services.json

1. Click "Download google-services.json"
2. **QUAN TRỌNG**: Copy file này vào thư mục:
   ```
   L:/3-HK2/mobile/app/google-services.json
   ```
3. **Thay thế** file placeholder hiện tại
4. Click "Next" → "Next" → "Continue to console"

## Bước 4: Enable Google Sign-in

1. Trong Firebase Console, vào **Authentication**
2. Click tab **Sign-in method**
3. Click **Google**
4. Bật switch "Enable"
5. Chọn support email (email Google của bạn)
6. Click "Save"

## Bước 5: Create Firestore Database

1. Trong Firebase Console, vào **Firestore Database**
2. Click "Create database"
3. Chọn location: **asia-southeast1 (Singapore)** (gần Vietnam nhất)
4. Start in **Production mode** (chúng ta sẽ tự viết security rules)
5. Click "Enable"

## Bước 6: Setup Firestore Security Rules

1. Trong Firestore Database, click tab **Rules**
2. Thay thế code hiện tại bằng:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users collection
    match /users/{userId}/records/{recordId} {
      // User chỉ được đọc/ghi data của chính mình
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

3. Click "Publish"

## ✅ Xong! Giờ bạn có thể build app

### Build và Test

1. Sync Gradle (nếu có lỗi về google-services.json, check lại Bước 3)
2. Build app (Ctrl+F9)
3. Run app (Shift+F10)
4. Click nút "Sign in with Google"
5. Chọn tài khoản Google
6. Kiểm tra authentication thành công

---

## 🚨 Troubleshooting

### Lỗi: "Unable to find google-services.json"
- Check file có đúng ở `app/google-services.json` không
- File phải tên chính xác `google-services.json`

### 4. Lỗi "Cloud Firestore API has not been used"
- **Nguyên nhân:** Chưa kích hoạt Firestore Database trên Firebase Console.
- **Cách fix:**
  1. Vào Firebase Console > Build > Firestore Database.
  2. Bấm "Create Database".
  3. Chọn Location (vd: `asia-southeast1`).
  4. Chọn Rules (Test Mode cho dễ test).
- **Hoặc:** Vào link trong log error và bấm ENABLE API.

### Lỗi: "SHA-1 not found" hoặc Google Sign-in tự đóng
- Bạn chưa thêm SHA-1 vào Firebase Console
- Xem lại Bước 2

### Lỗi: "FirebaseApp not initialized"
- Check `google-services.json` có đúng format không
- Sync lại Gradle
- Clean Build (Build > Clean Project)

### Lỗi: "API not enabled"
- Vào Firebase Console
- Enable "Google Sign In API"

---

## 📋 Next Steps

Sau khi Phase 1 hoàn thành, chúng ta sẽ tiếp tục:
- **Phase 2**: Tạo FirebaseService cho Firestore CRUD
- **Phase 3**: Update ViewModel và HistoryActivity
- **Phase 4**: Migration từ Room và testing

Hãy chạy thử app xem Google Sign-in có hoạt động không trước khi tiếp tục!
