# Rancangan: Map view — node graph di atas Board

> **Status:** Rancangan lengkap — keputusan O1–O5 + K8 disetujui user 2026-11-18;
> fase 1–6 selesai (di-push, remote `0bac376`), **fase 7 SELESAI 2026-11-18
> (uji perangkat user: Map bekerja dengan benar)**; perbaikan pasca-fase-7
> (grid latar Map + switcher mengambang) dibuat, menunggu push/build ·
> **Terakhir ditinjau:** 2026-11-18 · **HEAD:** `0bac376` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 5 · [data-model.md](../data-model.md) · [ui.md](../ui.md)
>
> **Asal:** dieksplorasi user 2026-11-18 dari screenshot software Kanvaz
> (pengalih tampilan Board / Map / Scratch). Bukan janji — dipahami dulu,
> dibangun nanti bila user membuka kembali.

## 1. Tujuan

Map adalah **tampilan kedua di atas data Board yang sama**, bukan papan kedua
dan bukan salinan media. Tiap gambar yang sudah di-import muncul sebagai kartu
node; node dihubungkan garis bertipe; tiap node bisa diberi catatan. Board
menjawab "di mana gambar ini ditaruh", Map menjawab "gambar ini berhubungan
dengan apa".

## 2. Keadaan sekarang (fakta kode)

- `Board` + `CanvasItem` + `Edge` (DB v10, 3 tabel): item punya `boardId`,
  `fullPath`, `thumbPath`, `posX/posY`, ukuran, skala, rotasi, `zIndex`, flip,
  `isGrayscale`, `mapX`/`mapY` (posisi node Map, O1), `note` (O2); `Edge`
  menghubungkan dua item dengan `type` (`DroidCanvasDatabase.kt`).
- Dua tampilan: kanvas tak terbatas (Board) + Map (pengalih Board/Map di
  panel kontrol atas).
- Media sudah single-copy: berkas full + thumbnail di `filesDir`, dipakai
  ulang oleh kanvas dan daftar layer — Map tinggal memakai sumber yang sama.
- Undo/redo lewat tombol; tidak ada gesture yang berebut dengan gesture Map
  (gesture tiga jari sudah dihapus 2026-11-18, remote `0386188`).

## 3. Mekanisme yang diamati di Kanvaz (screenshot user 2026-11-18)

- Pengalih tampilan di atas: Board / Map / Scratch. (Scratch belum
  dieksplorasi — di luar cakupan rancangan ini.)
- Node = kartu berisi thumbnail, nama berkas, label IMAGE, tombol **+** di
  bawah kartu, dan port lingkaran di sisi kartu.
- Menghubungkan dua node memunculkan picker **Connection type** — 8 jenis,
  masing-masing dengan warna sendiri (lihat K3).
- Garis berupa kurva bezier yang fleksibel: mengikuti node saat node digeser,
  label jenis tampil di tengah garis.
- Tombol **+** di bawah gambar menambah catatan yang menempel di node itu:
  tampil sebagai balon di bawah kartu + ikon komentar kecil di kartu.
- Angka `1/8`, `2/8` di kartu: **artinya belum dipastikan dari screenshot**
  (kemungkinan urutan gambar atau hitungan koneksi) — bukan bagian mekanisme
  inti, jangan ditebak; bila dibutuhkan, cek dulu di software aslinya.

## 4. Keputusan

