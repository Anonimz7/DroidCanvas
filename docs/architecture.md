# Arsitektur DroidCanvas

> **Terakhir ditinjau:** 2026-11-13 · **HEAD:** `4a4439b`

## Keadaan sekarang (2026-11-13)

Apa yang ada di `main` hari ini. Bagian ini menang atas bagian "Rencana" bila bertentangan.

- **Satu modul Gradle** (`:app`), satu `Activity` (`MainActivity`), satu layar Compose
  (`DroidCanvasScreen`). Tidak ada Navigation Compose, tidak ada multi-modul.
- **Stack:** Kotlin 2.2.10, Compose BOM 2024.09.00 (Material 3), Room 2.7.0 (KSP2),
  Coil 2.7.0 (termasuk GIF), coroutines 1.10.2, AGP 9.1.1. Java 17. minSdk 24,
  targetSdk/compileSdk 36. Lihat `gradle/libs.versions.toml` dan `app/build.gradle.kts`.
- **Package kode** masih `com.example`, sedangkan `applicationId` adalah
  `com.droidcanvas.app` (lihat G9 di [gaps.md](gaps.md)).
- **Tidak ada framework DI.** Semua dependensi dirakit manual di `MainActivity.onCreate`:
  database → repository → `DroidCanvasViewModelFactory` → ViewModel.
- **Tidak ada lapisan jaringan.** Satu-satunya I/O jaringan adalah impor gambar dari URL
  http(s) di `ImageStorageHelper` dan pemuatan gambar oleh Coil. Tidak ada Retrofit/
  API — entri Retrofit/Moshi/OkHttp di version catalog tidak dipakai (lihat G4).
- **Penyimpanan 100% lokal:** Room + `filesDir` + file teks + SharedPreferences.
  Tidak ada sinkronisasi, tidak ada akun.

## Peta modul

```
DroidCanvas/
├─ app/build.gradle.kts        # applicationId com.droidcanvas.app, v0.2.0, Room/Coil/Compose
├─ gradle/libs.versions.toml    # katalog versi (sebagian entri tidak dipakai — lihat gaps.md G4)
├─ settings.gradle.kts         # satu modul: :app
└─ app/src/main/
   ├─ AndroidManifest.xml      # MainActivity + SEND/SEND_MULTIPLE, FileProvider, INTERNET
   └─ java/com/example/
      ├─ MainActivity.kt            # 149 baris: DI manual, Coil GIF, share intent, tema
      ├─ data/
      │  ├─ DroidCanvasDatabase.kt  # Room v8: Board, CanvasItem, DAO, Repository, migrasi 1–8
      │  └─ ImageStorageHelper.kt   # impor gambar → filesDir (full + thumb 400px)
      └─ ui/
         ├─ DroidCanvasScreen.kt    # 6.413 baris: SELURUH UI (lihat ui.md)
         ├─ DroidCanvasViewModel.kt # 1.683 baris: SELURUH state + logika
         ├─ ValueProcessor.kt       # jembatan Kotlin → ImageValueProcessor (values study)
         ├─ CanvasMathHelper.java   # rotasi/skala/drag dalam ruang kanvas
         ├─ CanvasPhysicsHelper.java# peluruhan fling (friction 0.95)
         ├─ CanvasZoomHelper.java   # zoom ke centroid, batas 0.01–10
         ├─ ImageValueProcessor.java# posterisasi + box blur paralel (values study)
         ├─ StrokeOptimizer.java    # RDP + Chaikin + hit-test penghapus
         └─ theme/                  # Color.kt, Theme.kt, Type.kt
```

## Lapisan dan aliran data

```
┌─────────────────────────────────────────────────────────────────┐
│ UI (Compose) — DroidCanvasScreen.kt (6.413 baris)               │
│  CanvasItemView · SidebarContent · DrawingToolbar ·             │
│  ValuesStudyPanel · CropImageDialog                             │
└───────────────┬─────────────────────────────────┬───────────────┘
                │ collectAsState()                │ event: panggil fungsi
                ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ ViewModel — DroidCanvasViewModel.kt (1.683 baris)               │
│  StateFlow (boards, canvasItems, selection, strokes, …)        │
│  mutableState (kamera kanvas, pengaturan, mode gambar)          │
│  _itemOverrides: bayangan in-memory saat drag/resize            │
│  undo/redo: snapshot penuh, 2 tumpukan (item + goresan)         │
└───────┬──────────────────────┬──────────────────┬───────────────┘
        │ Room Flow/suspend    │ file I/O         │ SharedPreferences
        ▼                      ▼                  ▼
┌──────────────┐   ┌────────────────────┐   ┌──────────────────┐
│ Room         │   │ filesDir           │   │ canvas_view_prefs│
│ boards,      │   │ ref_full_<uuid>    │   │ posisi kamera    │
│ canvas_items │   │ ref_thumb_<uuid>   │   │ per board, snap, │
│ (v8)         │   │ drawing_strokes_   │   │ tema, haptic, …  │
│              │   │ <boardId>.txt      │   │                  │
└──────────────┘   └────────────────────┘   └──────────────────┘
```

