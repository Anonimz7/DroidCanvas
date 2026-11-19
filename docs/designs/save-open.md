# Rancangan: Save/Open Project File

> **Status:** Rancangan — keputusan K1–K6 disetujui 2026-11-19 (sesuai rekomendasi); Fase 1 (T2) + Fase 2 (T3) selesai 2026-11-19, belum di-push ·
> Dinamai ulang Export/Import → Save/Open 2026-11-19 (keputusan user;
> analogi Photoshop: Save/Open = berkas kerja, Export = hasil JPG/PNG) ·
> **Terakhir ditinjau:** 2026-11-19 · **HEAD:** `7f01519` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 1 · [gaps.md](../gaps.md) G5, G20 · [data-model.md](../data-model.md)

## 1. Tujuan

Satu papan (gambar + posisi + goresan) bisa disimpan jadi satu berkas,
dipindah ke perangkat lain, dan dibuka kembali utuh. Hari ini tidak ada
jalan keluar-masuk untuk isi papan sama sekali: satu-satunya "export" yang
ada adalah `exportSchema = false` di Room, dan itu arti yang berbeda.

## 2. Keadaan sekarang (fakta kode)

- `Board`: `id, name, createdAt, backgroundType, canvasScale,
  canvasTranslateX, canvasTranslateY`
  (`DroidCanvasDatabase.kt:21-30`). Tiga kolom kamera + `backgroundType`
  adalah kolom mati (G3/G10) — posisi kamera hidup di SharedPreferences
  (`canvas_view_prefs`), bukan di sini.
- `CanvasItem`: 16 kolom (`DroidCanvasDatabase.kt:44-64`) — `boardId`,
  `fullPath`, `thumbPath`, `posX/Y`, `width/height`, `scale`, `rotation`,
  `zIndex`, `isPinned`, `flipHorizontal/Vertical`, `isValuesEnabled`,
  `simplicity`, `stopsCount`, `stopsJson`.
- Berkas gambar di `filesDir`: `ref_full_<id>.<ext>` + `ref_thumb_<id>.jpg`
  (`ImageStorageHelper.kt:73-74`). `fullPath`/`thumbPath` menyimpan path
  absolut — tidak bisa dibawa mentah ke perangkat lain.
- Goresan di `drawing_strokes_<boardId>.txt`, format per baris
  `id|color|width|x,y;x,y;…` (`DroidCanvasViewModel.kt:455-469`).
  Tidak ada validasi dan tidak ada migrasi format (G6).
- `FileProvider` sudah terdaftar (`AndroidManifest.xml:18-26`) tapi
  `file_paths.xml` hanya mengekspos `external-files-path` `downloads/`
  (G20) — `filesDir` dan `cacheDir` tidak terekspos.
- Impor gambar sudah ada (`PickMultipleVisualMedia` + share intent
  `SEND_MULTIPLE`), tapi itu impor gambar, bukan impor proyek.
- Undo/redo adalah snapshot seluruh daftar item (`DroidCanvasViewModel.kt:127-128`).

## 3. Keputusan (disetujui 2026-11-19, sesuai rekomendasi)

| # | Keputusan | Pilihan | Keputusan |
|---|---|---|---|
| K1 | Kolom mati ikut manifest? | Ikut / buang | **Buang**, kecuali keputusan kolom mati (G3/G10) bilang dipakai. Manifest hanya berisi data yang hidup. |
| K2 | Satu berkas = satu papan? | Satu papan / banyak papan | **Satu papan** untuk v1. Multi-papan = v2. |
| K3 | Nama/ekstensi berkas | `.droidcanvas` (ZIP) / `.zip` | **`.droidcanvas`** — ZIP dengan nama sendiri, supaya bisa dibuka dari file manager. |
| K4 | Konflik saat Open | Selalu papan baru / timpa bila nama sama | **Selalu papan baru** (id baru). Tanpa dialog konflik di v1. |
| K5 | Bitmap values study ikut disimpan? | Ikut / hitung ulang | **Hitung ulang** saat Open — itu data turunan (`valuesResults` di ViewModel), bukan data papan. |
| K6 | Versi format tak dikenal | Tolak dengan pesan / coba baca | **Tolak dengan pesan yang jelas.** |

## 4. Rancangan

### 4.1 Format: satu arsip ZIP

```
nama-papan.droidcanvas
├── manifest.json
└── images/
    ├── <item-uuid>_full.<ext>
    └── <item-uuid>_thumb.jpg
```

`manifest.json` (v1):

