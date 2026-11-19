# Model Data

> **Terakhir ditinjau:** 2026-11-19 · **HEAD:** `c37d79d`
> Sumber: `app/src/main/java/com/example/data/DroidCanvasDatabase.kt` (424 baris),
> `ImageStorageHelper.kt` (189 baris), `DroidCanvasViewModel.kt` (strokes & prefs).

## Room — `droid_canvas_database`, versi 11

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
| `isGrayscale` | INTEGER | Grayscale per item (tombol di tab LAYERS; ditambah lagi migrasi 8–9) |
| `mapX`, `mapY` | REAL, default 0 | Posisi node di Map (migrasi 9–10; 0,0 = belum diposisikan, sentinel grid) |
| `note` | TEXT, default `''` | Catatan per item, hanya tampil di Map (migrasi 9–10) |

Catatan: kolom `isGrayscale` sempat dihapus di migrasi 7–8 (ADR-010) lalu
ditambahkan lagi di migrasi 8–9 saat fitur grayscale dibangun (2026-11-17).
Tidak ada `createdAt`/`updatedAt` di item — urutan mengandalkan `zIndex` dan `id`.

### Tabel `edges` (migrasi 9–10, Map)

FK `boardId → boards.id`, `fromItemId → canvas_items.id`,
`toItemId → canvas_items.id`, semuanya `ON DELETE CASCADE`; indeks di ketiga
kolom FK.

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `boardId` | INTEGER | Papan pemilik |
| `fromItemId`, `toItemId` | INTEGER | Dua ujung yang dihubungkan (tanpa arah) — id gambar ATAU id todo, tergantung kind |
| `type` | TEXT | `ConnectionType.name` — 8 jenis (lihat `designs/map.md` K3) |
| `fromKind`, `toKind` | TEXT, default `'ITEM'` | `ITEM`/`TODO` (migrasi 11–12, Todo T5) — FK ke `canvas_items` dibuang karena FK SQLite tidak bisa bersyarat; hapus berantai di repository (transaksi, dibatasi kind) |

Hanya dari/ke/jenis yang disimpan; geometri kurva diturunkan saat render dari
posisi node (K5).

### Tabel `todos` (migrasi 10–11, Todo berbasis kanvas; bentuk baru K11 di migrasi 12–13)

FK `boardId → boards.id` `ON DELETE CASCADE`; indeks di `boardId`.
Satu jenis objek lagi di kanvas, sejajar gambar & goresan (langkah 6, T1) —
bukan mode/tool/tampilan. Lihat `designs/todo.md`. **K11 (2026-11-19):**
satu todo = **judul + daftar baris** — judul cuma kepala (tidak bisa
dicentang); baris-barisnya hidup di `todo_items`.

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `boardId` | INTEGER | Papan pemilik |
| `title` | TEXT, default `''` | Judul (dulu `text` — diganti di migrasi 12–13; todo lama direset, tidak dimigrasi) |
| `posX`, `posY` | REAL, default 0 | Posisi di Board (ruang kanvas) — titik tengah kartu |
| `mapX`, `mapY` | REAL, default 0 | Posisi node di Map (0,0 = belum diposisikan, sentinel grid seperti item) |
| `createdAt` | INTEGER | `System.currentTimeMillis()`; urutan load `ORDER BY createdAt ASC` |

### Tabel `todo_items` (migrasi 12–13, K11)

FK `todoId → todos.id` `ON DELETE CASCADE`; indeks di `todoId`. Satu
baris dalam daftar todo (lihat `designs/todo.md` K11).

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `todoId` | INTEGER | Todo pemilik |
| `text` | TEXT, default `''` | Isi baris |
| `done` | INTEGER, default 0 | Selesai/belum; selesai = coret + pudar (P2, per baris) |
| `createdAt` | INTEGER | `System.currentTimeMillis()`; urutan baris `ORDER BY createdAt ASC` |

### DAO — `DroidCanvasDao`

- Boards: `getAllBoards()` (Flow, `ORDER BY createdAt ASC`), `getBoardById`,
  insert (`REPLACE`), update, delete.
- Items: `getItemsForBoard(boardId)` (Flow, `ORDER BY zIndex ASC`), insert
  (`REPLACE`), update, delete, `getMaxZIndex(boardId)`.
- Edges (`EdgeDao`): `getEdgesForBoard(boardId)` (Flow), insert (`REPLACE`),
  delete, `deleteEdgesForItem`, `deleteEdgesForBoard` (dua terakhir cadangan —
  penghapusan jalan lewat FK CASCADE, lihat `designs/map.md` fase 5).
- Todos (`TodoDao`, migrasi 10–11; bentuk K11 di migrasi 12–13):
  `getTodosForBoard(boardId)` (Flow, `ORDER BY createdAt ASC`), insert
  (`REPLACE`), update, delete, `deleteTodosForBoard` (cadangan —
  penghapusan jalan lewat FK CASCADE); baris: `getTodoItemsForBoard(boardId)`
  (Flow, subquery todo per papan, `ORDER BY createdAt ASC`), insert/update/
  delete per `TodoItem`.
- Tidak ada query pencarian, tidak ada transaksi multi-operasi — `restoreSnapshot`
  (undo) melakukan delete/insert satu per satu.

### Repository — `DroidCanvasRepository`

