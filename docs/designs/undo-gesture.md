# Rancangan: Undo/Redo Gesture

> **Status:** Rancangan — belum disetujui, belum dikerjakan ·
> **Terakhir ditinjau:** 2026-11-17 · **HEAD:** `95da467` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 2 · [gaps.md](../gaps.md) G18 · [canvas-engine.md](../canvas-engine.md)
>
> **Implementasi 2026-11-17 (working tree, belum commit, belum uji perangkat):**
> detektor swipe tiga jari di dalam blok pan/zoom yang sama
> (`DroidCanvasScreen.kt:535+`), sekali per gesture, pan/zoom dan fling
> ditekan selama gesture. Umpan balik memakai Toast (lihat K3) — bukan
> Snackbar, karena kode ini tidak punya `SnackbarHost`.

## 1. Tujuan

Undo/redo bisa dilakukan tanpa menjangkau tombol: satu gesture di atas
kanvas. Hari ini undo/redo hanya lewat tombol di top bar — di layar besar
atau saat satu tangan memegang stylus, tombol itu jauh.

## 2. Keadaan sekarang (fakta kode)

- `undo()` / `redo()` sudah ada dan sudah benar
  (`DroidCanvasViewModel.kt:171-201`): snapshot seluruh daftar item,
  tumpukan maks 50, tumpukan terpisah untuk goresan
  (`drawingUndoStack`, `drawingRedoStack`). `undo()` otomatis mengarah ke
  goresan saat mode gambar aktif (`_isDrawModeEnabled`).
- Ketersediaan terekspos: `canUndo` / `canRedo` (`StateFlow<Boolean>`,
  `DroidCanvasViewModel.kt:136-147`). Tombol sudah memakai ini.
- Kanvas sudah punya dua `pointerInput`:
  - Penghitung pointer (`DroidCanvasScreen.kt:414-425`): menghitung
    `pressedCount`, mengeset `globalIsMultiTouch` saat > 1 pointer.
  - Pan/zoom (`DroidCanvasScreen.kt:535+`): `awaitEachGesture` +
    `calculateZoom()` / `calculatePan()`, dengan `touchSlop` sebagai
    gerbang — di bawah ambang, gesture dianggap tap.
- `detectTransformGestures` diimpor tapi **tidak pernah dipakai**
  (`DroidCanvasScreen.kt:37`) — jangan dipakai untuk fitur ini; blok
  `awaitEachGesture` di atas sudah memiliki gesture kanvas.
- Item punya gesture sendiri: tap (`detectTapGestures`,
  `DroidCanvasScreen.kt:3078`) dan drag (`detectDragGestures`, `:3404`).
  Gesture kanvas harus tidak berebut dengan drag item.

## 3. Analisis benturan (ini inti rancangannya)

Dua jari **sudah dimiliki** pan/zoom: begitu dua pointer bergerak, blok
pan/zoom mengubah skala dan translasi. Gesture undo dua jari akan bentrok
dengan itu — tidak ada cara yang bersih untuk membedakan "swipe dua jari"
dari "pinch yang kebetulan horizontal". Karena itu:

| Kandidat | Benturan | Penilaian |
|---|---|---|
| Swipe dua jari | Langsung dengan pan/zoom | **Ditolak** |
| Double-tap dua jari | Tap dua jari hari ini tidak dipakai | Cadangan — mudah tertukar tap biasa |
| **Swipe tiga jari (kiri = undo, kanan = redo)** | Tidak ada gesture tiga jari hari ini | **Dipilih** — satu-satunya yang tidak berebut |

## 4. Keputusan yang harus diambil dulu