```json
{
  "formatVersion": 1,
  "appVersion": "0.2.0",
  "board": { "name": "…", "createdAt": 0,
             "camera": { "scale": 1, "translateX": 0, "translateY": 0 },
             "mapCamera": { "scale": 1, "translateX": 0, "translateY": 0 } },
  "items": [
    { "uuid": "…", "posX": 0, "posY": 0, "width": 0, "height": 0, "scale": 1,
      "rotation": 0, "zIndex": 0, "isPinned": false,
      "flipHorizontal": false, "flipVertical": false,
      "isValuesEnabled": false, "simplicity": 0, "stopsCount": 3,
      "stopsJson": "", "isGrayscale": false,
      "mapX": 0, "mapY": 0, "note": "",
      "fullFile": "images/<uuid>_full.jpg", "thumbFile": "images/<uuid>_thumb.jpg" }
  ],
  "edges": [
    { "from": "<uuid>", "to": "<uuid>", "type": "RELATED_TO" }
  ],
  "strokes": [
    { "id": "…", "color": 0, "width": 8,
      "points": [[0, 0], [1, 1]] }
  ]
}
```

Skema formal + contoh lengkap: [`designs/manifest-schema.md`](manifest-schema.md) dan [`designs/manifest-example.json`](manifest-example.json) (Fase 1, T2).

- `camera` di manifest diisi dari SharedPreferences + ViewModel saat
  Save (bukan dari kolom mati `Board`). Kalau K1 memutuskan kolom mati
  dipakai, sumbernya berubah — formatnya tidak berubah.
- Data Map (fase 5 Map, 2026-11-18 — lihat `designs/map.md` T9/O3):
  - `uuid` per item: kunci stabil di dalam arsip — uuid yang sama dengan
    stem nama berkas gambar (`<uuid>_full.<ext>`). Dipakai HANYA sebagai
    kunci join di dalam arsip; id baris DB yang baru selalu dibuat saat
    Open (aturan §4.2 tetap: id dari arsip tidak pernah dipakai mentah
    sebagai id baris). Bila stem nama berkas adalah id DB lama, ia tetap
    hanya kunci join, bukan id baru.
  - `edges`: `from`/`to` = uuid item, `type` = `ConnectionType.name`
    (mis. `RELATED_TO`, `SUPPORTS` — 8 jenis, lihat
    `ConnectionType.kt`). Validasi Open: tiap ujung harus merujuk ke
    item yang ada di manifest dan `type` harus dikenal — bila tidak,
    arsip ditolak (K6).
  - `mapX`/`mapY`/`note`: posisi node + catatan per item (O1/O2 Map).
    `mapX`/`mapY` = 0 berarti "belum diposisikan" (sentinel grid Map) —
    Open mempertahankan 0 apa adanya.
  - `mapCamera`: kamera Map, dari SharedPreferences `canvas_view_prefs`
    (`map_scale/trans_x/trans_y_$boardId`) — sumber yang sama persis
    dengan `camera` Board (`board_scale/trans_x/trans_y_$boardId`).
    Ikut karena kamera Board ikut: menyimpan satu kamera saja akan
    memulihkan tampilan Board tapi me-reset tampilan Map saat Open.
    Bukan dari kolom DB (tidak ada kolom kamera Map di DB; kolom kamera
    `Board` sendiri mati — K1).
  - Kompatibilitas mundur: arsip lama (pra-Map, tanpa
    `edges`/`mapX`/`mapY`/`note`/`mapCamera`) terbuka bersih — tanpa
    edge, `mapX`/`mapY`=0, `note`="", `mapCamera` default (skala 1,
    offset 0). Semua field Map opsional; `formatVersion` tetap 1
    (penambahan field opsional bukan versi format baru; K6 hanya menolak
    `formatVersion` yang tak dikenal). Sebaliknya, arsip baru yang
    dibuka aplikasi lama (pra-Map): kunci JSON yang tak dikenal
    diabaikan parser — papan terbuka tanpa data Map.
- `isGrayscale` dicantumkan sekarang supaya format tidak berubah saat
  fitur grayscale jadi (lihat `grayscale.md`).
- `strokes` ditulis dalam bentuk JSON di manifest, bukan menyalin
  `drawing_strokes_<id>.txt` — format teks lama tetap dipakai internal,
  format arsip tidak mewarisi utangnya (G6).
- Path absolut **dilarang** di manifest: semua rujukan berkas adalah nama
  relatif di dalam arsip.

### 4.2 Serializer terpisah (kelas murni)

- Satu kelas `ProjectSerializer` (atau sepasang `ProjectSaver` /
  `ProjectOpener`) di package `data`, tanpa dependensi Room, ViewModel,
  atau Compose — bisa diuji seperti `StrokeOptimizer`.
- Save: baca `Board` + items + strokes dari repository → tulis ZIP ke
  `cacheDir` → kembalikan `File` untuk dibagikan.
- Open: baca ZIP → **validasi dulu** (versi format, manifest lengkap,
  semua berkas gambar ada) → salin gambar ke `filesDir` dengan id baru
  (`ref_full_<newId>`) → insert `Board` + items **dalam satu transaksi
  Room** → tulis `drawing_strokes_<newBoardId>.txt` → bila ada langkah
  gagal, tidak ada papan setengah jadi (hapus berkas yang sudah disalin).
