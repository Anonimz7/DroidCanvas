# Mesin Kanvas

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`
> Sumber: `DroidCanvasScreen.kt` (gesture & render), `DroidCanvasViewModel.kt`,
> `CanvasMathHelper.java`, `CanvasPhysicsHelper.java`, `CanvasZoomHelper.java`,
> `StrokeOptimizer.java`, `ImageValueProcessor.java`, `ValueProcessor.kt`.

## Sistem koordinat

Ada dua ruang, jangan dicampur:

- **Ruang layar** (px layar): tempat jari menyentuh dan tempat Compose menggambar.
- **Ruang kanvas** (unit kanvas): tempat `CanvasItem.posX/posY/width/height` tinggal.
  Konversi: `kanvas = (layar − translate) / scale`.

Kamera kanvas = tiga angka di ViewModel: `canvasScale`, `canvasTranslateX`,
`canvasTranslateY`. Render memakai `graphicsLayer` dengan `transformOrigin = (0, 0)`,
jadi translate diterapkan sebelum scale. Posisi kamera disimpan ke SharedPreferences
dengan debounce 1 detik (bukan ke Room — lihat ADR-004).

## Gesture kanvas (latar)

Implementasi sendiri dengan `awaitPointerEventScope` di `DroidCanvasScreen.kt:535+`,
bukan `detectTransformGestures` (yang hanya diimpor, tidak dipakai di jalur ini):

1. **Pointer down** → batalkan fling yang sedang berjalan; mulai `VelocityTracker`.
2. **Gerakan melewati touchSlop** → bukan tap; terapkan zoom + pan per event lewat
   `CanvasZoomHelper.computeZoom` (zoom ke centroid, batas skala **0.01–10**).
3. **Tap (tanpa gerakan)** → `handleCanvasTap`: ubah tap ke ruang kanvas, cari item
   yang tertumpuk (diurutkan `zIndex` turun), tap berulang pada tumpukan yang sama
   berpindah ke item berikutnya; tap di ruang kosong membatalkan seleksi.
4. **Lepas dengan kecepatan > 150 px/detik** → fling: loop `withFrameNanos` memakai
   `CanvasPhysicsHelper.processDecay` (friction **0.95** per 16 ms) sampai kecepatan
   < 15 px/detik.

Double-tap pada item (`toggleZoomOnItem`): beralih antara skala 0.36 dan target
double-tap (default 2.3, bisa diatur), dengan kamera dipusatkan ke item.

## Manipulasi item

`CanvasItemView` (`DroidCanvasScreen.kt:2956-3630`) — satu item = `AsyncImage`
(Coil, memakai thumbnail) di dalam `graphicsLayer` (posisi, skala, rotasi, flip)
dengan handle di keempat sudut saat terpilih:

- **Drag:** loop `awaitEachGesture` sendiri (bukan `detectDragGestures`): long-press
  500 ms membuka menu konteks; melewati slop 8px memulai drag; delta dirotasi
  balik mengikuti rotasi item (`CanvasMathHelper.rotateDragAmount`); commit posisi
  absolut saat lepas (`updateItemAbsolutePosition`). Tap tunggal memilih item;
  double-tap (< 300 ms) memanggil `toggleZoomOnItem`. Multi-touch pada item
  membatalkan drag dan diserahkan ke gesture kanvas (pan/zoom seluruh kanvas).
- **Handle sudut** (hanya saat item terpilih, tidak di-pin, bukan multi-select):
  kiri-atas tap → toast, long-press → hapus item; kanan-atas tap → flip horizontal;
  kiri-bawah tap → flip vertikal; kanan-bawah diseret → resize (rasio jarak
  terhadap anchor kiri-atas lewat `CanvasMathHelper.calculateNewScale`, batas
  0.05–10, posisi disesuaikan `adjustPositionForScale`, commit saat drag selesai
  lewat `updateItemScaleAndPositionDirectly`).
- **Rotasi & skala numerik** diatur lewat slider di tab LAYERS (rotasi:
  `updateItemRotationInMemory` saat geser + `commitItemRotation` saat lepas).
  Tidak ada gesture putar dua jari pada item; `detectTransformGestures` diimpor
  tapi tidak pernah dipakai di file ini.
- **Pemuatan gambar adaptif:** thumbnail dipakai biasa; gambar resolusi penuh
  dimuat bila item terpilih, bila `(localScale × canvasScale) > 1.2`, atau bila
  berkasnya GIF.
- **Selama gesture** perubahan ditulis ke `_itemOverrides` (in-memory); **saat
  gesture selesai** commit ke Room (ADR-006).
- **Item terkunci (`isPinned`)** menolak semua mutasi posisi/skala/rotasi/flip.
- **Long-press** membuka menu konteks (panel aksi bawah mengambang).
- **Z-order:** `bringToFront` (zIndex = max + 1), `sendToBack` (zIndex = min − 1).
- **Flip** horizontal/vertikal adalah flag persisten di DB, diterapkan saat render.

## Menggambar (draw mode)

- Goresan aktif: `activeStroke` (titik `Offset`, warna, lebar). Warna default merah
  (`0xFFF44336`), lebar default 8f.
- Saat goresan selesai (`finishActiveStroke`): bila > 2 titik, jalankan
  `StrokeOptimizer.optimizeStroke(xs, ys, epsilon=0.8, smoothIterations=2)` —
  Ramer-Douglas-Peucker untuk membuang titik redundan, lalu Chaikin corner-cutting
  2 iterasi untuk menghaluskan. Lalu simpan ke file teks (lihat
  [data-model.md](data-model.md)).
- **Penghapus:** `eraseStrokeAt` memakai `StrokeOptimizer.isStrokeIntersectingPoint`
  dengan ambang default 30f — seluruh goresan yang bersentuhan dihapus (bukan
  sebagian).
- Undo/redo goresan memakai tumpukan terpisah dari undo item (ADR-007).
- Goresan digambar di atas item kanvas, dalam ruang kanvas yang sama.

## Auto-arrange

`autoArrangeGrid(layoutType, density)` — hanya item yang tidak di-pin, diurutkan
berdasarkan `id`. Semua item diskalakan dulu agar tinggi visualnya seragam
(`260dp × density`), lalu:

| Layout | Pola |
|---|---|
| `GRID` | Baris × kolom; kolom 2/3/4 menurut jumlah (≤2→N, ≤4→2, ≤9→3, >9→4) |
| `STRIP` | Satu baris horizontal |
| `COLUMN` | Satu kolom vertikal (penyeragaman menurut lebar) |
| `RADIAL` | Lingkaran; radius = 350×density + 25×density per item |
| `SPREAD` | Spiral Fermat (sudut emas 137.5°), rotasi deterministik −15°…+15° |

Semua layout mereset rotasi ke 0 kecuali SPREAD. Jarak antar item memakai
`arrangeSpacing` (default 60, dari SharedPreferences).

## Kamera: reset, fit, center

- `resetZoom()`: skala 1, translate 0.
- `fitToContent(viewport, density)`: bounding box semua item → skala pas
  (dibatasi 0.01–3.0) dengan padding 100px, kamera dipusatkan ke isi.
- `centerOnItem(...)`: animasi 350 ms, ease-out kubik, menggeser translate agar
  item tepat di tengah viewport (skala tidak diubah). Dibatalkan otomatis begitu
  pengguna mengubah kamera manual.

## Values study (posterisasi)

Pipeline: `ValuesStudyPanel` → ViewModel `scheduleValuesProcessing` →
`ValueProcessor.processImage` → `ImageValueProcessor.processImage` (Java).

1. Diproses dari **thumbnail** (maks 400px), bukan gambar penuh.
2. Debounce 150 ms; konfigurasi yang sama tidak diproses ulang (signature
   `path:simplicity:stopsCount:stopsJson`); satu job per item (job lama dibatalkan).
3. Bila `simplicity > 0`: box blur paralel (radius = simplicity / 4), dibagi per
   baris/kolom antar core CPU.
4. Luminance Rec.709 (`0.2126R + 0.7152G + 0.0722B`) → petakan ke stop terdekat →
   warna stop. Hasil: bitmap hasil + histogram 256 + distribusi % per stop.
5. Stop default: 2–8 stop (`generateDefaultStops`), posisi `1/n … 1`, warna
   grayscale merata; serialisasi `posisi:warna;…` di `stopsJson`.
6. Hasil disimpan di memori (`_valuesResults`) dan dibersihkan saat item dihapus
   atau values dimatikan. Tidak ada cache di disk.

## Crop

UI: `CropImageDialog` (rasio Free, 1:1, 4:3, 16:9, 3:2; handle seret di tepi +
sudut; batas dalam koordinat normalisasi 0–1). Logika: `cropCanvasItem`
(`DroidCanvasViewModel.kt`): koreksi rotasi EXIF → decode → potong → tulis berkas
baru (JPEG 95, thumbnail 400px) → item menunjuk ke berkas baru dengan posisi
disesuaikan offset crop yang dirotasi. Berkas asli dipertahankan untuk undo
(ADR-011).

## Yang BELUM ada di mesin kanvas

- **Undo/redo lewat gesture** (misalnya swipe): tidak ada — undo/redo hanya lewat
  tombol (lihat [roadmap.md](roadmap.md)).
- **Grayscale toggle**: tidak ada (lihat ADR-010).
- **Export/import proyek**: tidak ada.
- **Multi-touch pada item** saat resize bersamaan: pointer item menolak input bila
  `globalIsMultiTouch` atau sedang resize.
- **Batas kanvas**: tidak ada — kanvas benar-benar tak terbatas; `fitToContent`
  satu-satunya yang mengembalikan pandangan.