| # | Keputusan | Pilihan | Rekomendasi |
|---|---|---|---|
| K1 | Gesture yang dipakai | Swipe tiga jari / double-tap dua jari | **Swipe tiga jari** (bagian 3) |
| K2 | Arah | Kiri = undo (konvensi umum) / sebaliknya | **Kiri = undo, kanan = redo** |
| K3 | Umpan balik | Snackbar / toast / tanpa umpan balik | **Snackbar singkat** ("Undo" / "Redo") — gesture tak terlihat seperti tombol, pengguna butuh konfirmasi | **Diubah saat implementasi 2026-11-17: Toast**, karena layar ini tidak punya `SnackbarHost` dan umpan balik di kode ini sudah memakai `Toast` (`DroidCanvasScreen.kt:296, 3302`) |
| K4 | Haptic | Ya / tidak | **Tidak di v1** — tambah nanti bila terasa kurang |

## 5. Rancangan

- Deteksi di dalam blok pan/zoom yang sama (`DroidCanvasScreen.kt:535+`),
  **bukan** `pointerInput` baru: satu tempat yang tahu jumlah pointer,
  `touchSlop`, dan status `pastTouchSlop`.
- Syarat picu (semua harus terpenuhi):
  1. Tepat 3 pointer aktif saat gesture berjalan.
  2. Perpindahan horizontal total > `touchSlop * 2`, perpindahan vertikal
     < `touchSlop` (swipe harus jelas horizontal).
  3. Perubahan zoom `≈ 1` selama gesture (bukan pinch).
  4. Tidak ada drag item aktif (`globalIsMultiTouch` sudah true, dan
     handler drag item tidak berjalan).
- **Event pendaratan jari ke-3 dilewati** (ditemukan saat pengecekan ulang
  2026-11-17): saat jari ke-3 mendarat, centroid melompat ke arahnya dan
  `calculatePan()` melaporkan lompatan itu sebagai gerakan — tanpa
  dilewati, sekadar menaruh jari ke-3 di sisi kanvas sudah bisa memicu
  undo/redo. Akumulasi baru dimulai dari event berikutnya
  (`prevPressedCount == 3`).
- Picu **sekali per gesture**, saat ambang terlampaui (bukan saat jari
  diangkat — respons terasa langsung), lalu kunci sampai semua pointer
  diangkat.
- Panggil `viewModel.undo()` / `viewModel.redo()` yang sudah ada — tanpa
  logika undo baru. Di mode gambar, `undo()` sudah mengarah ke goresan
  dengan sendirinya.
- Bila tumpukan kosong (`canUndo`/`canRedo` false), gesture tidak
  melakukan apa-apa dan tidak menampilkan snackbar.
- Catat gesture ini di `decisions.md` (ADR baru) **sebelum** implementasi
  — gesture adalah kontrak perilaku, bukan detail kode.

## 6. To-do

| # | Kerja | Target / bukti selesai |
|---|---|---|
| T1 | Putuskan K1–K4 (bagian 4) | Keputusan tercatat di `decisions.md` (ADR baru) |
| T2 | Implementasi detektor di blok pan/zoom (`:535+`) | Swipe tiga jari kiri/kanan memanggil `undo()`/`redo()`; tidak ada `pointerInput` baru |
| T3 | Kunci sekali-per-gesture + syarat vertikal/zoom (bagian 5) | Satu swipe = satu undo; swipe diagonal tidak memicu; pinch tiga jari tidak memicu |
| T4 | Snackbar hasil + diam saat tumpukan kosong | Uji manual: undo kosong → tidak ada snackbar |
| T5 | Uji benturan: drag item, resize item, pan/zoom dua jari, mode gambar | Tidak satu pun memicu undo/redo secara tidak sengaja |
| T6 | Uji manual di perangkat (tidak ada tes UI otomatis — G8) | Daftar cek: kiri = undo, kanan = redo, mode gambar = undo goresan, redo setelah undo |
| T7 | Perbarui `roadmap.md` (langkah 2 → selesai), `canvas-engine.md`, `ui.md` | Dokumen konsisten dengan kode |

## 7. Di luar lingkup

- Gesture rotate dua jari (fitur lain; rotate hari ini lewat slider).
- Undo per item — snapshot undo adalah seluruh papan (G18).
- Gesture lain (mis. shake-to-undo) — satu gesture dulu, buktikan dulu.
- Mengubah isi/logika `undo()` / `redo()` — fitur ini hanya memanggil
  yang sudah ada.