| # | Keputusan | Pilihan | Status |
|---|---|---|---|
| K1 | Sumber media | Sama dari Board / salinan baru | **Diputuskan user:** sama dari Board, tidak digandakan |
| K2 | Posisi node di Map | Posisi kanvas (`posX/posY`) / layout sendiri | **Diputuskan user 2026-11-18:** layout sendiri |
| K3 | Jenis koneksi | 8 jenis Kanvaz / dipangkas | **Diputuskan user:** 8 jenis persis — Related To (ungu muda), Inspired By (ungu), Derived From (biru), Alternative To (kuning/oranye), Supports (hijau), Used In (merah), References (pink), Plain (abu-abu) |
| K4 | Panah arah | Ada panah / garis polos | **Diputuskan user:** garis polos saja, tanpa panah — walau jenisnya berarah |
| K5 | Geometri garis | Disimpan / turunan saat render | **Dari pengamatan:** turunan — yang disimpan hanya dari, ke, dan jenis; kurva digambar ulang tiap frame dari posisi node |
| K6 | Notes | Per node via tombol + / tanpa notes | **Dari pengamatan:** per node via tombol +; balon di bawah kartu + ikon komentar |
| K7 | Status | Bangun sekarang / pahami dulu | **Diputuskan user 2026-11-18:** cukup pahami dulu, diparkir |
| K8 | Kebebasan Map | Scroll saja / se-bebas Board | **Diputuskan user 2026-11-18:** se-bebas Board (ikut Kanvaz) — Map punya pan/zoom sendiri dan node bisa digeser bebas; grid fase 2 hanya penempatan awal |

**Diputuskan 2026-11-18** (disetujui user — semua seperti rekomendasi):

| # | Keputusan | Hasil |
|---|---|---|
| O1 | Penyimpanan posisi node Map | Kolom `mapX`/`mapY` di `CanvasItem` |
| O2 | Penyimpanan notes | Kolom teks `note` di `CanvasItem` (satu catatan per item) |
| O3 | Aturan hapus | Hapus gambar → edge + note + posisi Map ikut hapus |
| O4 | Notes di Board | Map saja (v1) |
| O5 | Arti counter `1/8` | Tidak ditiru di v1 (artinya belum jelas) |

## 5. Rancangan

- **Satu sumber kebenaran tetap:** satu baris `CanvasItem`, satu pasang berkas
  gambar. Board dan Map hanya dua presentasi atas data yang sama. Menghapus
  gambar di Board harus menghapus edge, note, dan posisi Map-nya (lihat O3).
- **Layout sendiri (K2)** berarti posisi node Map disimpan terpisah dari
  `posX/posY` kanvas — menggeser node di Map tidak menggeser gambar di Board,
  dan sebaliknya.

### To-do

| # | Tugas | Status |
|---|---|---|
| T1 | Entity `Edge` (`id`, `boardId`, `fromItemId`, `toItemId`, `type`) + DAO + migrasi berikutnya (9→10 bila dikerjakan sebelum pembersihan kolom mati; geser bila kolom mati didahulukan — lihat `roadmap.md` langkah 0) | Selesai (fase 1) |
| T2 | Kolom `mapX`/`mapY` di `CanvasItem` (O1) — migrasi | Selesai (fase 1) |
| T3 | Kolom teks `note` di `CanvasItem` (O2) — migrasi | Selesai (fase 1) |
| T4 | Enum `ConnectionType`: 8 jenis + warna + label (K3) | Selesai (fase 1) |
| T5 | Pengalih tampilan Board/Map + kartu node (thumbnail, nama, label IMAGE, tombol +, port di sisi) | Selesai (fase 2; tombol + ikut fase 4) |
| T6 | Render edge: kurva bezier antar port, warna per jenis, label jenis di tengah garis; tanpa panah (K4, K5) | Selesai (fase 3) |
| T7 | Gesture Map (K8, fase 3): drag node — simpan `mapX`/`mapY` saat drag selesai; pan/zoom Map sendiri — geser ruang kosong = pan, pinch = zoom; buat edge: tarik dari port → picker jenis → simpan; hapus edge (tap garis / menu) | Selesai (fase 3) |
| T8 | UI notes: tap + → input teks → balon di bawah kartu + ikon komentar (K6) | Selesai (fase 4) |
| T9 | Hapus gambar → edge + note + posisi Map ikut hapus (O3); Save/Open: edge, notes, dan posisi Map ikut format proyek — lihat `designs/save-open.md` (format harus mencakup kolom baru ini) | Selesai (fase 5) |
| T10 | Uji manual di perangkat (tidak ada tes UI otomatis hari ini): buat edge 8 jenis, geser node (garis ikut), tambah note, hapus gambar (edge ikut hilang) | Menunggu uji perangkat (fase 7) |

