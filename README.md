# Cognify Notch untuk Android (tablet) / for Android tablets

Notch kecil yang menempel di tepi atas layar tablet Android, di atas aplikasi lain: musik yang sedang diputar dengan lirik tersinkron, dan tugas dengan pengingat.
A small notch pinned to the top edge of an Android tablet, over other apps: now playing with synced lyrics, and tasks with reminders.

- Android 8.0 (API 26) ke atas / and up. Kotlin + Jetpack Compose, tanpa library pihak ketiga selain AndroidX.
- Hemat: saat tertutup tidak ada animasi, timer, atau polling; hanya satu foreground service.
- Lirik dari [LRCLIB](https://lrclib.net) (judul, artis, album, dan durasi dikirim, hanya saat panel musik dibuka). Tanpa akun, tanpa pelacakan.

## Izin / Permissions

| Izin | Untuk apa |
|---|---|
| Tampil di atas aplikasi lain / Display over other apps | menampilkan notch |
| Akses notifikasi / Notification access | membaca sesi media (lagu yang diputar). Isi notifikasi tidak dibaca |
| Notifikasi / Notifications (Android 13+) | pengingat tugas dan indikator layanan |
| Internet | lirik dari LRCLIB |

## Pasang / Install

Unduh APK dari halaman [Releases](../../releases), lalu buka di tablet (izinkan "pasang dari sumber ini"). Buka app, ketuk **Tampilkan notch**, dan beri izin yang diminta.

## Membangun / Build

```bash
export ANDROID_HOME=/path/ke/android-sdk   # platform 36, build-tools 36
./gradlew testDebugUnitTest assembleDebug  # build debug + tes parser waktu
./gradlew assembleRelease                  # perlu kunci tanda tangan (lihat bawah)
```

Build rilis membaca `keystore.properties` dari `../../notch-android-signing/` atau dari path di `COGNIFY_KEYSTORE_PROPS`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Tanpa berkas itu, APK rilis tidak ditandatangani. Jangan pernah memasukkan kunci ke repo.
Build debug punya `DemoReceiver` (membuat sesi media palsu) untuk pengujian tanpa aplikasi musik.

## Lisensi / License

AGPL-3.0, sama dengan Cognify Notch untuk Mac dan Windows.
