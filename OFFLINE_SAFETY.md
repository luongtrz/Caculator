# Offline Data Safety - Hướng dẫn quan trọng

## ⚠️ Vấn đề: Mất dữ liệu khi đăng xuất offline

### Scenario nguy hiểm:

```
1. Ngoài đồng (KHÔNG CÓ MẠNG)
2. Nhập 10 đợt cân → Lưu thành công (vào cache local)
3. Bấm "Đăng xuất" TRƯỚC KHI về nhà có WiFi
4. ❌ MẤT 10 đợt vừa nhập!
```

**Nguyên nhân:** Dữ liệu chưa kịp sync lên Firestore cloud, cache local bị clear khi sign out.

---

## ✅ Giải pháp đã implement

### 1. **Smart Sign Out Check**

Khi user bấm "Đăng xuất", app sẽ:

```kotlin
// Check pending writes với timeout 3 giây
firestore.waitForPendingWrites().await()
```

**Nếu có data chưa sync:**
- ⚠️ Hiện cảnh báo đỏ
- "Có dữ liệu chưa được đồng bộ lên cloud!"
- 2 options:
  - **"Vẫn đăng xuất (RỦI RO)"** → Cho phép nhưng cảnh báo rõ
  - **"Hủy - Giữ an toàn"** → Khuyến nghị

**Nếu đã sync hết:**
- ✅ Hiện dialog bình thường
- "Dữ liệu đã được lưu an toàn trên cloud"

---

## 📱 Hướng dẫn sử dụng an toàn

### Khi offline (ngoài đồng):

✅ **AN TOÀN:**
1. Nhập dữ liệu bình thường
2. Về nhà, kết nối WiFi/4G
3. **Đợi 5-10 giây** (để Firestore sync)
4. Đăng xuất → Không mất data

❌ **NGUY HIỂM:**
1. Nhập dữ liệu offline
2. Đăng xuất NGAY (chưa có mạng)
3. Data mất!

---

## 🔧 Technical Details

### Firestore Offline Persistence

```kotlin
// CaculateApplication.kt
Firebase.firestore.firestoreSettings = firestoreSettings {
    isPersistenceEnabled = true
    cacheSizeBytes = CACHE_SIZE_UNLIMITED
}
```

**Cách hoạt động:**
- Write offline → Lưu cache + mark "pending"
- Khi online → Auto sync pending writes
- `waitForPendingWrites()` → Check còn pending không

---

## 🎯 Best Practices cho User

### Khuyến nghị cho nông dân:

1. **Trước khi đăng xuất:**
   - Kết nối WiFi/4G
   - Mở app, đợi 10 giây
   - Xem icon sync (nếu có)
   - Mới đăng xuất

2. **Nếu vội:**
   - ĐỪNG đăng xuất khi offline
   - Để app chạy background
   - Đăng xuất sau khi về nhà

3. **Emergency:**
   - Nếu phải đăng xuất offline
   - App sẽ cảnh báo rõ
   - Chấp nhận rủi ro nếu data không quan trọng

---

## 📊 Monitoring Sync Status (Future Enhancement)

Có thể thêm indicator để user biết:

```kotlin
// Ý tưởng: Badge hiển thị pending writes
firestore.addSnapshotsInSyncListener {
    // Callback khi sync xong
    showSyncCompleteIcon()
}
```

**UI suggestion:**
- Icon cloud ✅ = đã sync
- Icon cloud ⏳ = đang sync
- Icon cloud ⚠️ = có lỗi

---

## 🚨 Lưu ý quan trọng

1. **3 giây timeout** cho pending check:
   - Đủ cho most cases
   - Tránh user đợi quá lâu
   - Nếu timeout → Assume có pending (safe)

2. **User vẫn có thể force sign out:**
   - Không block hoàn toàn
   - Chỉ cảnh báo
   - User decide dựa trên tình huống

3. **Best effort, not guarantee:**
   - Network có thể fail
   - Firestore có thể error
   - Luôn khuyến nghị: sync trước khi sign out