- Aturan Open: `boardId` dan `id` item **selalu dibuat baru**; id dari
  arsip tidak pernah dipakai mentah.
- Data Map saat Open: bangun peta `uuid → id baru` selagi insert item
  (satu item = satu entri peta); setelah semua item ter-insert, insert
  `edges` dengan `fromItemId`/`toItemId` hasil pemetaan — **dalam
  transaksi Room yang sama** dengan Board + items, jadi edge yang
  ujungnya tidak terpetakan membatalkan seluruh Open (bukan papan
  setengah jadi). Validasi dulu (sebelum menyalin apa pun): tiap edge
  merujuk ke uuid item yang ada di manifest dan `type`-nya salah satu
  dari 8 `ConnectionType`; `mapCamera` yang hilang = default (1/0/0),
  bukan galat. Buka berkas yang sama dua kali (T7) tetap menghasilkan
  dua papan baru — peta uuid→id dibangun baru per Open, jadi edge tidak
  pernah tertukar antar papan.

### 4.3 Berbagi berkas

- `file_paths.xml` ditambah `cache-path` (untuk ZIP Save di `cacheDir`)
  dan, bila perlu, `files-path` — hari ini hanya ada `downloads/` (G20).
- Save: `FileProvider.getUriForFile` + `ACTION_SEND`, MIME
  `application/zip`.
- Open: `ACTION_OPEN_DOCUMENT`, MIME `application/zip` +
  `application/octet-stream` (file manager tidak selalu mengenal
  `.droidcanvas`).

### 4.4 UI

- Tombol Save / Open di tab BOARD (di bawah daftar papan, dekat
  "YOUR BOARDS" — `DroidCanvasScreen.kt:2165`) atau di dialog Manage
  Boards. Satu tempat saja, jangan dua-duanya.
- Selama Save/Open: indikator progres; sesudahnya: toast/snackbar
  hasil ("Papan X berhasil disimpan", "Open gagal: versi format tidak
  dikenal").
- Pesan gagal harus menyebut sebabnya (K6), bukan "terjadi kesalahan".

## 5. To-do

| # | Kerja | Target / bukti selesai |
|---|---|---|
| T1 | Putuskan K1–K6 (bagian 3) | Keputusan tercatat di `decisions.md` (ADR-015, 2026-11-19) — **Selesai** |
| T2 | Tulis skema manifest v1 sebagai dokumen (bagian 4.1) | Skema + satu contoh `manifest.json` di `docs/designs/` — **Selesai** (`designs/manifest-schema.md` + `designs/manifest-example.json`, 2026-11-19) |
| T3 | `ProjectSerializer`: Save Board+items+strokes → ZIP | Unit test: round-trip 1 papan (2 gambar + 1 goresan) kembali identik — **Selesai** (Fase 2, `ProjectSaver` + `ProjectSaverTest`, 2026-11-19) |
| T4 | `ProjectOpener`: validasi → salin → transaksi Room | Unit test: versi tak dikenal ditolak; ZIP korup ditolak; gambar hilang ditolak; Open gagal = tidak ada papan baru |
| T5 | `file_paths.xml`: tambah `cache-path` | Berkas ZIP dari `cacheDir` bisa dibuka lewat `FileProvider` |
| T6 | UI Save/Open di tab BOARD + progres + pesan hasil | Uji manual: Save → kirim ke perangkat lain → Open → isi papan sama — **SEBAGIAN (Fase 4, 2026-11-19, sudah di-push remote `132058e`):** tombol Save + progres + pesan hasil selesai (ViewModel `saveProject()` + seksi PROJECT FILE di tab BOARD + berbagi via `FileProvider`/`ACTION_SEND`); berbagi baru jalan setelah `cache-path` (T5, Fase 3); tombol Open menunggu `ProjectOpener` (Fase 3, dikerjakan user terpisah) |
| T7 | Buka berkas yang sama dua kali | Dua papan baru, tidak ada tabrakan id/berkas |
| T8 | Uji manual papan besar (20+ gambar + goresan + values study) | Open selesai tanpa ANR; values study terhitung ulang |
| T9 | Perbarui `roadmap.md` (langkah 1 → selesai), `data-model.md`, `decisions.md` | Dokumen konsisten dengan kode |
| T10 | Data Map ikut format: `edges` + `mapX`/`mapY` + `note` + `mapCamera`; uji round-trip + buka arsip lama | Round-trip 1 papan ber-edge kembali identik (edge, note, posisi Map, kamera Map); arsip pra-Map terbuka bersih tanpa edge |

## 6. Di luar lingkup

- Sinkronisasi cloud / akun — aplikasi ini tanpa backend.
- Save banyak papan sekaligus (v2).
- Ekspor kanvas jadi gambar/PDF — itu fitur lain, bukan proyek.
- Mengubah format teks `drawing_strokes_<id>.txt` internal (G6 tetap
  berdiri sendiri).
