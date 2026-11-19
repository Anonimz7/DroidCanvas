# Rancangan: Save/Open Project File

> **Status:** Rancangan — keputusan K1–K6 disetujui 2026-11-19 (sesuai rekomendasi) ·
> Dinamai ulang Export/Import → Save/Open 2026-11-19 (keputusan user;
> analogi Photoshop: Save/Open = berkas kerja, Export = hasil JPG/PNG) ·
> **Terakhir ditinjau:** 2026-11-19 · **HEAD:** `132058e` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 1 · [gaps.md](../gaps.md) G5, G20 · [data-model.md](../data-model.md)

## Fase pengerjaan

| Fase | Kerja | Status (2026-11-19) |
|---|---|---|
| 1 | Skema manifest (T2) | Selesai & di-push (`f8d487c`) |
| 2 | `ProjectSaver` (T3) | Kode selesai & di-push (`b1e932e`) — **terbukti 2026-11-19:** `ProjectSaverTest` 5/5 hijau di `:app:testDebugUnitTest` (butuh JDK 21 — lihat catatan build di §5.1) |
| 3 | `ProjectOpener` + `file_paths` (T4+T5) | **Selesai 2026-11-19** — `ProjectOpener.kt` + `ProjectOpenerTest.kt` (7/7 hijau) + `cache-path` di `file_paths.xml`, sesuai panduan §5.1; kompilasi + test terbukti (`:app:testDebugUnitTest`, JDK 21) |
| 4 | UI Save/Open (T6) | **Selesai (kode) 2026-11-19:** tombol Save + Open + progres + pesan hasil (ViewModel `openProject()` + launcher `ACTION_OPEN_DOCUMENT` + papan baru langsung aktif dengan kameranya); uji manual end-to-end di Fase 7 |
| 5 | Uji (T7, T8, T10) | **Selesai (kode test) 2026-11-19:** T7 + T8 ditambah ke `ProjectOpenerTest` (total 9 test); T10 sudah terbukti test Fase 3. Eksekusi test baru belum dibuktikan (butuh `:app:testDebugUnitTest`, JDK 21 — lihat catatan T3) |
| 6 | Dokumen penutup (T9) | **Selesai 2026-11-19:** `roadmap.md` langkah 1 + audit, `data-model.md` (seksi Save/Open), `decisions.md` (ADR-016) |
| 7 | Uji perangkat | Siap dikerjakan (Fase 3 selesai 2026-11-19) |

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
| K3 | Nama/ekstensi berkas | `.dc` (ZIP) / `.zip` | **`.dc`** — ZIP dengan nama sendiri, supaya bisa dibuka dari file manager. Diubah 2026-11-19 dari `.droidcanvas` (kependekan, keputusan user); arsip `.droidcanvas` lama tetap bisa dibuka (Opener tidak memeriksa ekstensi). |
| K4 | Konflik saat Open | Selalu papan baru / timpa bila nama sama | **Selalu papan baru** (id baru). Tanpa dialog konflik di v1. |
| K5 | Bitmap values study ikut disimpan? | Ikut / hitung ulang | **Hitung ulang** saat Open — itu data turunan (`valuesResults` di ViewModel), bukan data papan. |
| K6 | Versi format tak dikenal | Tolak dengan pesan / coba baca | **Tolak dengan pesan yang jelas.** |

## 4. Rancangan

### 4.1 Format: satu arsip ZIP