Pembungkus tipis satu-lapis di atas DAO, tanpa logika tambahan (juga membungkus
`EdgeDao` + `TodoDao`, konstruktor kedua, ketiga, dan keempat — `database`,
untuk transaksi hapus berantai edge di `deleteCanvasItem`/`deleteTodo`).
Semua akses Room dari ViewModel berjalan di `Dispatchers.IO`.

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
| 8 → 9 | `canvas_items.isGrayscale` ditambahkan lagi (fitur grayscale, 2026-11-17) |
| 9 → 10 | `canvas_items.mapX/mapY/note` + tabel `edges` (Map, 2026-11-18) |
| 10 → 11 | Tabel `todos` (Todo berbasis kanvas, 2026-11-19) |
| 11 → 12 | Tabel `edges` dibuat ulang: `fromKind`/`toKind`, FK ke `canvas_items` dibuang (Todo T5, 2026-11-19) |
| 12 → 13 | Tabel `todos` dibuat ulang (tanpa `text`/`done`, `title` saja — todo lama direset) + tabel `todo_items` (K11, 2026-11-19) |

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
- Juga ditulis oleh `ProjectOpener` saat Open, isinya dari manifest arsip.
- Dibaca saat papan dipilih (`loadDrawingStrokes`); baris rusak dilewati diam-diam.
- Optimasi diterapkan sebelum simpan: RDP epsilon 0.8 + Chaikin 2 iterasi.
- Tidak ada migrasi format — mengubah format berarti memutus goresan lama.

## SharedPreferences — `canvas_view_prefs`

| Kunci | Isi |
|---|---|
| `board_scale_<id>`, `board_trans_x_<id>`, `board_trans_y_<id>` | Posisi kamera per papan (debounce 1 detik) |
| `map_scale_<id>`, `map_trans_x_<id>`, `map_trans_y_<id>` | Posisi kamera Map per papan (debounce 1 detik) |
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

## Save/Open — berkas proyek `.dc` (Fase 1–5, 2026-11-19)

Format + rancangan: `designs/save-open.md`, skema formal
`designs/manifest-schema.md` + contoh `designs/manifest-example.json`.

- **Arsip:** ZIP berisi `manifest.json` di root +
  `images/<uuid>_full.<ext>` + `images/<uuid>_thumb.jpg` + (opsional)
  `edges`, `todos`, `strokes`. `formatVersion` harus 1; kolom mati `Board` tidak ikut
  (K1); satu berkas = satu papan (K2); Open selalu papan baru (K4); values
  study dihitung ulang saat Open (K5) — bitmap values TIDAK disimpan di arsip.
- **`ProjectSaver`** (`data`, kelas murni): Board + items + edges + todos + goresan →
  ZIP. Satu UUID baru per item per Save, dipakai sebagai stem nama berkas
  arsip; satu UUID baru per todo per Save sebagai kunci join edge + baris.
  Gagal sebelum menulis apa pun bila edge merujuk item/todo yang tidak ada
  / kind tak dikenal / type tak dikenal / gambar sumber hilang.
- **`ProjectOpener`** (`data`, kelas murni): validasi 9 aturan skema → salin
  gambar ke `filesDir` dengan nama `ref_full_<uuid baru>.<ext>` /
  `ref_thumb_<uuid baru>.jpg` → insert Board + items + todos + edges dalam **satu
  transaksi Room** (`database.withTransaction`) → tulis
  `drawing_strokes_<newBoardId>.txt` (format teks sama seperti
  `saveDrawingStrokes`). Mengembalikan `OpenedProject(boardId, boardCamera,
  mapCamera)`; TIDAK menyentuh SharedPreferences. Gagal = rollback + berkas
  staged dihapus.
- **ViewModel (Save ulang, 2026-11-19):** `requestSave()` / `requestSaveAs()` /
  `requestShare()` + dialog penamaan (`saveNameDialog`); `saveProject(nama,
  shareAfter)` menulis ZIP ke temp `cacheDir` lalu ke
  `Download/droidcanvas` lewat `ProjectFileStore` (MediaStore API 29+,
  File API di bawahnya; nama sama = timpa) + `saveState` (`SaveState`:
  `Saved` membawa nama berkas + `shareAfter`); target per papan di prefs
  (`save_file_<boardId>`). `openProject(uri)` menyalin URI ke temp
  `open-<uuid>.dc` di `cacheDir` (dihapus di `finally`), memanggil
  Opener, menulis kedua kamera + target (dari DISPLAY_NAME berkas asal)
  ke prefs **sebelum** `selectBoard` (yang membaca kamera dari prefs),
  lalu `openState` (`OpenState`). ViewModel menerima objek
  `DroidCanvasDatabase` lewat factory (ADR-016) — repository tidak
  diubah. UI: tombol Save/Save As/Open/Share di tab BOARD, seksi PROJECT
  FILE; Open lewat `ACTION_OPEN_DOCUMENT` (MIME `application/zip` +
  `application/octet-stream`); berbagi hanya lewat Share (salinan
  `cacheDir` + `FileProvider`, `cache-path project_cache`, T5).
- **Test:** `ProjectSaverTest` 5/5 + `ProjectOpenerTest` 7/7 terbukti hijau
  (round-trip, 5 penolakan, pra-Map); 2 test baru T7 (buka-dua-kali) + T8
  (papan 20 gambar) ditambah 2026-11-19, belum dieksekusi —
  `:app:testDebugUnitTest`, JDK 21 (Robolectric sdk 36 menolak JDK 17).
