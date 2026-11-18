# Roadmap

> **Terakhir ditinjau:** 2026-11-18 · **HEAD:** `c3a14d8`
> Dokumen ini mengaudit roadmap di `README.md` terhadap kode. Bagian "Keadaan
> sebenarnya" menang atas README bila bertentangan.

## Audit roadmap README vs kode

### Selesai (menurut README) — hasil audit

| Item README | Keadaan sebenarnya | Bukti |
|---|---|---|
| Grayscale toggle | ✅ **Ada** | Flag persisten per item + migrasi 8→9; `ColorFilter.colorMatrix` di kanvas & thumbnail layer; tombol di tab LAYERS (values study menang bila aktif). Teruji di perangkat 2026-11-18. Lihat ADR-010 |
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

Bagian ini kosong sejak 2026-11-18: Grayscale pindah ke selesai, dan
Undo/redo Gesture dibatalkan setelah sempat diimplementasikan lalu dihapus
(lihat langkah 2). README kini punya bagian Planned — lihat di bawah.

### Planned (menurut README)

| Item README | Keadaan sebenarnya |
|---|---|
| Map view | ❌ **Belum ada** — tidak ada tampilan kedua, tidak ada model edge/notes. Rancangan di langkah 5. |

## Urutan kerja yang diusulkan

Bukan janji — urutan ini memaksimalkan agar fondasi tidak dibangun dua kali.
Setiap langkah merujuk ke dokumen yang relevan.

### 0. Bereskan klaim (sebelum membangun apa pun) — kecil

> **Selesai 2026-11-17** (remote `95da467`). Grayscale yang disebut di bawah
> kemudian diimplementasikan 2026-11-17 dan teruji di perangkat 2026-11-18 —
> lihat langkah 4.

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

> **Dibatalkan 2026-11-18 atas perintah user:** sempat diimplementasikan
> (swipe tiga jari, 2026-11-17, remote `05a581c`) lalu dihapus — di perangkat
> user bentrok dengan gesture Android/ROM ponsel; undo/redo tetap lewat
> tombol yang sudah ada. Rancangan disimpan sebagai arsip di
> [designs/undo-gesture.md](designs/undo-gesture.md).

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

> **Selesai 2026-11-17, teruji di perangkat 2026-11-18** (remote `05a581c`):
> flag persisten per item, migrasi 8→9, toggle di tab LAYERS.

Rancangan: [designs/grayscale.md](designs/grayscale.md)

- Implementasi yang konsisten: tambah flag persisten per item (bukan kolom
  migrasi yang lalu dihapus), terapkan `ColorFilter.colorMatrix` saat render
  di `CanvasItemView`, dan pastikan flag itu ikut dalam export/import (langkah 1).
- Alternatif yang sah: nyatakan values study sebagai pengganti grayscale dan
  hapus item ini dari roadmap. Yang tidak boleh: klaim selesai tanpa kode.

### 5. Map view — node graph (dipahami, diparkir)

Rancangan: [designs/map.md](designs/map.md)

> **Dipahami 2026-11-18, diparkir atas perintah user:** tampilan kedua di atas
> data Board yang sama; node + edge bertipe 8 jenis + notes per node; layout
> node sendiri (bukan posisi kanvas); garis polos tanpa panah; garis fleksibel
> (geometri turunan).

- Rancangan sudah lengkap di `designs/map.md` (keputusan K1–K7 + O1–O5,
  disetujui user 2026-11-18; to-do T1–T10). Bangun hanya bila user membuka kembali.
- Dependensi: migrasi berikutnya (9→10) bila dikerjakan sebelum pembersihan
  kolom mati — lihat langkah 0; format export/import harus mencakup edge,
  notes, dan posisi Map (lihat langkah 1).

### Pekerjaan fondasi (jalan bersamaan, sebelum rilis publik)

- **Pecah `DroidCanvasScreen.kt`** (G1) — bertahap, satu ekstraksi per commit.
- **Ganti package `com.example`** (G9) — sekali jalan.
- **CI menjalankan tes** (G7) — job tanpa keystore: `assembleDebug` +
  `testDebugUnitTest`.
- **Pembersih file yatim** (G5) — sweep berkas tanpa referensi, aman terhadap undo.
