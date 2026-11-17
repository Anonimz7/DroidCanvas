# Model Data

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`
> Sumber: `app/src/main/java/com/example/data/DroidCanvasDatabase.kt` (232 baris),
> `ImageStorageHelper.kt` (189 baris), `DroidCanvasViewModel.kt` (strokes & prefs).

## Room — `droid_canvas_database`, versi 8

`exportSchema = false` (tidak ada histori skema JSON) dan
`fallbackToDestructiveMigration()` aktif — migrasi yang tidak dikenal akan
**menghapus seluruh database** (lihat G12). Database dibuat singleton
double-checked locking di `DroidCanvasDatabase.getDatabase()`.

### Tabel `boards`

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `name` | TEXT | Nama papan |
| `createdAt` | INTEGER | `System.currentTimeMillis()` |
| `backgroundType` | TEXT, default `'SLATE'` | Ditambah migrasi 1–2; **tidak pernah dibaca UI** (lihat G10) |
| `canvasScale` | REAL, default 1.0 | Ditambah migrasi 2–3; **tidak pernah dipakai** — posisi kamera tinggal di SharedPreferences (ADR-004) |
| `canvasTranslateX/Y` | REAL, default 0.0 | Sama seperti di atas |

### Tabel `canvas_items`

FK `boardId → boards.id` dengan `ON DELETE CASCADE`, indeks di `boardId`.

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `boardId` | INTEGER | Papan pemilik |
| `fullPath` | TEXT | Path `filesDir` ke gambar asli |
| `thumbPath` | TEXT | Path `filesDir` ke thumbnail |
| `posX`, `posY` | REAL | Posisi dalam **ruang kanvas** (bukan layar) |
| `width`, `height` | REAL | Dimensi native/asli (px gambar) |
| `scale` | REAL, default 1 | Faktor skala visual; 0.05–10 saat diubah |
| `rotation` | REAL, default 0 | Derajat |
| `zIndex` | INTEGER, default 0 | Urutan gambar; `ORDER BY zIndex ASC` saat load |
| `isPinned` | INTEGER | Item tidak bisa dipindah/diubah; dikecualikan dari auto-arrange |
| `flipHorizontal`, `flipVertical` | INTEGER | Flip visual |
| `isValuesEnabled` | INTEGER | Values study aktif untuk item ini |
| `simplicity` | INTEGER, default 0 | 0–100; radius blur = simplicity / 4 |
| `stopsCount` | INTEGER, default 3 | 2–8 |
| `stopsJson` | TEXT | Stop values study, format `posisi:warna;posisi:warna` |

Catatan: tidak ada kolom `isGrayscale` (lihat ADR-010). Tidak ada `createdAt`/
`updatedAt` di item — urutan mengandalkan `zIndex` dan `id`.

### DAO — `DroidCanvasDao`

- Boards: `getAllBoards()` (Flow, `ORDER BY createdAt ASC`), `getBoardById`,
  insert (`REPLACE`), update, delete.
- Items: `getItemsForBoard(boardId)` (Flow, `ORDER BY zIndex ASC`), insert
  (`REPLACE`), update, delete, `getMaxZIndex(boardId)`.
- Tidak ada query pencarian, tidak ada transaksi multi-operasi — `restoreSnapshot`
  (undo) melakukan delete/insert satu per satu.

### Repository — `DroidCanvasRepository`

Pembungkus tipis satu-lapis di atas DAO, tanpa logika tambahan. Semua akses Room
dari ViewModel berjalan di `Dispatchers.IO`.

### Riwayat migrasi

| Migrasi | Perubahan |
|---|---|
| 1 → 2 | `boards.backgroundType` |
| 2 → 3 | `boards.canvasScale/TranslateX/TranslateY` |
| 3 → 4 | `canvas_items.isPinned` |
| 4 → 5 | `canvas_items.flipHorizontal/flipVertical` |
| 5 → 6 | `canvas_items.isGrayscale` (kemudian **dihapus** di 7–8) |
| 6 → 7 | `canvas_items.isValuesEnabled/simplicity/stopsCount/stopsJson` |
| 7 → 8 | Tabel `canvas_items` dibuat ulang (salin → drop → rename) tanpa `isGrayscale` |

## Berkas gambar — `filesDir`

Ditulis oleh `ImageStorageHelper.importImage()`:

- **Nama:** `ref_full_<uuid>.<ext>` (jpg/png/webp/gif) dan `ref_thumb_<uuid>.jpg`.
- **GIF dipertahankan utuh** (disalin mentah, frame animasi utuh; thumbnail = salinan
  file GIF itu sendiri).
- **Non-GIF:** berkas asli disalin mentah (kualitas 100%); rotasi EXIF (90/180/270)
  hanya diterapkan ke thumbnail + penukaran width/height, bukan ke berkas full.
- **Thumbnail:** downsample ke maks 400px (inSampleSize pangkat 2), JPEG kualitas 90.
- **Impor dari URL http(s)** didukung langsung (timeout 10 detik) — satu-satunya
  jaringan di luar Coil.
- **Gagal decode bounds** → fallback dimensi 800×600.
- **Duplikasi item** menyalin kedua berkas dengan UUID baru, offset posisi +40px.
- **Penghapusan berkas** hanya terjadi di `deleteBoard`. Hapus item / bersihkan papan
  sengaja tidak menghapus berkas (ADR-012) — file yatim menumpuk (lihat G5).

## Goresan — file teks per papan

`drawing_strokes_<boardId>.txt` di `filesDir`. Satu baris per goresan:

```
<uuid>|<warna-int>|<lebar>|<x,y;x,y;...>
```

- Ditulis utuh setiap kali goresan selesai/dihapus (`saveDrawingStrokes`).
- Dibaca saat papan dipilih (`loadDrawingStrokes`); baris rusak dilewati diam-diam.
- Optimasi diterapkan sebelum simpan: RDP epsilon 0.8 + Chaikin 2 iterasi.
- Tidak ada migrasi format — mengubah format berarti memutus goresan lama.

## SharedPreferences — `canvas_view_prefs`

| Kunci | Isi |
|---|---|
| `board_scale_<id>`, `board_trans_x_<id>`, `board_trans_y_<id>` | Posisi kamera per papan (debounce 1 detik) |
| `is_snap_to_grid`, `snap_step_size` (40f) | Snap ke grid |
| `arrange_spacing` (60f) | Jarak antar item auto-arrange |
| `grid_style` (`"none"`) | Gaya grid latar |
| `is_haptic_enabled` (true) | Haptic |
| `double_tap_zoom_target` (2.3f) | Target zoom double-tap |
| `theme_mode` (`"system"`), `dynamic_color_enabled` (true), `pitch_black_enabled` (false) | Tema |

## State yang hanya hidup di memori (tidak persisten)

- `_itemOverrides` — bayangan item selama gesture (ADR-006).
- `_valuesResults: Map<itemId, ValuesResult>` — bitmap hasil posterisasi + histogram
  + distribusi; dihitung ulang dari thumbnail setiap kali konfigurasi berubah.
- `decodedBitmapCache` — LRU 5 bitmap (hanya dipakai values processing).
- Undo/redo stacks (maks 50 snapshot) — hilang saat proses mati.
- `activeStroke` — goresan yang sedang digambar.
