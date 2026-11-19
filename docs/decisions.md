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
