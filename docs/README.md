# Dokumentasi DroidCanvas

> **Terakhir ditinjau:** 2026-11-18 · **HEAD:** `c3a14d8` · **Repo:** `Anonimz7/DroidCanvas` (fork dari `meaayu/DroidCanvas`, sumber asli sudah diarsipkan)

Dokumentasi ini adalah peta keadaan proyek **sekarang**, ditulis dari pembacaan langsung
seluruh kode sumber — bukan dari ingatan atau dari klaim README. Tujuannya: siapa pun
(yang melanjutkan pengembangan) tidak perlu memetakan ulang dari nol sebelum merancang
fitur berikutnya.

Aturan pemeliharaan:

- Setiap dokumen punya header "Terakhir ditinjau" + commit yang dibaca. Kalau kode berubah,
  perbarui header itu — dokumen tanpa tanggal adalah dokumen yang tidak bisa dipercaya.
- Bagian **"Keadaan sekarang"** selalu menang atas **"Rencana"** bila keduanya bertentangan.
- Klaim soal "sudah selesai" harus bisa ditunjuk ke file + baris kode, bukan ke README.

## Peta dokumen

| Dokumen | Isi |
|---|---|
| [architecture.md](architecture.md) | Gambaran besar: modul, lapisan, aliran data, dan arsitektur target |
| [decisions.md](decisions.md) | Keputusan arsitektur (ADR) beserta alasan dan yang ditolak |
| [data-model.md](data-model.md) | Skema Room, migrasi, file gambar, strokes, dan SharedPreferences |
| [canvas-engine.md](canvas-engine.md) | Mesin kanvas: koordinat, gesture, fisika, gambar, auto-arrange, values study |
| [ui.md](ui.md) | Struktur Compose: layar, sidebar, panel, dialog, tema |
| [roadmap.md](roadmap.md) | Roadmap README **diaudit terhadap kode** + urutan kerja yang diusulkan |
| [gaps.md](gaps.md) | Utang teknis, kode mati, dan risiko — bernomor (G1, G2, …) agar bisa dirujuk |
| [designs/save-open.md](designs/save-open.md) | Rancangan fitur Save/Open project file (langkah 1) |
| [designs/manifest-schema.md](designs/manifest-schema.md) | Skema manifest v1 Save/Open + contoh (`manifest-example.json`) |
| [designs/undo-gesture.md](designs/undo-gesture.md) | Rancangan undo/redo gesture (langkah 2) |
| [designs/tablet.md](designs/tablet.md) | Rancangan tablet optimization (langkah 3) |
| [designs/grayscale.md](designs/grayscale.md) | Rancangan grayscale toggle (langkah 4) |
| [designs/map.md](designs/map.md) | Rancangan Map view — node graph di atas Board (langkah 5) |

## Proyek dalam satu paragraf

DroidCanvas adalah aplikasi Android satu modul (Kotlin + Jetpack Compose + Room) untuk
papan referensi gambar: kanvas tak terbatas tempat gambar bisa dipan, di-zoom, diputar,
diatur, digambar di atasnya, dan dipelajari nilai tonalnya. Satu `Activity`, satu layar
utama, tanpa navigasi, tanpa backend, tanpa akun. Semua data tersimpan lokal: Room untuk
papan dan item, `filesDir` untuk berkas gambar, file teks untuk goresan, SharedPreferences
untuk pengaturan dan posisi kamera kanvas.

## Angka kunci (di HEAD `4a4439b`)

| Ukuran | Nilai |
|---|---|
| Total baris (main + test + res + build) | ~10.200 |
| `DroidCanvasScreen.kt` | 6.413 baris — satu file berisi hampir seluruh UI |
| `DroidCanvasViewModel.kt` | 1.683 baris — seluruh state dan logika |
| File sumber main | 17 file (9 Kotlin UI/data, 5 Java helper, 3 tema) |
| Tabel Room | 2 (`boards`, `canvas_items`), versi DB 8 |
| Versi aplikasi | 0.2.0 (versionCode 1), minSdk 24, targetSdk 36 |
| Tes | 4 file; hanya `StrokeOptimizerTest` yang menguji logika nyata |

## Cara membaca peta ini sebelum mengubah kode

1. Baca `architecture.md` untuk orientasi 10 menit.
2. Baca dokumen topik yang disentuh perubahanmu (`canvas-engine.md` untuk gesture,
   `data-model.md` untuk skema, `ui.md` untuk tampilan).
3. Cek `decisions.md` — kalau rencanamu bertentangan dengan ADR yang tercatat, itu
   sinyal untuk memperbarui ADR-nya dulu, bukan diam-diam melanggarnya.
4. Kalau menemukan ketidaksesuaian antara dokumen dan kode, perbaiki dokumennya di
   commit yang sama dengan perubahan kode.
