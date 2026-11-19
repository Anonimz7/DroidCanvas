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
| Save/Open project file | ✅ **Selesai & teruji perangkat 2026-11-19 (Fase 1–7)** — format `.dc` (ZIP + manifest v1, `designs/manifest-schema.md`); `ProjectSaver` + `ProjectOpener` dengan unit test (5/5 + 7/7 terbukti; 2 test baru T7/T8 belum dieksekusi); tombol Save + Open + progres + berbagi lewat FileProvider sudah ada; **Save ulang 2026-11-19:** simpan ke `Download/droidcanvas` (MediaStore), dialog nama, Save As + Share terpisah. Uji manual end-to-end Fase 7 lulus 2026-11-19 (laporan user: jalan dan berhasil). Lihat `designs/save-open.md` |

### Belum selesai (menurut README)

Bagian ini kosong sejak 2026-11-18: Grayscale pindah ke selesai, dan
Undo/redo Gesture dibatalkan setelah sempat diimplementasikan lalu dihapus
(lihat langkah 2). README kini punya bagian Planned — lihat di bawah.

### Planned (menurut README)

| Item README | Keadaan sebenarnya |
|---|---|
| Map view | ✅ **Selesai & teruji perangkat 2026-11-19** — tampilan kedua + model edge/notes sudah ada; uji perangkat (fase 7) lulus 2026-11-19 (laporan user: jalan dan berhasil). Rancangan di langkah 5. |

## Urutan kerja yang diusulkan

Bukan janji — urutan ini memaksimalkan agar fondasi tidak dibangun dua kali.
Setiap langkah merujuk ke dokumen yang relevan.

### 0. Bereskan klaim (sebelum membangun apa pun) — kecil

> **Selesai 2026-11-17** (remote `95da467`). Grayscale yang disebut di bawah
> kemudian diimplementasikan 2026-11-17 dan teruji di perangkat 2026-11-18 —
> lihat langkah 4.

- Perbarui README: pindahkan crop ke selesai; hapus centang grayscale (atau
  implementasikan — lihat langkah 4); tandai Save/Open dan undo-gesture
  sebagai belum. Perbaiki link `meaayu/DroidCanvas` → `Anonimz7/DroidCanvas`
  (README masih menunjuk ke repo lama untuk APK dan clone).
- Putuskan nasib kolom mati (`Board.canvasScale` dkk, `backgroundType`,
  `isGrayscale`) — lihat G3/G10. Keputusan ini memengaruhi format proyek
  (langkah 1), jadi harus diambil dulu.

### 1. Save/Open project file — fitur besar berikutnya

> **Keputusan K1–K6 disetujui 2026-11-19 (sesuai rekomendasi)** — lihat `designs/save-open.md` §3 (ADR-015). Fase 1 + Fase 2 selesai & di-push; **Fase 3 selesai 2026-11-19** (`ProjectOpener` + `ProjectOpenerTest` 7/7 hijau + `cache-path` di `file_paths.xml` — kompilasi + unit test terbukti, lihat `designs/save-open.md` §5.1); Fase 4 selesai (kode: tombol Save + Open + progres + pesan hasil); Fase 5 selesai (kode test T7/T8/T10 — 2 test baru belum dieksekusi); Fase 6 selesai (dokumen penutup, T9). Fase 7 lulus 2026-11-19 (uji manual end-to-end di perangkat, laporan user).

Rancangan: [designs/save-open.md](designs/save-open.md)

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
- **UI:** tombol Save/Open di tab BOARD; berbagi lewat `FileProvider`
  (perhatikan `file_paths.xml` hari ini hanya mengekspos `downloads/`).
- Prasyarat: keputusan langkah 0 (kolom mana yang ikut disimpan).

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
  di `CanvasItemView`, dan pastikan flag itu ikut dalam Save/Open (langkah 1).
- Alternatif yang sah: nyatakan values study sebagai pengganti grayscale dan
  hapus item ini dari roadmap. Yang tidak boleh: klaim selesai tanpa kode.

### 5. Map view — node graph (selesai, teruji perangkat 2026-11-19)

Rancangan: [designs/map.md](designs/map.md)

> **Dibangun 2026-11-18 (fase 1–5, di-push remote `0dceada`):** tampilan kedua
> di atas data Board yang sama; node + edge bertipe 8 jenis + notes per node;
> layout node sendiri (bukan posisi kanvas); garis polos tanpa panah; garis
> fleksibel (geometri turunan).

- `designs/map.md` (keputusan K1–K8 + O1–O5, disetujui user 2026-11-18;
  to-do T1–T9 selesai, T10 menunggu uji perangkat — fase 7).
- Migrasi 9→10 sudah jalan (tabel `edges` + kolom `mapX`/`mapY`/`note`);
  format Save/Open sudah mencakup data Map (lihat langkah 1, T10 di
  `designs/save-open.md`).

### Pekerjaan fondasi (jalan bersamaan, sebelum rilis publik)

- **Pecah `DroidCanvasScreen.kt`** (G1) — bertahap, satu ekstraksi per commit.
- **Ganti package `com.example`** (G9) — sekali jalan.
- **CI menjalankan tes** (G7) — job tanpa keystore: `assembleDebug` +
  `testDebugUnitTest`.
- **Pembersih file yatim** (G5) — sweep berkas tanpa referensi, aman terhadap undo.
