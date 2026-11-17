# UI (Compose)

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`
> Sumber utama: `app/src/main/java/com/example/ui/DroidCanvasScreen.kt` (6.413 baris),
> `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`), `MainActivity.kt`.

## Kerangka layar

`DroidCanvasScreen(viewModel)` (`DroidCanvasScreen.kt:230-2930`) — satu composable
raksasa yang berisi seluruh layar. Kerangkanya:

```
BoxWithConstraints
└─ ModalNavigationDrawer                    # hanya layar sempit (< 720dp)
   └─ Scaffold
      └─ Row
         ├─ Sidebar (301dp)                # hanya layar lebar (≥ 720dp) + tidak fullscreen
         │   └─ SidebarContent
         └─ Box (kanvas, weight 1f)
             ├─ latar: grid/slate + dots   # digambar inline, bukan file terpisah
             ├─ gesture latar              # pan/zoom/fling/tap (lihat canvas-engine.md)
             ├─ CanvasItemView per item
             ├─ goresan (selesai + aktif)
             ├─ top bar / bottom bar / FAB
             └─ dialog: buat papan, kelola papan, pengaturan, crop, …
```

- **Breakpoint tablet adalah `maxWidth >= 720.dp`.** Layar lebar: sidebar tetap
  301dp di kiri. Layar sempit: sidebar menjadi drawer (gesture drawer dimatikan
  saat fullscreen). Tidak ada layout khusus tablet selain breakpoint ini —
  "tablet optimization" di roadmap pada dasarnya baru sebatas ini.
- **Fullscreen** menyembunyikan sidebar (state lokal `isFullscreenMode`).
- **State lokal vs ViewModel:** ViewModel memegang data (boards, item, seleksi,
  goresan, pengaturan, kamera). Screen memegang state UI sesaat dengan `remember`:
  visibilitas dialog (~10 boolean), `cropTargetItem`, ukuran viewport, jumlah
  pointer, `flingJob`, `isSidebarExpanded`, `isFullscreenMode`. Dialog papan
  (buat/ganti nama/kelola) memakai state lokal yang memanggil fungsi ViewModel.

## Inventaris composable

| Composable | Baris | Peran |
|---|---|---|
| `DroidCanvasScreen` | 230–~2930 | Seluruh layar: drawer/scaffold, kanvas, gesture, top/bottom bar, dialog |
| `adjustPositionForScale` | 2930–2955 | Util posisi saat resize (penduplikasi `CanvasMathHelper` — lihat G2) |
| `CanvasItemView` | 2956–3630 | Satu item: gambar Coil (thumbnail/full adaptif), seleksi, drag, handle sudut, menu long-press |
| `ZoomPercentageText` | 3631–3647 | Indikator zoom |
| `DrawingToolbar` | 3648–3806 | Toolbar gambar: pen/penghapus, warna, lebar stroke, undo/redo, bersihkan |
| `SidebarSectionHeader` | 3807–3863 | Header seksi sidebar |
| `SidebarContent` | 3869–5477 | Sidebar 3 tab (lihat bawah), 301dp |
| `ValuesStudyPanel` | 5478–5812 | Panel values study per item |
| `CropImageDialog` | 5850–6308 | Dialog crop (rasio + handle seret) |
| `CropRatioChip` | 6309–6335 | Chip pilihan rasio |
| `CornerHandleIndicator` | 6337–6413 | Indikator handle sudut crop |
| `Modifier.customShadow` | 5812–5849 | Util bayangan kustom |

Hanya ada **11 `@Composable`** di file 6.413 baris — rata-rata ~580 baris per composable.
Ini temuan utamanya: pemecahan berikutnya harus memecah isi composable, bukan
menambah composable baru di file yang sama (lihat [architecture.md](architecture.md)
bagian target).

## Sidebar — 3 tab

`SidebarTab` (`DroidCanvasScreen.kt:3864`): `BOARD`, `DRAW`, `LAYERS`.

- **BOARD (4034–4466):** pilih papan (kapsul + dropdown aksi), daftar papan
  (buat/ganti nama/hapus), impor gambar, auto-arrange (5 layout), latar, zoom,
  pengaturan (tema, dynamic color, pitch black, haptic, snap, double-tap target).
- **DRAW (4466–4778):** mode gambar — pen vs penghapus, palet warna, lebar brush,
  undo/redo goresan, bersihkan goresan.
- **LAYERS (4778–5477):** daftar item papan (diurutkan zIndex), indikator item
  aktif, aksi per item (pin, flip, values, crop, duplikat, bawa ke depan/belakang,
  hapus), multi-select.

## Panel & dialog penting

- **ValuesStudyPanel:** membaca `valuesResults[item.id]`; slider simplicity,
  editor stops (tambah/geser/hapus stop, jumlah 2–8), histogram + distribusi %,
  toggle values per item. Perubahan ditulis in-memory dulu, commit saat selesai
  (ADR-006).
- **CropImageDialog:** pratinjau gambar penuh via Coil; kotak crop dengan handle
  di 4 sisi + 4 sudut (drag `detectDragGestures`); rasio Free/1:1/4:3/16:9/3:2;
  tombol reset rasio; konfirmasi memanggil `viewModel.cropCanvasItem`.
- **Menu konteks item (long-press):** panel aksi bawah mengambang — ganti long-press
  menu lama (riwayat: `107ca8f`).
- **Top/bottom bar:** undo/redo (ikon aktif menurut `canUndo`/`canRedo`),
  indikator zoom, penghitung seleksi, tombol values, hapus, bersihkan seleksi,
  tambah gambar (FAB dengan animasi spring saat masuk).

## Tema

`MyApplicationTheme` (`Theme.kt:128-159`):

- Dynamic color (Material You) bila aktif dan SDK ≥ 31; bila tidak, skema **Mono**
  (terang/gelap). Opsi pitch black menimpa background/surface menjadi `0xFF000000`.
- Skema **Green** dan **Purple** didefinisikan lengkap tapi **tidak dipakai** —
  kode mati (lihat G4).
- `Color.kt` berisi token Purple (template), Green (light/dark), Mono (light/dark).
- `Type.kt` hanya mengubah `bodyLarge` (16sp/24sp); gaya teks lain masih default
  Material 3.
- Pilihan tema pengguna (`themeMode`: system/light/dark) disimpan di
  SharedPreferences dan diterapkan di `MainActivity` termasuk warna system bar
  (edge-to-edge).

## Integrasi sistem (MainActivity)

- **Coil:** `ImageLoader` kustom dengan `ImageDecoderDecoder` (SDK ≥ 28) atau
  `GifDecoder` (di bawah itu) — ini yang membuat GIF beranimasi.
- **Share intent:** `ACTION_SEND` / `ACTION_SEND_MULTIPLE` dengan `image/*`
  → `viewModel.addImages(...)`. Juga saat activity sudah hidup (`onNewIntent`).
- **Edge-to-edge** + warna system bar mengikuti tema (gelap/terang).
- **Manifest:** izin `INTERNET` (impor URL + Coil) dan `REQUEST_INSTALL_PACKAGES`;
  `FileProvider` (otoritas `${applicationId}.fileprovider`) dengan `file_paths.xml`
  yang hanya mengekspos `external-files-path/downloads/` — perhatikan: file gambar
  di `filesDir` internal **tidak** diekspos lewat provider ini.
- Backup: `allowBackup=true` dengan `backup_rules.xml` /
  `data_extraction_rules.xml` yang masih template kosong (semua aturan dikomentari).