> **Catatan implementasi fase 1 (2026-11-18):** O3 diwujudkan dengan FK
> `CASCADE` (`Edge` → `Board`, `Edge` → `CanvasItem` dua arah) — codebase
> sudah memakai `ForeignKey` dengan CASCADE (`CanvasItem` → `Board`), jadi
> tidak ada penghapusan eksplisit di jalur hapus item (`restoreSnapshot`,
> `clearCurrentBoard`, `deleteSelectedItems` tidak berubah). Konsekuensi:
> undo yang mengembalikan item yang sempat terhapus tidak mengembalikan
> edge-nya (edge ikut terhapus saat item dihapus). Migrasi 9→10 tunggal:
> 3 kolom baru + tabel `edges`.
>
> **Catatan implementasi fase 2 (2026-11-18):** T5 selesai — pengalih
> Board/Map (semula baris kedua panel kontrol atas, state lokal `MapViewMode` di
> `DroidCanvasScreen`, seperti `isSidebarExpanded`; **2026-11-18 dipindah jadi
> pill mengambang di bawah panel** — posisi sama, terpisah dari menu, panel
> jadi satu baris; posisi dari tinggi panel yang diukur `onSizeChanged`) + `MapView.kt` baru
> (kartu node: thumbnail `thumbPath`, nama "Image #N" seperti daftar layer,
> label IMAGE, port kiri/kanan di luar kartu supaya tidak terpotong clip;
> thumbnail ikut grayscale bila `isGrayscale`, sama seperti kanvas/layer).
> Yang TIDAK ikut fase 2 (sengaja): tombol `+` tidak dibuat — fungsinya
> notes (T8/fase 4), tombol mati dilarang; **geser node tidak
> diimplementasikan — tidak ada to-do yang mencantumkannya** (T10 hanya
> mengujinya). Diputuskan user 2026-11-18 (K8): ikut fase 3 — Map se-bebas
> Board (pan/zoom sendiri + drag node bebas); T7 diperbarui. Penempatan awal node yang
> belum diposisikan (`mapX`/`mapY` = 0): grid deterministik dari indeks
> item (urutan `zIndex`); (0, 0) adalah sentinel "belum diposisikan" — node
> yang digeser tepat ke (0, 0) kembali ke slot grid-nya. Area Map bisa
> di-scroll vertikal supaya semua kartu terjangkau (scroll ini DIHAPUS di
> fase 3 — Map kini ruang bertransformasi kamera, lihat catatan fase 3).
> Gesture pan/zoom kanvas
> dimatikan saat mode Map (`pointerInput(viewMode)` + gate
> `pastTouchSlop`), jadi menggeser area Map tidak menggerakkan Board yang
> tersembunyi. Bottom bar dibiarkan apa adanya (aksi-aksinya milik Board;
> di mode Map tetap berlaku untuk Board, tidak terlihat di Map).
>
> **Catatan implementasi fase 3 (2026-11-18):** T6 + T7 selesai — render
> edge (kurva bezier antar port yang saling berhadapan, warna per jenis,
> label jenis di tengah garis via `TextMeasurer`, tanpa panah; digambar DI
> BAWAH kartu node) + gesture Map (K8): satu loop `awaitEachGesture` untuk
> seluruh area Map — sentuhan di port (radius sentuh 28dp) mulai buat edge,
> di kartu drag node, di ruang kosong pan/zoom; tap pada garis membuka
> konfirmasi hapus. Kamera Map TERPISAH dari kamera Board: state di
> ViewModel + persist debounce 1 dtk ke `canvas_view_prefs`
> (`map_scale/trans_x/trans_y_$boardId`, pola sama seperti kamera Board),
> dimuat di `setCurrentBoardId` — jadi bertahan saat pindah Board↔Map dan
> saat aplikasi dibuka ulang; skala awal 1, offset 0, batas zoom 0,01–10×
> (disamakan dengan Board 2026-11-18 atas permintaan user; semula 0,25–4×).
> `verticalScroll` fase 2 DIHAPUS — area Map kini ruang bertransformasi kamera (`graphicsLayer`). Drag node: override in-memory
> tiap frame (pola `updateSimplicityInMemory`), SATU tulis DB saat lepas
> (`commitMapPosition`); snapshot undo diambil SEKALI saat drag mulai
> (sebelum override) — `saveCurrentStateToUndo()` membaca `canvasItems`
> yang sudah termasuk override, jadi snapshot saat commit akan menangkap
> posisi yang sudah pindah dan membuat undo no-op. Undo posisi node aman
> untuk edge (`restoreSnapshot` update item per id, tidak menghapusnya).
> Buat edge: tarik dari port → lepas di kartu lain → picker 8 jenis
> (titik warna + label, pola `AlertDialog` yang sudah ada); lepas di ruang
> kosong atau di node yang sama = batal. Garis tanpa panah = tak berarah,
> jadi duplikat (pasangan sama + jenis sama, arah mana pun) ditolak dengan
> Toast. Sengaja TIDAK dibuat: fling/inersia saat pan Map (Board punya;
> v1 cukup tanpa itu), dan tombol `+` notes tetap fase 4.
>
> **Catatan implementasi fase 4 (2026-11-18):** T8 selesai — tombol `+`
> lingkaran di bawah kartu (selalu ada) + balon note di bawah kartu bila
> `note` tidak kosong (tinggi tetap 56dp, maks 2 baris + ellipsis; teks
> penuh ada di dialog edit) + ikon komentar kecil `ChatBubbleOutline` di
> baris nama kartu bila ada note. Tap `+` atau tap balon membuka dialog
> yang sama (`AlertDialog` + `OutlinedTextField`, pola dialog fase 3;
> judul Add/Edit note, tombol Save/Cancel); menyimpan teks kosong
> menghapus note (tidak ada balon). Notes HANYA di Map (O4) — tidak ada
> apa pun soal note di tampilan Board. Simpan lewat `updateNote` di
> ViewModel (snapshot undo dulu seperti `togglePinItem`, lalu
> `updateCanvasItem(item.copy(note = ...))`; menyimpan note yang tidak
> berubah = no-op supaya tidak mengotori undo stack). RISIKO INTERAKSI:
> loop gesture fase 3 memakai `awaitFirstDown(requireUnconsumed = false)`,
> jadi sentuhan pada tombol/balon tetap sampai ke loop itu — ditangani
> dengan mode `IGNORED`: `controlsAt` menguji persis area balon (kotak)
> dan tombol `+` (lingkaran radius sentuh 28dp) SEBELUM `nodeAt`, sehingga
> sentuhan yang dimulai di sana tidak menggeser node maupun menggeser map,
> dan karena loop tidak mengonsumsi apa pun, `onClick` kontrol tetap
> jalan saat tap. `nodeAt` sengaja TETAP hanya kartu (188dp) — sentuhan
> pada balon/tombol bukan drag node. Tinggi kontrol dibuat tetap
> (`noteGap` 8dp, `noteBubbleHeight` 56dp, `noteButtonSize` 36dp, satu
> sumber di atas `MapView`) supaya hit-test dan layout tidak bisa
> bergeser satu sama lain; celah 8dp antar elemen tetap pan seperti biasa.
> **Perbaikan grid (2026-11-18):** batasan di atas DIPERBAIKI — tinggi tiap
> baris grid kini = tinggi node tertinggi di baris itu (232dp tanpa note,
> 296dp dengan note), dihitung dari konstanta yang sama dengan layout dan
> hit-test (`cardHeight`, `noteGap`, `noteBubbleHeight`, `noteButtonSize`
> via `nodeHeightPx`/`gridRowTopYPx`); posisi y = `startY` + jumlah tinggi
> baris di atasnya. Menambah note ke node di baris 0 menggeser semua baris
> di bawahnya ke bawah; menghapus note mengembalikannya. Hanya node yang
> belum diposisikan yang memakai grid — tinggi baris hanya dihitung dari
> node-node itu (node yang sudah digeser tidak ikut mendorong baris).
> Jeda 16dp antar baris ditambahkan 2026-11-18 (pitch = tinggi node +
> `cellGap`) — baris tidak lagi bersentuhan persis.
>
> **Catatan implementasi fase 5 (2026-11-18):** T9, separuh pertama —
> verifikasi O3 end-to-end (statis). Semua jalur hapus Board/CanvasItem
> diinventarisasi dan semuanya tercakup FK CASCADE, jadi TIDAK ADA
> perubahan kode di fase ini:
>
> | Jalur hapus | Mekanisme | Edge ikut hapus? |
> |---|---|---|
> | `deleteItem` — satu gambar (`DroidCanvasScreen.kt:3394, 3706, 5487`) | `deleteCanvasItem` → DAO `@Delete` | Ya — FK CASCADE `Edge.fromItemId/toItemId → CanvasItem.id` |
> | `deleteSelectedItems` — multi-select (Screen:1006) | sama, per item | Ya, sama |
> | `clearCurrentBoard` (Screen:2992) | sama, per item | Ya, sama |
> | `deleteBoard` (Screen:2324, 4253) | `deleteBoard` → DAO `@Delete`; item TIDAK dihapus eksplisit dulu | Ya — dua jalur CASCADE: `Edge.boardId → Board.id` langsung, dan `CanvasItem.boardId → Board.id` lalu `Edge → CanvasItem` |
> | `restoreSnapshot` (undo/redo) | item yang tidak ada di target dihapus via `deleteCanvasItem` | Ya, sama |
>
> note + `mapX`/`mapY` adalah kolom di baris `CanvasItem`, jadi ikut
> terhapus bersama barisnya di semua jalur. Tidak ada jalur yang melewati
> CASCADE: tidak ada `DELETE FROM canvas_items`/`boards` mentah di mana
> pun (satu-satunya DELETE SQL adalah 3 `@Delete` DAO + 2 query eksplisit
> `DELETE FROM edges`, yang justru menghapus edge itu sendiri); builder
> Room tidak memanggil `setForeignKeyConstraintsEnabled(false)` — Room
> mengaktifkan FK secara default, jadi CASCADE berlaku. Helper eksplisit
> `deleteEdgesForItem`/`deleteEdgesForBoard` ada tapi tidak dipanggil
> jalur hapus mana pun (CASCADE yang bekerja; helper itu cadangan).
> Konsekuensi fase 1 tetap berlaku: undo yang mengembalikan item yang
> sempat terhapus TIDAK mengembalikan edge-nya (snapshot hanya berisi
> item). Batas verifikasi: statis saja (tanpa Android SDK di VM) —
> penutup runtime-nya adalah T10 (hapus gambar di perangkat → edge ikut
> hilang). Separuh kedua T9 — format Save/Open mencakup data Map —
> dicatat di `designs/save-open.md` §4.1–§4.2.
>
> **Catatan implementasi fase 6 (2026-11-18):** verifikasi statis
> menyeluruh lintas fase atas seluruh Map (diff `f3bfbdc..HEAD`, 8 file)
> — daftar periksa: (1) wiring migrasi 9→10/DAO/repository/`edges`
> StateFlow/MapView terhubung; (2) grid fix di kedua jalur posisi
> (`positions` render + `positionsOf` loop gesture memakai
> `gridRowTopYPx` yang sama; tidak ada sisa `cellHeightPx`; baris kosong
> tetap 0dp); (3) undo — snapshot drag sekali di awal drag sebelum
> override, `updateNote` no-op bila tak berubah, `restoreSnapshot` tidak
> mengembalikan edge (terdokumentasi di catatan fase 1 + 5); (4) Board
> tidak rusak — diff `DroidCanvasScreen.kt` hanya switcher + gate gesture
> + wrapper `if (viewMode == BOARD)` + `edges` StateFlow; (5) K1–K8/O1–O5
> ditegakkan (8 jenis persis, tanpa panah, layout sendiri, note hanya di
> Map, counter `1/8` tidak ditiru, kamera Map terpisah (batas zoom disamakan
> dengan Board 0,01–10× pada 2026-11-18; semula 0,25–4×), fling Map
> sengaja tidak dibuat); (6) scan kode mati — tidak ada TODO/FIXME/print,
> semua import Map terpakai (catatan: `getValue`/`setValue` dipakai
> implisit lewat delegasi `by`, bukan tidak terpakai). Perbaikan kecil:
> (a) `updateEdge` DIHAPUS (DAO + repository + ViewModel) — tidak ada
> pemanggil; tidak ada UI edit jenis edge; (b) target lepas buat edge
> kini seluruh visual node (kartu + balon/tombol note), bukan hanya
> kartu — melepas di balon/tombol node lain tidak lagi membatalkan edge.
> Dokumen diselaraskan: status T1–T9 selesai/T10 menunggu, `roadmap.md`
> langkah 5 + tabel Planned, `README.md` (tetap belum Completed — fase 7
> belum lolos), `data-model.md` (DB v10 + tabel `edges` + kolom baru;
> catatan `isGrayscale` yang basi sejak 2026-11-17 ikut diperbaiki).
> TIDAK ADA temuan besar — tidak ada yang ditunda ke laporan terpisah.
>
> **Perbaikan pasca-fase-7 (2026-11-18, dari uji perangkat user):**
> (a) **Grid latar Map beku** — grid latar (CANVAS GRID: dots/lines/graph)
> digambar sekali di Screen (`.drawBehind` Box luar) dari kamera Board
> (`canvasScale`/`canvasTranslateX/Y`), jadi tidak ikut zoom/geser saat
> kamera Map yang bergerak. Perbaikan: `drawBehind` memilih kamera sesuai
> `viewMode` (Map → `mapScale`/`mapTranslateX/Y`, Board → kamera Board);
> rumus posisi grid (`screen = content × scale + trans`) sama dengan
> transformasi kamera Map, jadi grid sejajar dengan node Map.
> (b) **Switcher mengambang** — atas permintaan user, switcher Board/Map
> keluar dari panel (panel jadi satu baris) dan menjadi pill `AnimatedVisibility`
> terpisah di bawah panel, di atas canvas, posisi sama seperti sebelumnya
> (tengah atas). Offset dari tinggi panel yang diukur (`onSizeChanged`,
> `controlPanelHeightPx` + jeda 8dp), animasi masuk/keluar + sembunyi saat
> fullscreen sama seperti panel. `testTag` tidak berubah
> (`view_mode_switcher` / `view_mode_board` / `view_mode_map`).
> (c) **Viewport Controller mati di Map (2026-11-19)** — Reset View,
> Fit All, dan baris Zoom/Pan di sidebar hanya menyentuh kamera Board
> (`canvasScale`/`canvasTranslateX/Y`); pil zoom bar atas (klik = Fit)
> juga. Perbaikan: `resetZoom`/`fitToContent` di ViewModel mendapat
> parameter `isMapMode` (pola yang sama dengan `ZoomPercentageText`) —
> di Map, operasikan `mapScale`/`mapTranslateX/Y`; Fit All di Map
> menghitung kotak dari `mapX/mapY` + ukuran kartu node (180×188dp +
> catatan, lihat `nodeHeightPx`); baris Zoom/Pan menampilkan nilai
> kamera aktif; batas atas skala Fit di Map 10 (Board tetap 3,0).
> `SidebarContent` kini menerima `viewMode` sebagai parameter.

## 6. Yang sengaja tidak dirancang di sini

- Tab **Scratch** (Kanvaz) — mekanismenya belum dieksplorasi user.
- Layout otomatis node Map — K2 memutuskan layout sendiri (manual); auto-layout
  adalah fitur terpisah bila nanti diminta.
- Counter `1/8` di kartu — lihat O5.
