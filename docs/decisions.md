# Keputusan Arsitektur (ADR)

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`

Format: setiap ADR mencatat konteks, keputusan, dan konsekuensi. Status `Diterima`
berarti kode saat ini mengikuti keputusan itu. Status `Perlu ditinjau` berarti
keputusannya dipertanyakan oleh temuan pemetaan — lihat [gaps.md](gaps.md).

## ADR-001 — Satu Activity, satu layar, tanpa navigasi

- **Status:** Diterima.
- **Konteks:** Aplikasi ini satu papan kanvas; tidak ada alur multi-layar.
- **Keputusan:** `MainActivity` satu-satunya activity; `DroidCanvasScreen` satu-satunya
  layar. Tidak memakai Navigation Compose (entrinya ada di version catalog tapi tidak
  dipakai).
- **Konsekuensi:** Semua "layar" (pengaturan, kelola papan, crop, values) adalah dialog/
  panel di dalam Screen. Kalau suatu hari ada layar kedua (misalnya galeri papan),
  navigasi harus diperkenalkan dari nol.

## ADR-002 — DI manual, tanpa Hilt/Koin

- **Status:** Diterima (dengan catatan).
- **Konteks:** Graf dependensi kecil: database → repository → ViewModel.
- **Keputusan:** Perakitan manual di `MainActivity.onCreate` (`MainActivity.kt:39-50`)
  dengan `DroidCanvasViewModelFactory`.
- **Konsekuensi:** Sederhana dan transparan; tapi setiap dependensi baru harus
  di-threading manual lewat factory. Tinjau ulang saat graf melewati ~5 dependensi
  atau saat butuh scope berbeda (misalnya per-board).

## ADR-003 — Semua data lokal, tanpa backend/akun

- **Status:** Diterima.
- **Konteks:** README: "no account required". Aplikasi referensi untuk seniman.
- **Keputusan:** Room + `filesDir` + file teks + SharedPreferences. Tidak ada
  sinkronisasi cloud, tidak ada server.
- **Konsekuensi:** Save/Open file proyek menjadi satu-satunya jalan keluar-
  masuk data (lihat [roadmap.md](roadmap.md)). Backup otomatis Android aktif
  (`allowBackup=true`) tapi aturan backup masih template kosong — file gambar di
  `filesDir` ikut tercadangkan apa adanya.

## ADR-004 — Posisi kamera kanvas disimpan di SharedPreferences, bukan Room

- **Status:** Perlu ditinjau (lihat G3).
- **Konteks:** Migrasi 2–3 menambahkan `canvasScale/canvasTranslateX/canvasTranslateY`
  ke tabel `boards`.
- **Keputusan (yang terjadi di kode):** ViewModel mengabaikan kolom itu. Posisi kamera
  dibaca/ditulis ke SharedPreferences (`board_scale_<id>` dst, debounce 1 detik,
  `DroidCanvasViewModel.kt:340-366`).
- **Konsekuensi:** Dua tempat menyimpan hal yang sama; kolom Room jadi mati.
  Putuskan satu: pindahkan ke Room (agar ikut saat papan dipindah/diekspor) atau hapus
  kolomnya di migrasi berikutnya.

## ADR-005 — Hot path ditulis di Java, stateless, murni fungsi

- **Status:** Diterima.
- **Konteks:** Perhitungan per-frame (gesture, fisika, piksel) harus cepat dan teruji.
- **Keputusan:** `CanvasMathHelper`, `CanvasPhysicsHelper`, `CanvasZoomHelper`,
  `StrokeOptimizer`, `ImageValueProcessor` ditulis Java, tanpa state, tanpa
  dependensi Android kecuali `Bitmap`.
- **Konsekuensi:** Bagian ini yang paling mudah diuji — dan memang satu-satunya yang
  punya tes (`StrokeOptimizerTest`). Pola ini dipertahankan: logika baru yang murni
  (misalnya serialisasi format proyek) ditulis dengan cara yang sama.

## ADR-006 — Override in-memory selama gesture, commit saat selesai

- **Status:** Diterima.
- **Konteks:** Menulis Room di setiap frame drag akan menggerakkan Flow dan membuat
  recomposition berantai.
- **Keputusan:** Selama drag/resize/putar/sesuaikan values, ViewModel menulis ke
  `_itemOverrides: Map<Int, CanvasItem>`; `canvasItems` adalah gabungan
  `dbItems + overrides`. Commit ke Room terjadi di akhir gesture
  (`commitItemScale`, `commitItemRotation`, `commitSimplicity`, `commitStops`).
- **Konsekuensi:** UI mulus; tapi ada dua representasi item yang harus selalu
  konsisten, dan override yang lupa di-commit akan hilang diam-diam. Setiap
  interaksi baru yang mengubah item harus mengikuti pola ini, bukan menulis
  langsung ke Room di tengah gesture.

## ADR-007 — Undo/redo memakai snapshot penuh, dua tumpukan terpisah

- **Status:** Diterima (dengan batas).
- **Konteks:** Undo harus mengembalikan penghapusan item dan file-nya.
- **Keputusan:** `undoStack` menyimpan salinan seluruh `List<CanvasItem>` (maks 50);
  tumpukan terpisah untuk goresan (`drawingUndoStack`). Restore dilakukan dengan
  diff terhadap DB (`restoreSnapshot`).
- **Konsekuensi:** Benar dan sederhana; boros memori untuk papan dengan ratusan item.
  Batas 50 snapshot menahan borosnya. Jangan ganti ke command-pattern tanpa
  mencatat ADR baru — snapshot adalah alasan file gambar tidak dihapus saat delete
  (lihat ADR-012).

## ADR-008 — Goresan disimpan sebagai file teks, bukan Room

- **Status:** Diterima (dengan catatan).
- **Konteks:** Goresan adalah data transient per papan dengan format sederhana.
- **Keputusan:** `drawing_strokes_<boardId>.txt` di `filesDir`, satu baris per goresan:
  `id|color|width|x,y;x,y;…` (`saveDrawingStrokes`/`loadDrawingStrokes`).
- **Konsekuensi:** Tidak ada migrasi, tidak ada query, dan baris rusak dilewati diam-diam
  saat load. Kalau goresan menjadi fitur utama (lapisan, tekanan stylus), pindahkan
  ke Room dengan ADR baru.

## ADR-009 — Values study = posterisasi berbasis luminance + stops

- **Status:** Diterima.
- **Konteks:** Fitur "master copy study": petakan gambar ke rentang tonal grayscale.
- **Keputusan:** Pipeline `ValueProcessor` (Kotlin) → `ImageValueProcessor` (Java):
  box blur paralel sesuai `simplicity`, luminance Rec.709, petakan tiap piksel ke
  stop terdekat, hasilkan bitmap + histogram + distribusi. Diproses dari thumbnail
  (400px), debounce 150 ms, dedup berdasarkan signature konfigurasi, cache LRU 5 bitmap.
- **Konsekuensi:** Cepat dan cukup akurat untuk studi nilai; bukan hasil resolusi
  penuh — jangan jadikan output ini sebagai ekspor akhir tanpa diputuskan.

## ADR-010 — Grayscale toggle: TIDAK diimplementasikan (klaim README keliru)

- **Status:** Perlu ditinjau — temuan pemetaan, bukan keputusan sadar.
- **Konteks:** README mencentang "Grayscale toggle" sebagai selesai.
- **Fakta kode:** Kolom `isGrayscale` ditambahkan migrasi 5–6, lalu **dihapus** oleh
  migrasi 7–8 saat tabel dibuat ulang; entity `CanvasItem` tidak punya field itu;
  `ColorMatrix`/`ColorFilter` diimpor di Screen tapi tidak pernah dipakai; tidak ada
  toggle di UI. Yang ada hanyalah values study, yang memang memetakan ke grayscale
  — itu fitur berbeda.
- **Keputusan yang diperlukan:** (a) implementasikan grayscale sebagai flag per-item
  yang persisten, atau (b) hapus centang itu dari README. Jangan biarkan klaim dan
  kode berbeda — ini persis jenis ketidaksesuaian yang membuat pemetaan ulang
  diperlukan.

## ADR-011 — Crop membuat berkas baru, aslinya utuh

- **Status:** Diterima.
- **Konteks:** Crop harus bisa di-undo, dan hasil crop harus punya identitas sendiri.
- **Keputusan:** `cropCanvasItem` mendekode file asli (dengan koreksi rotasi EXIF),
  memotong, lalu menulis `ref_full_<uuid-baru>.jpg` + thumbnail baru; item di-DB
  menunjuk ke berkas baru, posisi digeser mengikuti offset crop yang dirotasi.
  Berkas lama tidak dihapus (agar undo mengembalikannya).
- **Konsekuensi:** Konsisten dengan ADR-007/012; setiap crop menambah file. Lihat G5
  untuk rencana pembersihan file yatim.

## ADR-012 — Hapus item tidak menghapus berkas gambar

- **Status:** Diterima sementara, perlu tindak lanjut (lihat G5).
- **Konteks:** Undo sesudah hapus harus mengembalikan gambar.
- **Keputusan:** `deleteItem` dan `clearCurrentBoard` sengaja mengomentari
  `ImageStorageHelper.deleteImageFiles`; hanya `deleteBoard` yang menghapus berkas.
- **Konsekuensi:** Penyimpanan membengkak oleh file yatim. Perlu pekerjaan lanjutan:
  pengumpul sampah (sweep berkas tanpa referensi di DB) yang aman terhadap undo —
  misalnya hanya hapus berkas yang sudah tidak dirujuk DAN snapshot undo-nya sudah
  kedaluwarsa.

## ADR-013 — Tema: Mono sebagai default, dynamic color bila tersedia

- **Status:** Diterima (dengan kode mati — lihat G4).
- **Konteks:** Tampilan harus mengikuti Material You di Android 12+, dengan fallback
  yang tenang.
- **Keputusan:** `MyApplicationTheme`: dynamic color bila aktif dan SDK ≥ 31; bila
  tidak, skema Mono (terang/gelap). Opsi pitch black menimpa background menjadi
  hitam pekat. Skema Green dan Purple didefinisikan di `Color.kt`/`Theme.kt` tapi
  **tidak dipakai** — hapus atau sambungkan ke pengaturan tema.
- **Konsekuensi:** Perilaku tema hari ini sepenuhnya ditentukan Mono + dynamic color.
  Menambah pilihan tema berarti menghidupkan skema yang ada atau menambah skema baru
  di satu tempat ini saja.

## ADR-014 — Rilis lewat GitHub Actions, hanya bila ada keystore

- **Status:** Diterima (dengan celah — lihat G7).
- **Konteks:** Rilis APK bertanda tangan dari tag `v*`.
- **Keputusan:** Workflow `build-release.yml`: tag `v*` → decode keystore dari secret
  → `assembleRelease` → upload artefak (retensi 30 hari).
- **Konsekuensi:** Tanpa secret keystore, workflow tidak membangun apa-apa — termasuk
  tidak menjalankan tes. CI hari ini bukan pengaman kualitas; jadikan begitu
  (tambah job `assembleDebug` + `testDebugUnitTest` tanpa keystore) sebelum
  mengandalkan CI untuk review.

## ADR-015 — Save/Open berkas proyek: format arsip v1

- **Status:** Diterima (2026-11-19; sudah dibangun, Fase 1–5).
- **Konteks:** Satu papan harus bisa disimpan jadi satu berkas, dipindah ke perangkat lain, dan dibuka kembali utuh. Nama fitur diputuskan Save/Open (analogi Photoshop), bukan Export/Import; Export adalah fitur terpisah (render JPG/PNG).
- **Keputusan:** K1 kolom mati **dibuang** dari manifest (hanya data hidup); K2 satu berkas = **satu papan** (v1); K3 ekstensi **`.dc`** (ZIP — diubah 2026-11-19 dari `.droidcanvas`, kependekan); K4 Open **selalu papan baru** (id baru, tanpa dialog konflik); K5 values study **dihitung ulang** saat Open; K6 versi format tak dikenal **ditolak dengan pesan yang jelas**. Rancangan: `docs/designs/save-open.md`.
- **Konsekuensi:** Format v1 membeku sebelum dibangun; arsip pra-Map tetap terbaca (field Map opsional); multi-papan dan Export JPG/PNG tetap di luar lingkup.

## ADR-016 — Wiring Open: database ke ViewModel, kamera via prefs, uji T7/T8

- **Status:** Diterima (2026-11-19; sudah dibangun, Fase 4–5).
- **Konteks:** Tombol Open butuh `ProjectOpener.open(berkas, filesDir, database)` — Opener butuh objek database untuk `withTransaction` lintas-DAO, sementara ViewModel selama ini hanya memegang repository.
- **Keputusan:** (a) Database diteruskan ke ViewModel lewat factory (`DroidCanvasViewModelFactory(repository, context, database)`; `MainActivity` sudah memegang `database`) — repository tidak diubah. (b) `openProject(uri)`: salin URI ke temp ZIP di `cacheDir` (nama `open-<uuid>.dc`, dihapus di `finally`), panggil Opener, tulis kedua kamera ke prefs **sebelum** `selectBoard` — karena `selectBoard` membaca kamera dari prefs. Papan baru langsung aktif. (c) T7 (buka dua kali) + T8 (20 gambar) ditutup dengan unit test di `ProjectOpenerTest` (total 9 test); ANR + values study terhitung ulang tetap uji perangkat (Fase 7) karena tidak bisa dibuktikan unit test.
- **Konsekuensi:** ViewModel kini bergantung pada database (bukan hanya repository); `MainActivity` adalah satu-satunya situs konstruksi factory. Pesan gagal Open sudah menyebut sebabnya (K6) lewat `ProjectOpenException`.

## ADR-017 — Save ulang: simpan ke Download/droidcanvas, Save/Save As/Share

- **Status:** Diterima (2026-11-19; sudah dibangun, menunggu push/build).
- **Konteks:** Save Fase 4 menulis ZIP ke `cacheDir` lalu langsung membuka share sheet — pengguna meminta Save menyimpan ke penyimpanan (folder `droidcanvas`), dialog nama saat pertama simpan, dan Save As terpisah.
- **Keputusan:** (a) Save menulis ke folder publik `Download/droidcanvas` lewat `ProjectFileStore` (MediaStore API 29+, File API di bawahnya; nama sama = timpa). (b) Target (nama berkas) diingat per papan di prefs (`save_file_<boardId>`); Save pertama kali / Save As / Share tanpa target → dialog penamaan (default = nama papan / target saat ini). (c) Share = simpan dulu (segar) lalu bagikan via salinan `cacheDir` + `FileProvider` (satu jalur semua API); Save biasa tidak lagi membuka share sheet. (d) Open mengisi target papan baru dari nama berkas asal (DISPLAY_NAME) — Save menulis kembali ke berkas yang dibuka. (e) UI PROJECT FILE jadi dua baris: Save + Save As, Open + Share.
- **Konsekuensi:** Berkas `.dc` kelihatan di file manager dan selamat dari uninstall; `cache-path` tetap dipakai (salinan berbagi); nama sama menimpa tanpa konfirmasi (keputusan sadar — folder ini milik pengguna); rename papan tidak mengubah nama berkas target.

## ADR-018 — Hapus papan pertama, indikator PROJECT FILE, slug nama berkas, gambar Map utuh

- **Status:** Diterima (2026-11-19; sudah dibangun, menunggu push; build ditahan menunggu keputusan item 5 — catatan di Board).
- **Konteks:** (a) Papan pertama ("Number 1") tidak bisa dihapus — penjaga hapus punya 2 syarat (`size > 1` + `id != defaultBoardId`). (b) PROJECT FILE tidak menunjukkan papan mana yang aktif / berkas apa. (c) Nama berkas hasil Save "Number 1.dc.zip" — spasi dipertahankan + MediaStore menempelkan `.zip`. (d) Gambar node Map terpotong (Crop); pengguna minta tampil utuh.
- **Keputusan:** (a) Syarat `id != defaultBoardId` dihapus di kedua tempat (dialog Manage Boards + daftar WORKSPACES); syarat `size > 1` dipertahankan (aplikasi selalu butuh ≥1 papan); hapus papan kini lewat dialog konfirmasi (hapus = permanen, termasuk berkas gambarnya). `deleteBoard` sudah menangani hapus papan aktif (pindah ke papan tersisa). (b) PROJECT FILE menampilkan "Papan aktif: <nama>" + "Berkas: <nama>" / "Belum pernah disimpan" (dari `currentBoardSavedFileName`). (c) `sanitizeFileName` jadi slug (huruf kecil, spasi → `_`, `.dc.zip` lama dibuang saat Open); MIME MediaStore jadi `application/octet-stream` agar `.zip` tidak ditempelkan (Share tetap `application/zip`). (d) `mapImageFull` (prefs `map_image_full`, default true): Fit (utuh) vs Crop; opsi Switch di Settings, seksi MAP.
- **Konsekuensi:** Papan pertama bisa dihapus seperti papan lain; berkas lama `Number 1.dc.zip` tidak berubah nama (hapus manual); Open berkas `.dc.zip` lama menargetkan `number_1.dc`.

## ADR-019 — Catatan juga di Board (tombol + di bawah gambar terpilih)

- **Status:** Diterima (2026-11-19; sudah dibangun, menunggu push/build bersama ADR-018).
- **Konteks:** Catatan (O2: satu kolom `note` per item, dipakai bersama Board+Map) selama ini hanya bisa ditambah/diubah dari Map (O4 v1). Pengguna minta tombol + yang sama di Board.
- **Keputusan:** (a) Tombol + lingkaran di bawah gambar terpilih (tengah bawah, di antara handle sudut — keempat sudut sudah terpakai: hapus, flip H/V, resize), hanya tampil untuk gambar terpilih yang tidak di-pin; membuka dialog catatan yang sama seperti di Map (Add/Edit note, `updateNote` yang sama; simpan kosong = hapus catatan). (b) Ikon komentar kecil di tengah atas gambar bila ada catatan (tampil selalu, dipilih atau tidak) — hanya penanda. (c) Balon isi catatan tetap hanya di Map, supaya tidak menutupi gambar di Board.
- **Konsekuensi:** O4 berubah — tambah/ubah catatan kini dua arah (Board + Map), tampilan balon tetap Map-only; catatan gambar yang di-pin hanya bisa diubah lewat Map.

## ADR-020 — Model edge untuk todo (P5, opsi (a))

- **Status:** Diterima (2026-11-19; dibangun di T5).
- **Konteks:** `Edge.fromItemId`/`toItemId` ber-FK ke `canvas_items` (CASCADE), jadi todo tidak bisa jadi ujung edge. Rancangan awal mengira FK bisa dipertahankan untuk ujung ITEM — salah: FK SQLite tidak bisa bersyarat; menyimpan id todo di kolom ber-FK akan ditolak database. Diverifikasi dari kode (`DroidCanvasDatabase.kt` ~78: tiga FK di `@Entity` `Edge`).
- **Keputusan:** (a) Satu tabel `edges` + kolom penanda jenis ujung (`fromKind`/`toKind`: `ITEM`/`TODO`, default `ITEM`); kedua FK ke `canvas_items` DIBUANG (migrasi 11→12 membuat ulang tabel — SQLite tidak bisa menghapus FK pakai ALTER); FK `boardId` TETAP. Hapus berantai pindah ke repository dalam satu transaksi (`deleteCanvasItem`/`deleteTodo` memanggil `deleteEdgesForItem`/`deleteEdgesForTodo` dulu); kedua query WAJIB dibatasi kind — id gambar dan id todo punya urutan autoGenerate sendiri dan bisa sama angkanya, tanpa pembatasan kind hapus gambar bisa ikut menghapus garis todo.
- **Konsekuensi:** Jaminan hapus-berantai tidak lagi dipegang database melainkan kode — jalur hapus baru yang lupa memanggil helper repository bisa meninggalkan garis yatim; dijaga oleh `EdgeTodoCascadeTest` (5 kasus, termasuk tabrakan id 1=1). Opsi (b) (tabel terpisah) ditolak karena menduplikasi seluruh logika edge; (c) (ruang id bersama) ditolak karena rapuh. Node todo di Map: kartu 180×96dp (centang + teks, maks 2 baris), port di tengah vertikal (48dp); grid awal satu daftar (gambar lalu todo); drag tanpa snapshot undo (T7). Gap sementara: Saver T5 melewati garis ke/dari todo (manifest belum punya `todos[]`) — ditutup T6.

## ADR-021 — Todo = judul + daftar baris (K11)

- **Status:** Diterima (2026-11-19; dibangun di R1–R3).
- **Konteks:** Todo T1–T5 dibangun sebagai satu entri (`text` + `done`). Pengguna mengoreksi konsepnya: todo bukan satuan — satu todo = **judul** lalu **daftar todo di bawahnya**, ditambah lewat tombol **+**. Judul cuma kepala (tidak bisa dicentang); Map menampilkan kartu yang sama seperti Board.
- **Keputusan:** (a) `Todo` tinggal judul (`title`; `text`/`done` dihapus) + entitas baru `TodoItem` (tabel `todo_items`, FK `todoId → todos.id` CASCADE; `text`, `done`, `createdAt`). (b) Migrasi 12→13 DROP + buat ulang `todos` — todo lama **tidak dimigrasi, direset** (keputusan user: "Biarin aja toh nanti direset"). (c) Isi kartu Board + node Map = satu composable bersama `TodoListContent` (judul tebal + garis + baris + tombol + di tengah; tinggi kartu 93+36N dp); port Map di tengah judul (22dp). (d) Interaksi: ketuk judul → ubah judul; ketuk baris → ubah baris; hapus baris dari **dalam dialog ubah baris** (bukan long-press baris — menyimpang dari usulan awal supaya tidak ada hit-test per baris); long-press kartu Board → menu Ubah judul / Hapus todo. (e) `toggleTodo` (todo utuh) dihapus — yang dicentang adalah baris (`toggleTodoItem`).
- **Konsekuensi:** Todo yang dibuat di T1–T5 hilang saat migrasi 12→13 berjalan (sengaja, atas kata user); `EdgeTodoCascadeTest` diperbarui (`text` → `title`); test baru `TodoItemTest` (5 kasus, belum dieksekusi — butuh `:app:testDebugUnitTest` JDK 21). Bentuk manifest T6 ikut berubah: `todos[]` berisi `title` + `items[]` bersarang.

## ADR-022 — Bubble note tampil di Board + kontainer Map ikut rasio media

- **Status:** Diterima (2026-11-19; dibangun di R2–R3).
- **Konteks:** (a) Di Board, note hanya tampak sebagai ikon komentar kecil di atas gambar — isi note tidak kelihatan (ADR-019 sengaja menahan balon agar tidak menutupi gambar). Pengguna bertanya apakah note Map sebaiknya ditampilkan di Board. (b) Gambar Map utuh (ADR-018, Fit) masih tampil di kotak tetap 120dp — gambar rasio layar ponsel mengecil di dalam kotak.
- **Keputusan:** (a) Bubble note tampil juga di Board: teks note (maks 2 baris) di bawah gambar, 56dp, ketuk → dialog ubah note; posisi mengikuti tombol + (di bawahnya bila tampil, langsung di bawah gambar bila tidak). Ikon komentar kecil dihapus — teksnya sudah kelihatan. O4 berubah lagi: balon kini di Board DAN Map. (b) Kontainer thumbnail Map **menyesuaikan media**: bila `mapImageFull` nyala, tinggi thumbnail = 180dp × (tinggi ÷ lebar gambar) — gambar rasio ponsel jadi node tinggi yang tampil utuh selebar 180dp; bila mati (Crop), tetap 120dp. Tinggi kartu = tinggi thumbnail + 68dp; grid Map sudah menghitung tinggi per baris, dan `fitToContent` Map memakai tinggi node sebenarnya.
- **Konsekuensi:** Bubble menambah jejak visual di bawah gambar yang punya note (hanya bila ada note); node Map untuk gambar rasio ponsel jadi jauh lebih tinggi dari sebelumnya — grid dan Fit All sudah mengikuti tinggi sebenarnya.

## ADR-023 — Gambar selalu HD (pengaturan)

- **Status:** Diterima (2026-11-19; sudah dibangun).
- **Konteks:** Thumbnail yang disimpan adalah hasil downsample maks 400px (`ImageStorageHelper`, target max dimension 400px), sementara gambar asli 1080×2400 — thumbnail burik bila diperbesar. Di Board, gambar asli dimuat hanya bila terpilih, zoom > 1.2×, atau GIF (`shouldLoadFull`); di Map, node selalu memuat `thumbPath` dan node tidak bisa diklik — jadi Map selalu burik, isinya tidak kelihatan.
- **Keputusan:** Switch baru di Settings seksi MAP, "Gambar selalu HD" (`alwaysFullRes`, prefs `always_full_res`, **default mati**): bila nyala, Board (`shouldLoadFull`) dan Map (`MapNodeCard`, param baru `alwaysFullRes`) selalu memuat `fullPath`, bukan thumbnail. Mati = perilaku sekarang (HD hanya saat diklik/zoom/GIF di Board; Map selalu thumbnail).
- **Konsekuensi:** Nyala = gambar tajam di Board dan Map tanpa perlu klik; harga = muat awal lebih lambat, terutama di papan dengan banyak gambar besar. Tombol + todo dipindah ke tengah kartu dalam commit yang sama (mengubah K11 dari rata kanan — `TodoListContent`, `CenterEnd` → `Center`).