```
nama-papan.dc
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
  `.dc`).

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
| T3 | `ProjectSerializer`: Save Board+items+strokes → ZIP | Unit test: round-trip 1 papan (2 gambar + 1 goresan) kembali identik — **Kode selesai (Fase 2, `ProjectSaver` + `ProjectSaverTest`, 2026-11-19) — TERBUKTI 2026-11-19:** `ProjectSaverTest` 5/5 hijau di `:app:testDebugUnitTest` (JDK 21 + Robolectric sdk 36). Workflow Actions masih hanya `assembleRelease`, tidak menjalankan unit test (lihat G7) |
| T4 | `ProjectOpener`: validasi → salin → transaksi Room | Unit test: versi tak dikenal ditolak; ZIP korup ditolak; gambar hilang ditolak; Open gagal = tidak ada papan baru — **Selesai 2026-11-19 (Fase 3):** `ProjectOpener.kt` + `ProjectOpenerTest.kt` (round-trip penuh + 5 penolakan + arsip pra-Map), 7/7 hijau di `:app:testDebugUnitTest` |
| T5 | `file_paths.xml`: tambah `cache-path` | Berkas ZIP dari `cacheDir` bisa dibuka lewat `FileProvider` — **Selesai 2026-11-19 (Fase 3):** `<cache-path name="project_cache" path="." />`; `files-path` sengaja tidak ditambahkan (aturan keras 8) |
| T6 | UI Save/Open di tab BOARD + progres + pesan hasil | Uji manual: Save → kirim ke perangkat lain → Open → isi papan sama — **SELESAI (kode, Fase 4, 2026-11-19):** tombol Save + Open (ViewModel `saveProject()` + `openProject()` + seksi PROJECT FILE di tab BOARD; Open lewat `ACTION_OPEN_DOCUMENT`, papan baru langsung aktif + kamera dimuat, pesan gagal menyebut sebabnya); berbagi Save jalan (cache-path T5). Uji manual end-to-end menunggu Fase 7 |
| T7 | Buka berkas yang sama dua kali | Dua papan baru, tidak ada tabrakan id/berkas — **Test ditambah 2026-11-19 (Fase 5):** `buka berkas yang sama dua kali` di `ProjectOpenerTest` (2 papan, id + nama berkas berbeda, isi identik, `filesDir` tepat 6 berkas). Eksekusi belum dibuktikan (butuh test run) |
| T8 | Uji manual papan besar (20+ gambar + goresan + values study) | Open selesai tanpa ANR; values study terhitung ulang — **Test skala ditambah 2026-11-19 (Fase 5):** `papan besar 20 gambar` di `ProjectOpenerTest` (20 item + edge + 2 goresan kembali utuh, `filesDir` tepat 41 berkas). ANR + values study terhitung ulang tetap uji perangkat (Fase 7) |
| T9 | Perbarui `roadmap.md` (langkah 1 → selesai), `data-model.md`, `decisions.md` | Dokumen konsisten dengan kode — **Selesai 2026-11-19 (Fase 6):** `roadmap.md` langkah 1 + baris audit, `data-model.md` seksi Save/Open + catatan goresan, `decisions.md` ADR-016 |
| T10 | Data Map ikut format: `edges` + `mapX`/`mapY` + `note` + `mapCamera`; uji round-trip + buka arsip lama | Round-trip 1 papan ber-edge kembali identik (edge, note, posisi Map, kamera Map); arsip pra-Map terbuka bersih tanpa edge — **TERBUKTI 2026-11-19 (test Fase 3):** round-trip penuh + arsip pra-Map di `ProjectOpenerTest`, 7/7 hijau |

### 5.1 Panduan Fase 3 — `ProjectOpener` (T4) + `file_paths` (T5)

> Fase ini dikerjakan user terpisah (2026-11-19). Panduan ini memindahkan
> konteks yang dibutuhkan — baca dulu sebelum menulis kode.
>
> **Status: SELESAI 2026-11-19.** `ProjectOpener.kt` + `ProjectOpenerTest.kt`
> (7/7 hijau) + `cache-path` di `file_paths.xml`; `:app:testDebugUnitTest`
> hijau (19 tes, 0 gagal) dengan JDK 21 + Android SDK 36.
>
> Keputusan implementasi yang diambil (dari pilihan di aturan 2):
> `ProjectOpener.open(input, filesDir, database)` menerima objek
> `DroidCanvasDatabase` dan memakai `withTransaction` (room-ktx) untuk
> transaksi lintas-DAO — repository & `MainActivity` tidak perlu diubah.
> Nama berkas salinan memakai `UUID.randomUUID()` (aturan keras 3), karena id
> DB belum diketahui sebelum insert. Catatan build: (a) Robolectric
> `@Config(sdk = [36])` menolak JDK 17 ("Android SDK 36 requires Java 21") —
> unit test butuh JDK 21, sedangkan workflow Actions memakai JDK 17 (lihat
> G7); (b) `gradle/wrapper/gradle-wrapper.jar` di repo rusak — `./gradlew`
> tidak bisa jalan, build dijalankan lewat Gradle 9.3.1 langsung (lihat G21).

**Baca dulu:**

- `manifest-schema.md` §7 (8 aturan validasi) + §8 (kompatibilitas pra-Map)
- `save-open.md` §4.2 (alur Open) + §4.3 (berbagi berkas)
- `ProjectSaver.kt` — format manifest yang ditulis; pakai ulang
  `ProjectSaver.FORMAT_VERSION` (jangan duplikasi konstanta)
- `DroidCanvasDatabase.kt` — entitas + DAO + `DroidCanvasRepository`
  (catatan: repository saat ini TIDAK memegang objek database)
- `DroidCanvasViewModel.kt`: `DrawingStroke` (±baris 45),
  `saveDrawingStrokes`/`loadDrawingStrokes` (format teks
  `id|color|width|x,y;x,y;…`, berkas `drawing_strokes_<boardId>.txt`),
  kunci kamera di prefs (`board_scale/trans_x/trans_y_$boardId`,
  `map_scale/trans_x/trans_y_$boardId`)
- `file_paths.xml` + `AndroidManifest.xml` (FileProvider, authority
  `${applicationId}.fileprovider`)
- `MainActivity.kt` (±baris 52:
  `DroidCanvasRepository(database.droidCanvasDao(), database.edgeDao())` —
  satu-satunya tempat repository dibuat)
- `ProjectSaverTest.kt` (cara membangun arsip di test) +
  `ExampleRobolectricTest.kt` (pola Robolectric, `@Config(sdk = [36])`)

**Yang dibangun:**

1. `app/src/main/java/com/example/data/ProjectOpener.kt` (package
   `com.example.data`) — kelas murni: tanpa `Context`, ViewModel, atau
   Compose. JSON pakai `org.json` (sama seperti Saver). Fungsi utama
   `suspend`; I/O berkas di `Dispatchers.IO`.
2. `app/src/test/java/com/example/ProjectOpenerTest.kt` (Robolectric).
3. `app/src/main/res/xml/file_paths.xml` — tambah `<cache-path>` (T5).

**Aturan keras:**

1. **Validasi dulu, sebelum menyalin apa pun** (skema §7): ZIP valid;
   `manifest.json` ada + JSON valid; `formatVersion` == 1 — bila bukan,
   tolak dengan pesan yang menyebut versi yang ditemukan (K6); field wajib
   ada + tipe benar; `uuid` item unik; tiap `fullFile`/`thumbFile` ada di
   arsip, stem = `uuid`, path relatif (tolak `/` di awal / `..`); tiap ujung
   edge merujuk `uuid` yang ada + `type` salah satu dari 8 `ConnectionType`;
   `points` goresan tidak kosong. Pesan gagal harus menyebut sebabnya.
2. **Satu transaksi Room** untuk Board + items + edges. Room 2.7.0 +
   room-ktx tersedia (`withTransaction`). Repository tidak memegang objek
   database dan DAO tidak bisa bertransaksi lintas DAO — putuskan caranya
   (mis. repository menerima database, atau metode `@Transaction` di kelas
   Database). Perubahan konstruktor repository harus ikut mengubah
   `MainActivity`.
3. **Id selalu baru** (K4): `boardId` + `id` item dari `autoGenerate`; nama
   berkas salinan memakai UUID **baru** (`UUID.randomUUID()`), JANGAN pakai
   ulang uuid arsip sebagai nama berkas. Bangun peta `uuid arsip → id DB
   baru` selagi insert item; insert edges dengan id hasil pemetaan — dalam
   transaksi yang sama.
4. **Kompatibilitas pra-Map:** arsip tanpa `edges`/`mapX`/`mapY`/`note`/
   `mapCamera` terbuka bersih (tanpa edge, map 0, note `""`, `mapCamera`
   default 1/0/0). `mapCamera` hilang BUKAN galat. Board baru dibuat dengan
   `name` + `createdAt` dari manifest; kolom mati `Board` dibiarkan default
   (K1).
5. **Goresan:** tulis `drawing_strokes_<newBoardId>.txt` di `filesDir`
   dengan format teks yang SAMA seperti `saveDrawingStrokes`, supaya
   `loadDrawingStrokes` bisa membacanya kembali.
6. **Kamera:** Opener TIDAK menyentuh SharedPreferences. Kembalikan kamera
   Board + Map dalam hasil Open (mis. `OpenedProject(boardId, boardCamera,
   mapCamera)`) — ViewModel yang akan menyimpannya (Fase 4; sisi Save sudah
   ada: `saveProject()`).
7. **Bersih saat gagal:** bila langkah apa pun gagal setelah penyalinan
   dimulai, hapus berkas yang sudah disalin; transaksi Room me-rollback
   papan. Tidak boleh ada papan setengah jadi DAN tidak boleh ada berkas
   yatim.
8. **T5:** tambah `<cache-path>` saja. JANGAN tambah `files-path` kecuali
   ada kebutuhan nyata — gambar di `filesDir` bersifat internal dan tidak
   pernah dibagikan lewat FileProvider.

**Unit test (bukti T4):** Robolectric, Room in-memory via
`Room.inMemoryDatabaseBuilder` + `ApplicationProvider.getApplicationContext()`
(belum ada preseden Room di test repo ini).

- Round-trip penuh: bangun arsip dengan `ProjectSaver` (2 gambar + 1
  goresan + 1 edge + note + posisi Map + kedua kamera) → Open ke DB
  in-memory → assert papan BARU (id baru, name/createdAt sama), items
  (semua field), edges ter-resolve ke id BARU, berkas goresan tertulis
  dengan isi benar, kamera dikembalikan benar.
- Penolakan (tiap kasus: jumlah papan tetap + tidak ada berkas tersalin):
  versi tak dikenal (`formatVersion` 2), ZIP korup (byte sampah), gambar
  hilang, edge tak valid (ujung tidak ada / type tak dikenal).
- Arsip pra-Map → terbuka bersih.

**Yang belum terbukti dari sini:** uji perangkat (Fase 7) — kompilasi +
eksekusi unit test sudah dibuktikan 2026-11-19 (`:app:testDebugUnitTest`,
JDK 21). Workflow Actions tetap hanya `assembleRelease` dan tidak menjalankan
unit test (lihat G7 + G21).

## 6. Di luar lingkup

- Sinkronisasi cloud / akun — aplikasi ini tanpa backend.
- Save banyak papan sekaligus (v2).
- Ekspor kanvas jadi gambar/PDF — itu fitur lain, bukan proyek.
- Mengubah format teks `drawing_strokes_<id>.txt` internal (G6 tetap
  berdiri sendiri).
