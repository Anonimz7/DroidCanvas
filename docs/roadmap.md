# Roadmap

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`
> Dokumen ini mengaudit roadmap di `README.md` terhadap kode. Bagian "Keadaan
> sebenarnya" menang atas README bila bertentangan.

## Audit roadmap README vs kode

### Selesai (menurut README) — hasil audit

| Item README | Keadaan sebenarnya | Bukti |
|---|---|---|
| Grayscale toggle | ❌ **Tidak ada** | Kolom `isGrayscale` ditambah migrasi 5–6 lalu dihapus 7–8; entity tanpa field itu; `ColorMatrix` diimpor tapi tak dipakai; tanpa toggle di UI. Lihat ADR-010 |
| Flip transformations | ✅ Ada | `flipHorizontal/flipVertical` persisten di DB; toggle per item |
| Animated GIF support | ✅ Ada | Coil `GifDecoder`/`ImageDecoderDecoder` di `MainActivity`; GIF disalin mentah di `ImageStorageHelper` |
| Auto-arrange | ✅ Ada (lebih dari klaim) | 5 layout: GRID, STRIP, COLUMN, RADIAL, SPREAD |
| Multi-image import | ✅ Ada | `PickMultipleVisualMedia` + share intent `SEND_MULTIPLE` + stagger 60px |
| Undo/Redo | ✅ Ada (tombol) | 2 tumpukan snapshot, maks 50; tombol di top bar + toolbar gambar |

### Berjalan (menurut README) — hasil audit

| Item README | Keadaan sebenarnya |
|---|---|
| Tablet optimization | ⚠️ **Sebatas breakpoint** — satu-satunya adaptasi tablet adalah sidebar tetap vs drawer pada `maxWidth >= 720.dp`. Tanpa layout khusus tablet, tanpa dukungan stylus/pen khusus, tanpa multi-window. |
| Crop tool | ✅ **Sudah jadi** — `CropImageDialog` lengkap (rasio, handle) + `cropCanvasItem` di ViewModel. Status "berjalan" di README sudah kedaluwarsa; pindahkan ke selesai. |
| Export/Import project file | ❌ **Belum ada sama sekali** — tidak ada serializer, format, atau UI. Satu-satunya "export" adalah `exportSchema = false` di Room (itu arti berbeda). |

### Belum selesai (menurut README)

| Item README | Keadaan sebenarnya |
|---|---|
| Undo/redo Gesture | ❌ Belum ada — undo/redo hanya lewat tombol. Tidak ada deteksi swipe/gesture untuk undo di kode. |

## Urutan kerja yang diusulkan

Bukan janji — urutan ini memaksimalkan agar fondasi tidak dibangun dua kali.
Setiap langkah merujuk ke dokumen yang relevan.

### 0. Bereskan klaim (sebelum membangun apa pun) — kecil

- Perbarui README: pindahkan crop ke selesai; hapus centang grayscale (atau
  implementasikan — lihat langkah 4); tandai export/import dan undo-gesture
  sebagai belum. Perbaiki link `meaayu/DroidCanvas` → `Anonimz7/DroidCanvas`
  (README masih menunjuk ke repo lama untuk APK dan clone).
- Putuskan nasib kolom mati (`Board.canvasScale` dkk, `backgroundType`,
  `isGrayscale`) — lihat G3/G10. Keputusan ini memengaruhi format proyek
  (langkah 1), jadi harus diambil dulu.

### 1. Export/import project file — fitur besar berikutnya

Rancangan: [designs/export-import.md](designs/export-import.md)

Desain dulu, baru kode (satu dokumen desain per fitur, pola photocraft):

- **Format:** satu arsip (ZIP) berisi manifest JSON berversi + berkas gambar
  (full + thumb) + goresan. Nomor versi format di manifest; loader menolak versi
  yang tidak dikenal dengan pesan yang jelas.
- **Isi manifest:** papan (nama, background, kamera), item (semua kolom
  `CanvasItem` kecuali path absolut — path diganti nama berkas di arsip),
  goresan, versi format, versi aplikasi.
- **Serializer terpisah** dari Room dan UI (kelas murni, bisa diuji seperti
  `StrokeOptimizer`). Impor = validasi → tulis berkas → insert Room dalam satu
  transaksi; impor yang gagal tidak boleh meninggalkan papan setengah jadi.
- **UI:** tombol export/import di tab BOARD; berbagi lewat `FileProvider`
  (perhatikan `file_paths.xml` hari ini hanya mengekspos `downloads/`).
- Prasyarat: keputusan langkah 0 (kolom mana yang ikut diekspor).

### 2. Undo/redo gesture — kecil-menengah

Rancangan: [designs/undo-gesture.md](designs/undo-gesture.md)

- Pilih satu gesture (misalnya swipe dua jari / tiga jari) dan tetapkan di
  `decisions.md` sebelum implementasi — gesture harus tidak bentrok dengan
  pan/zoom dua jari yang sudah ada.
- Implementasi: panggil `viewModel.undo()/redo()` yang sudah ada; tanpa logika
  undo baru. Tambahkan umpan balik visual (toast/animasi) karena gesture tidak
  terlihat seperti tombol.
- Perlu tes gesture manual di perangkat (tidak ada tes UI otomatis hari ini).

### 3. Tablet optimization — menengah

Rancangan: [designs/tablet.md](designs/tablet.md)

- Definisikan dulu apa artinya "optimal untuk tablet" untuk aplikasi ini:
  kandidatnya panel ganda (sidebar + inspector bersamaan), dukungan stylus
  (tekanan, palm rejection), layout landscape, multi-window.
- Hari ini hanya ada breakpoint 720dp; langkah ini dimulai dari keputusan
  cakupan, bukan dari kode.

### 4. Grayscale toggle — kecil (kalau jadi dikerjakan)

Rancangan: [designs/grayscale.md](designs/grayscale.md)

- Implementasi yang konsisten: tambah flag persisten per item (bukan kolom
  migrasi yang lalu dihapus), terapkan `ColorFilter.colorMatrix` saat render
  di `CanvasItemView`, dan pastikan flag itu ikut dalam export/import (langkah 1).
- Alternatif yang sah: nyatakan values study sebagai pengganti grayscale dan
  hapus item ini dari roadmap. Yang tidak boleh: klaim selesai tanpa kode.

### Pekerjaan fondasi (jalan bersamaan, sebelum rilis publik)

- **Pecah `DroidCanvasScreen.kt`** (G1) — bertahap, satu ekstraksi per commit.
- **Ganti package `com.example`** (G9) — sekali jalan.
- **CI menjalankan tes** (G7) — job tanpa keystore: `assembleDebug` +
  `testDebugUnitTest`.
- **Pembersih file yatim** (G5) — sweep berkas tanpa referensi, aman terhadap undo.