Aliran baca: `Room Flow` → `StateFlow` di ViewModel (`stateIn`,
`WhileSubscribed(5000)`) → `collectAsState()` di Compose. Aliran tulis: gesture/event UI
→ fungsi ViewModel → `Dispatchers.IO` → Room; posisi kamera kanvas di-debounce 1 detik
lalu ditulis ke SharedPreferences, **bukan** ke Room.

## Prinsip yang terlihat dari kode

1. **Satu ViewModel untuk segalanya.** Papan, item, seleksi, gambar, undo, crop,
   values study, pengaturan, dan kamera kanvas semuanya tinggal di satu kelas.
   Ini membuat fitur baru cepat ditulis, tapi membuat kelas itu titik perubahan
   untuk hampir semua hal (lihat G1).
2. **Hot path ditulis di Java.** Matematika kanvas, fisika fling, zoom, optimasi
   goresan, dan pemrosesan piksel values study semuanya helper Java stateless yang
   murni fungsi — mudah diuji tanpa Android. Inilah satu-satunya bagian kode yang
   punya tes unit nyata (`StrokeOptimizerTest`).
3. **Optimistis dulu, simpan belakangan (override in-memory).** Saat drag/resize/
   putar, UI menulis ke `_itemOverrides` (peta `id → CanvasItem`) dan baru commit
   ke Room saat gesture selesai. Ini yang membuat manipulasi terasa mulus tanpa
   membanjiri database.
4. **Undo = snapshot penuh.** Setiap mutasi menyimpan salinan seluruh daftar item
   (maksimal 50 snapshot). Sederhana dan benar, tapi boros memori untuk papan besar.
5. **Berkas gambar tidak pernah dihapus saat undo masih mungkin.** Hapus item dan
   bersihkan papan sengaja mengomentari pemanggilan `deleteImageFiles` — file yatim
   menumpuk (lihat G5).

## Arsitektur target

Arah yang disepakati untuk pengembangan ke depan. Bukan janji tanggal — ini pagar
supaya fitur baru tidak mengulang pola yang sudah terbukti rapuh.

1. **Pecah `DroidCanvasScreen.kt` per bidang**, tanpa mengubah perilaku: satu file
   untuk kanvas + gesture, satu untuk sidebar, satu untuk dialog (crop, pengaturan,
   papan), satu untuk panel values study. Target: tidak ada satu file UI di atas
   ~1.000 baris. Lakukan bertahap, satu ekstraksi per commit, dengan build hijau
   di tiap langkah.
2. **Pecah ViewModel per domain** mengikuti jejak yang sudah dimulai di riwayat
   ("extract board and selection operations to dedicated state holders"):
   `BoardState`, `SelectionState`, `DrawingState`, `ValuesState` — masing-masing
   dengan StateFlow-nya sendiri, dikoordinasikan satu ViewModel tipis.
3. **Satu sumber kebenaran per data.** Posisi kamera kanvas pindah sepenuhnya ke
   Room (kolom `Board.canvasScale/Translate` sudah ada sejak migrasi 2–3 tapi tidak
   pernah dipakai — lihat G3) **atau** kolom itu dihapus; tidak boleh dua-duanya hidup.
   Aturan yang sama untuk kolom `isGrayscale` (lihat ADR-010).
4. **Batas repository ditegakkan.** UI tidak boleh menyentuh `File` atau
   SharedPreferences langsung; semua lewat ViewModel/repository. Hari ini
   `CropImageDialog` dan Screen masih mengakses `File`/`LocalContext` di beberapa
   tempat — rapikan saat menyentuh area itu.
5. **Format proyek (Save/Open) dirancang sebagai lapisan sendiri**
   (lihat [roadmap.md](roadmap.md)): serializer terpisah dari Room dan dari UI,
   dengan nomor versi format, supaya file proyek lama tetap bisa dibuka.
6. **Nama paket diganti** dari `com.example` ke `com.droidcanvas` sebelum rilis
   publik berikutnya — sekali jalan, bersamaan dengan pembaruan `applicationId`
   yang sudah benar (lihat G9).
7. **Tes mengikuti ekstraksi.** Setiap helper/logika yang dipindah dari Screen/
   ViewModel ke kelas tersendiri wajib membawa tesnya (pola `StrokeOptimizerTest`).
   Target minimum: semua helper Java/Kotlin murni + serialisasi format proyek.
