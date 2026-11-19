# Kesenjangan (Gaps)

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`
> Bernomor agar bisa dirujuk dari dokumen lain dan dari commit ("Perbaiki G7").
> Urutan = gabungan dampak × kemudahan, bukan urutan pengerjaan wajib.

## G1 — `DroidCanvasScreen.kt` 6.413 baris: satu file berisi hampir seluruh UI

- **Fakta:** 11 `@Composable`, rata-rata ~580 baris per composable. `DroidCanvasScreen`
  sendiri ~2.700 baris; `SidebarContent` ~1.600 baris; `CanvasItemView` ~670 baris.
- **Dampak:** Setiap perubahan UI menyentuh file yang sama; konflik merge, sulit
  mencari, sulit menguji. Ini risiko terbesar untuk pengembangan lanjutan.
- **Arah:** Pecah per bidang (kanvas+gesture, sidebar, dialog, values panel) sesuai
  [architecture.md](architecture.md) target 1. Satu ekstraksi per commit.

## G2 — Fungsi ViewModel tanpa pemanggil + wrapper yang tampak seperti duplikat

- **Fakta:** `updateItemScaleAndRotation` dan `updateItemAbsoluteRotation`
  (`DroidCanvasViewModel.kt:1066,1122`) tidak dipanggil dari mana pun — rotasi
  dijalankan lewat slider tab LAYERS (`updateItemRotationInMemory` +
  `commitItemRotation`). Sebaliknya, fungsi lokal `adjustPositionForScale`
  (`DroidCanvasScreen.kt:2930`) **bukan** duplikat `CanvasMathHelper`: ia hanya
  wrapper tipis yang mengubah `PositionResult` menjadi `Pair` — jangan "diperbaiki"
  dengan menghapusnya tanpa memeriksa.
- **Dampak:** Fungsi mati menyesatkan pembaca ("rotasi dua jari pasti memanggil
  ini"); wrapper yang disangka duplikat bisa terhapus secara keliru.
- **Arah:** Hapus kedua fungsi mati; bila wrapper ingin dihilangkan, ganti
  pemanggilnya langsung ke helper Java.

## G3 — Kolom kamera di `Board` mati (posisi kamera tinggal di SharedPreferences)

- **Fakta:** Migrasi 2–3 menambah `canvasScale/TranslateX/TranslateY` ke `boards`,
  tapi ViewModel membaca/menulis `board_scale_<id>` di SharedPreferences dan tidak
  pernah menyentuh kolom itu.
- **Dampak:** Dua sumber kebenaran; ekspor papan tidak membawa posisi kamera.
- **Arah:** Putuskan satu (lihat ADR-004): pindah ke Room atau hapus kolomnya.

## G4 — Kode mati: skema warna + entri katalog yang tidak dipakai

- **Fakta:** Skema Green (light/dark) dan Purple (`DarkColorScheme`/`LightColorScheme`)
  didefinisikan tapi tidak pernah dipakai — tema aktif hanya Mono + dynamic color.
  Di `libs.versions.toml`, entri retrofit, moshi, okhttp, camera, datastore,
  navigation-compose, accompanist-permissions, dan play-services-location
  dideklarasikan tapi tidak ada di `dependencies`.
- **Dampak:** Membingungkan pembaca baru ("apakah aplikasi ini memakai kamera?").
- **Arah:** Hapus yang tidak dipakai, atau sambungkan (Green sebagai pilihan tema).
  Jangan biarkan katalog menjadi daftar keinginan.

## G5 — File gambar yatim menumpuk (hapus tidak menghapus berkas)

- **Fakta:** `deleteItem` dan `clearCurrentBoard` sengaja tidak memanggil
  `deleteImageFiles` (demi undo). Hanya `deleteBoard` yang menghapus berkas.
  Crop dan duplikasi juga menambah berkas baru tanpa pernah menghapus yang lama.
- **Dampak:** Penyimpanan membengkak; pengguna tidak punya cara membersihkannya.
- **Arah:** Pengumpul sampah: hapus berkas di `filesDir` yang tidak dirujuk
  `canvas_items` **dan** tidak lagi dirujuk snapshot undo (lihat ADR-012).

## G6 — Goresan disimpan sebagai teks tanpa validasi/migrasi

- **Fakta:** Format `id|color|width|x,y;…` per baris; baris rusak dilewati diam-diam;
  tidak ada nomor versi format.
- **Dampak:** Perubahan format memutus goresan lama; kerusakan file tidak terdeteksi.
- **Arah:** Tambah nomor versi di baris pertama saat format proyek (roadmap langkah 1)
  dirancang; sampai saat itu, jangan ubah formatnya.

## G7 — CI tidak membangun dan tidak menguji tanpa keystore

- **Fakta:** `build-release.yml` hanya punya langkah build bila secret keystore ada.
  Tanpa secret, job checkout + setup lalu selesai tanpa membangun apa pun — dan
  tidak ada langkah `test` sama sekali.
- **Dampak:** Pull request bisa hijau tanpa pernah dikompilasi/diuji.
- **Arah:** Tambah job `assembleDebug` + `testDebugUnitTest` yang selalu jalan.

## G8 — Hampir tidak ada tes

- **Fakta:** 4 file tes: `StrokeOptimizerTest` (4 tes nyata),
  `ExampleUnitTest` (2+2), `ExampleRobolectricTest` (cek nama aplikasi),
  `GreetingScreenshotTest` (screenshot "Hello Robolectric!" — placeholder template,
  bukan UI aplikasi). Tidak ada tes ViewModel, Room, atau impor gambar.
- **Dampak:** Refaktor besar (G1) berjalan tanpa jaring pengaman.
- **Arah:** Ikuti pola `StrokeOptimizerTest` untuk setiap logika murni yang
  diekstrak; tambah tes ViewModel (undo/restore, arrange) sebelum memecah ViewModel.

## G9 — Package masih `com.example`

- **Fakta:** Kode di `com.example.*`, `applicationId` sudah `com.droidcanvas.app`.
- **Dampak:** Menyesatkan; Play Store dan beberapa tool menganggap `com.example`
  sebagai placeholder.
- **Arah:** Rename ke `com.droidcanvas` sekali jalan, sebelum rilis publik.

## G10 — `updateBoardBackground` dan `backgroundType` tanpa pemanggil

- **Fakta:** `Board.backgroundType` (default `'SLATE'`) dan
  `ViewModel.updateBoardBackground()` tidak dipanggil dari UI mana pun; latar
  kanvas selalu memakai warna tema + dots/grid inline di Screen.
- **Dampak:** Fitur latar papan setengah jadi: skema ada, UI-nya tidak.
- **Arah:** Putuskan: sambungkan ke pengaturan papan (UI pemilih latar) atau hapus
  kolom + fungsinya.

## G11 — `exportSchema = false`: tidak ada histori skema Room

- **Fakta:** Room tidak menyimpan JSON skema; migrasi hanya bisa dibaca dari kode.
- **Dampak:** Sulit memverifikasi migrasi dan sulit mendeteksi perubahan skema
  yang lupa dimigrasikan.
- **Arah:** Nyalakan `exportSchema = true` + lokasi `schemas/` begitu alur
  pengembangan stabil.

## G12 — `fallbackToDestructiveMigration()`: migrasi tak dikenal = hapus data

- **Fakta:** Bila versi DB naik tanpa migrasi yang cocok, Room menghapus database.
- **Dampak:** Satu kelalaian migrasi menghapus semua papan pengguna.
- **Arah:** Hapus fallback ini setelah migrasi berikutnya ditulis dan diuji
  (atau pertahankan hanya untuk build debug via `fallbackToDestructiveMigrationOnDowngrade`
  yang lebih sempit — putuskan dan catat di ADR).

## G13 — Impor gambar tanpa batas ukuran

- **Fakta:** `ImageStorageHelper` mengimpor URL/URI apa pun (timeout 10 detik),
  lalu decode bitmap penuh untuk thumbnail; tidak ada batas dimensi/ukuran berkas.
- **Dampak:** Gambar raksasa bisa menyebabkan OOM saat decode; impor HTTP berjalan
  di `Dispatchers.IO` tanpa pembatalan.
- **Arah:** Tolak/batasi dimensi ekstrem sebelum decode penuh; pertimbangkan
  `inJustDecodeBounds` + subsampling bertahap (sebagian sudah ada untuk thumbnail).

## G14 — Cache bitmap values study hanya 5 entri, berisi bitmap penuh

- **Fakta:** `decodedBitmapCache: LruCache<String, Bitmap>(5)` menyimpan bitmap hasil
  decode (thumbnail 400px — masih lumayan besar) tanpa batas byte.
- **Dampak:** Risiko tekanan memori saat berpindah antar item values study.
- **Arah:** Batasi berdasarkan byte (`LruCache.sizeOf`) saat values study dipakai
  untuk banyak item.

## G15 — Riwayat git mengandung penghapusan `app/src` (a033100)

- **Fakta:** Commit `a033100` ("Delete app/src directory") menghapus seluruh
  sumber; HEAD mengembalikannya lewat commit di atasnya — kecuali dua file yang
  hilang dan tidak kembali: `ui/composables/CanvasGridBackground.kt` dan
  `ui/composables/CanvasItemLayer.kt` (grid kini digambar inline di Screen).
- **Dampak:** Riwayat sebelum `a033100` bukan cerminan arsitektur yang bisa
  dipercaya begitu saja; file composable yang hilang mungkin berisi logika yang
  perlu diperiksa sebelum ditulis ulang.
- **Arah:** Jangan jadikan riwayat sebagai sumber kebenaran arsitektur — dokumen
  ini (yang dibaca dari HEAD) adalah acuannya. Bila butuh isi kedua file itu,
  ambil dari parent commit `a033100^`.

## G16 — README masih menunjuk ke repo lama

- **Fakta:** Link unduh APK dan perintah clone di README mengarah ke
  `meaayu/DroidCanvas`, bukan `Anonimz7/DroidCanvas`.
- **Dampak:** Pengguna mengikuti README akan mengunduh/meng-clone repo arsip.
- **Arah:** Perbaiki bersama langkah 0 di [roadmap.md](roadmap.md).

## G17 — Versi 0.2.0 dengan versionCode 1

- **Fakta:** `versionName = "0.2.0"`, `versionCode = 1` — versionCode tidak pernah
  naik mengikuti versionName.
- **Dampak:** Play Store menolak update bila versionCode tidak naik; penomoran
  membingungkan.
- **Arah:** Samakan skema (misalnya versionCode = 2 untuk 0.2.0 berikutnya) dan
  catat aturannya di sini.

## G18 — Undo snapshot menyalin seluruh daftar item

- **Fakta:** `saveCurrentStateToUndo()` menyalin `List<CanvasItem>` utuh (maks 50
  snapshot) di setiap mutasi — termasuk saat drag selesai, auto-arrange, dan
  toggle sederhana.
- **Dampak:** Papan dengan ratusan item memakai memori besar untuk undo; beberapa
  aksi (misalnya auto-arrange) menyimpan snapshot yang sangat besar.
- **Arah:** Terima untuk sekarang (batas 50 menahan); tinjau ulang ke
  command-pattern bila papan besar menjadi keluhan nyata — dengan ADR baru.

## G19 — Tidak ada pemisah antara data values study dan data gambar

- **Fakta:** `stopsJson`/`simplicity` tinggal di `CanvasItem` yang sama dengan
  geometri; hasil proses (`ValuesResult`) hanya di memori.
- **Dampak:** Mengubah skema values berarti mengubah tabel item; hasil proses
  harus dihitung ulang setiap buka papan.
- **Arah:** Biarkan dulu — pemisahan hanya bila values study tumbuh (misalnya
  preset tersimpan atau ekspor hasil). Catat di sini supaya tidak dicampur
  diam-diam saat refactor.

## G20 — FileProvider hanya mengekspos `downloads/`, bukan `filesDir`

- **Fakta:** `file_paths.xml` hanya berisi `external-files-path/downloads/`;
  gambar di `filesDir` internal tidak bisa dibagikan lewat provider ini.
- **Dampak:** Fitur berbagi/ekspor gambar nanti harus menambah path — atau
  menyalin dulu ke lokasi yang diekspos.
- **Arah:** Perbaiki saat mengerjakan Save/Open (roadmap langkah 1), bukan
  sekarang.
