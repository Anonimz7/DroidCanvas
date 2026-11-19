# Todo berbasis kanvas — objek kanvas seperti gambar & goresan

> **Terakhir ditinjau:** 2026-11-19 · **HEAD:** `c37d79d`
> **Status:** Dirancang 2026-11-19; **T1–T6 + R1–R3 selesai (kode)
> 2026-11-19**, T7–T8 belum. Dokumen ini hasil tiga kali koreksi user atas
> ide awal + satu koreksi konsep (K11) — baca §3 dulu sebelum mengubah apa
> pun.

## 1. Konsep dalam satu paragraf

Todo di DroidCanvas **bukan** aplikasi todo dan **bukan** mode/tampilan/tool
baru — todo adalah **satu jenis objek lagi di kanvas**, seperti gambar dan
goresan, seperti mode drawing menambahkan objek kotak atau segitiga.
Alurnya: buka Todo → muncul beberapa template (desain demo todo) → ketuk
template yang diinginkan → muncul notif (konfirmasi) → kalau dijawab ya,
template itu muncul di kanvas. Yang disediakan hanya **blank todo** —
komponen kosong yang diisi manual. Tidak ada seret (drag-and-drop tadi hanya
cara menjelaskan); penambahan lewat **ketuk**. Di Board, todo tampil sebagai
kartu; di Map, todo tampil seperti di Board tapi bisa di-connect dot-to-dot
seperti media gambar — jadi node Map yang bisa jadi ujung edge. Board dan
Map sendiri tidak berubah konsepnya. **Koreksi konsep (K11, 2026-11-19):**
todo **bukan satu entri** — satu todo = **judul** (kepala, tidak bisa
dicentang) + **daftar baris** di bawahnya (masing-masing bisa dicentang),
ditambah lewat **tombol + di tengah** di bawah daftar.

## 2. Fakta kode yang dipakai

Semua baris di bawah ini dibaca langsung dari kode di HEAD `d11aee5`
(2026-11-19), bukan dari ingatan.

- **Sidebar 3 tab** — `enum class SidebarTab { BOARD, DRAW, LAYERS }`
  (`DroidCanvasScreen.kt:4213`); label tab (`:4341-4343`): BOARD → "Board"
  (ikon `GridView`), DRAW → **"Paint"** (ikon `Edit`), LAYERS → "Layers"
  (ikon `Layers`).
- **Tab Paint** — cabang `SidebarTab.DRAW -> {` (`:5129`) berisi **satu**
  seksi saja: **"DRAWING TOOLS"** (`SidebarSectionHeader`, `:5132`) dengan
  Switch `viewModel.isDrawModeEnabled`. Kalau nyala: kartu berisi Live Brush
  Tip, pilihan warna, ukuran brush, pen/eraser, tombol Undo/Redo, dan di
  paling bawah tombol **"Clear Board Drawings"** (`:5426`,
  `viewModel.clearDrawingStrokes()`). Kalau mati: teks "Enable drawing mode
  to unlock pen options." Cabang DRAW berakhir sebelum `SidebarTab.LAYERS`
  (`:5441`) — **di bawah DRAWING TOOLS tidak ada seksi lain**.
- **Pen di drawing tool** — toolbar mengambang `DrawingToolbar` (`:3997`):
  tombol "Pen Tool" (ikon `Edit`), tombol "Eraser Tool" (ikon `Contrast`),
  6 warna preset, pengatur lebar. Plus tombol pen di bar bawah
  (`bottom_bar_pen_toggle`, `:1156`) yang menyalakan/mematikan draw mode.
- **Item kanvas** — `CanvasItem` (`DroidCanvasDatabase.kt`): `id`, `boardId`,
  `fullPath`, `thumbPath`, `posX/Y`, `width/height`, `scale`, `rotation`,
  `zIndex`, `isPinned`, `flipHorizontal/Vertical`, `isGrayscale`,
  `isValuesEnabled`, `simplicity: Int`, `stopsCount`, `stopsJson`,
  **`mapX/mapY: Float`**, **`note: String`**. DB **versi 10**.
- **Edge Map** — tabel `edges`: `id`, `boardId`, `fromItemId`, `toItemId`,
  `type: String` (= nama `ConnectionType`); tiga FK `onDelete = CASCADE`
  (board + kedua ujung → `canvas_items`) + indeks. `EdgeDao` (Flow + CRUD,
  tanpa update). 8 jenis koneksi (`ConnectionType.kt`) dengan warna sendiri.
  `edges: StateFlow<List<Edge>>` di ViewModel via `flatMapLatest` pada
  `_currentBoardId`. `DroidCanvasRepository(dao, edgeDao)` — satu-satunya
  tempat konstruksi di `MainActivity.kt:52`.
- **Node Map** — `MapView.kt`: kartu node 180dp × 188dp, lingkaran port 12dp
  di luar kartu, edge bezier antar port yang menghadap, label jenis di tengah
  kurva, tanpa panah. Satu loop `awaitEachGesture`: port → buat edge, kartu
  → geser node, lainnya → pan. Geser: gerak di memori per frame, **satu**
  tulis DB saat dilepas, snapshot undo **sekali** di awal drag. Penempatan
  awal: grid deterministik; item dengan `mapX == 0f && mapY == 0f` dianggap
  belum diposisikan.
- **Dialog note** — pola yang dipakai ulang untuk isi teks todo
  (`MapView.kt:640-695`): `AlertDialog` + `OutlinedTextField` (judul
  Add/Edit note, tombol Save/Cancel; simpan kosong = hapus). Simpan lewat
  `updateNote(item, note)` (`DroidCanvasViewModel.kt:1614`).
- **Kamera** — Board: `canvasScale`/`canvasTranslateX/Y`; Map:
  `mapScale`/`mapTranslateX/Y` (masing-masing persist ke `canvas_view_prefs`
  dengan debounce 1 dtk). Tengah tampilan yang terlihat = pusat viewport
  dalam koordinat kanvas, dihitung dari kamera yang aktif.
- **Undo** — snapshot penuh (maks 50), tumpukan terpisah untuk item dan
  goresan. Undo yang mengembalikan item terhapus **tidak** mengembalikan
  edge-nya (konsekuensi FK CASCADE, tercatat di `map.md`).
- **Save/Open** — manifest v1 (`designs/manifest-schema.md`),
  `formatVersion = 1`, ekstensi `.dc`; `ProjectSaver` (`org.json`, tanpa
  dependensi baru) + `ProjectOpener` (validasi dulu, lalu insert Board +
  item + edge dalam **satu** transaksi Room). Penambahan Map dulu tidak
  menaikkan `formatVersion` (field baru opsional) — preseden untuk todo.

## 3. Keputusan (disetujui user 2026-11-19)

| # | Keputusan | Hasil |
|---|---|---|
| K1 | Apa itu todo | **Objek kanvas**, sejajar gambar & goresan — bukan mode, bukan tool, bukan tampilan baru. "Tidak ada yang spesial." |
| K2 | Letak pemilih Todo | Tab **Paint**, seksi baru **di bawah DRAWING TOOLS** (seksi ke-2 dan terakhir di tab itu). |
| K3 | Template | Daftar template menampilkan **desain demo todo**; yang disediakan hanya **blank todo** — komponen kosong, diisi manual. |
| K4 | Cara tambah | **Ketuk**, bukan seret. (Drag-and-drop hanya analogi penjelasan.) |
| K5 | Konfirmasi | Setelah ketuk template, muncul **notif (dialog konfirmasi)**; kalau dijawab ya, todo muncul di kanvas. Berlaku setiap kali tambah. "Notif" di sini = dialog di aplikasi, **bukan** notifikasi sistem Android. |
| K6 | Posisi awal | **Tengah tampilan yang sedang terlihat** (pusat viewport dalam koordinat kanvas, dari kamera aktif); lalu digeser manual seperti gambar. |
| K7 | Tampilan | Board: **kartu todo** (kotak centang + teks). Map: **node seperti gambar** — kartu + port (dot) di sisinya, bisa di-connect dot-to-dot, bisa jadi ujung edge. |
| K8 | Per papan | Todo milik satu papan (satu workspace = satu set todo), seperti item dan edge. Belum diputuskan eksplisit — lihat P1. |
| K9 | Switch draw mode | **Todo TIDAK memakai `isDrawModeEnabled`.** Switch itu ada karena menggambar adalah *mode input*: saat nyala, overlay gambar (`DroidCanvasScreen.kt:906`) menangkap semua sentuhan kanvas dan mengubahnya jadi goresan. Tambah todo adalah *aksi diskrit* (ketuk template → konfirmasi → sisip di tengah) — tidak ada gesture kanvas yang ditafsir ulang, jadi tidak butuh mode. Konsekuensi: seksi TODO adalah **sibling** seksi DRAWING TOOLS, di luar gate `if (viewModel.isDrawModeEnabled)` (`:5150`); saat draw mode nyala, overlay tetap mengambil alih sentuhan sehingga kartu todo (seperti gambar) tidak bisa diketuk/digeser sampai draw mode dimatikan. |
| K10 | Lipat/buka seksi | **Seksi TODO bisa dilipat/dibuka seperti DRAWING TOOLS.** Pola yang sama persis: `SidebarSectionHeader` (chevron `KeyboardArrowDown` yang berputar 0°/−90° dengan spring, `:4156`) + state sendiri `isTodoExpanded` (default `true`, seperti semua seksi lain) + isi dibungkus `AnimatedVisibility` (`expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`). Bedanya: slot `action` di header dikosongkan (Drawing Tools memakainya untuk Switch; Todo tidak punya switch — K9). |
| K11 | Bentuk todo | **Satu todo = judul + daftar baris.** Judul cuma kepala (**tidak bisa dicentang**); di bawahnya garis pemisah, lalu baris-baris (masing-masing centang + teks), lalu tombol **+ di tengah** di bawah daftar (diubah 2026-11-19 dari rata kanan, atas permintaan user). Todo lama (satu entri `text`/`done`) **tidak dimigrasi — direset** (keputusan user 2026-11-19: "Biarin aja toh nanti direset"). Map menampilkan kartu yang **sama** seperti Board (bukan ringkasan). Interaksi: ketuk judul → ubah judul; ketuk baris → ubah baris (hapus baris dari dalam dialog itu); long-press kartu Board → menu Ubah judul / Hapus todo. (Penyimpangan dari usulan awal: hapus baris **bukan** long-press baris — hapus tinggal di dalam dialog ubah baris, supaya tidak ada hit-test per baris.) |

### Usulan (butuh kata user sebelum dibangun)

| # | Usulan | Rekomendasi |
|---|---|---|
| P1 | Cakupan todo | **Per papan** (K8) — konsisten dengan semua data lain; tiap workspace punya todonya sendiri. Alternatif (satu kanvas todo global) ditolak karena melanggar model "satu workspace = satu papan". |
| P2 | Tampilan selesai | Tetap terlihat, **coret + pudar** — posisi todo adalah informasi; todo yang selesai tetap menandai tempatnya. |
| P3 | Hapus / ubah teks | **Long-press** seperti hapus gambar (menu konteks) + ketuk teks/kartu untuk ubah lewat dialog (pola dialog note). |
| P4 | Undo | Todo **ikut snapshot undo** seperti item — tapi perlu baca dulu kode undo sebelum memutuskan (snapshot hari ini hanya item + goresan). |
| P5 | Model edge untuk todo | **DIPUTUSKAN 2026-11-19: opsi (a)** — satu tabel `edges` + kolom `fromKind`/`toKind`; kedua FK ke `canvas_items` DIBUANG (FK SQLite tidak bisa bersyarat — koreksi rancangan awal); hapus berantai di repository (transaksi, dibatasi kind). Lihat §4.3 + ADR-020. |

## 4. Rancangan

### 4.1 Data — entitas `Todo` + `TodoItem` (K11)

Alasan entitas sendiri (bukan kolom di `CanvasItem`, bukan `note`): note
itu satu teks per gambar dan menempel ke gambar; todo berdiri sendiri, punya
daftar baris yang masing-masing bisa selesai/belum, dan harus tetap ada
walau gambarnya dihapus. Satu kolom `note` tidak bisa menampung itu.

**`Todo` — satu judul** (K11: judul cuma kepala, tidak bisa dicentang):

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | Int (PK, autoGenerate) | Seperti `Edge.id` |
| `boardId` | Int (FK → `boards`, CASCADE) | Todo ikut hapus bila papan dihapus |
| `title` | String | Judul; kosong = belum diisi |
| `posX`, `posY` | Float | Posisi di Board (koordinat kanvas) — **titik tengah kartu**, bukan sudut kiri atas seperti `CanvasItem` (T2) |
| `mapX`, `mapY` | Float | Posisi di Map; `0f, 0f` = belum diposisikan → grid awal seperti item — **titik tengah kartu** (T2); `addTodoAtCenter` selalu mengisi keduanya, jadi sentinel tidak pernah terpicu untuk todo |
| `createdAt` | Long (epoch ms) | Urutan + stabilitas |

**`TodoItem` — satu baris dalam daftar** (tabel `todo_items`, FK
`todoId → todos.id` CASCADE — hapus todo menghapus baris-barisnya):

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | Int (PK, autoGenerate) | |
| `todoId` | Int (FK → `todos`, CASCADE) | Baris milik todo ini |
| `text` | String | Isi baris; kosong = belum diisi |
| `done` | Boolean | Selesai/belum (P2: coret + pudar, per baris) |
| `createdAt` | Long (epoch ms) | Urutan baris (menaik) |

- **Migrasi 12→13 (R1):** `DROP TABLE todos` + buat ulang `todos`
  (tanpa `text`/`done`) + `CREATE TABLE todo_items` + indeks
  `index_todos_boardId` / `index_todo_items_todoId`. DROP aman karena
  todo lama **tidak dimigrasi** (keputusan user K11: "biarin aja toh
  nanti direset") dan tidak ada tabel lain yang ber-FK ke `todos`
  (FK edge sudah dibuang di T5). Harus **persis** — migrasi salah
  memicu `fallbackToDestructiveMigration()` dan menghapus semua data
  (pelajaran yang sama seperti migrasi 9→10).
- **DAO** (`TodoDao`): Flow todo per papan + Flow `TodoItem` per papan
  (subquery `todoId IN (SELECT id FROM todos WHERE boardId = …)`,
  `ORDER BY createdAt ASC`) + insert/update/delete per baris, pola
  `EdgeDao`. **Repository** mendapat `todoDao` sebagai parameter ke-3
  (konstruktor `DroidCanvasRepository(dao, edgeDao, todoDao, database)`;
  satu-satunya tempat konstruksi `MainActivity.kt:52`).
- **ViewModel:** `todos: StateFlow<List<Todo>>` (db + override posisi
  Map, pola `canvasItems`) + `todoItems: StateFlow<List<TodoItem>>`
  (flat, tanpa override — baris tidak punya posisi); `addTodo` /
  `updateTodo` (judul) / `deleteTodo`; `addTodoItem` / `updateTodoItem` /
  `toggleTodoItem` / `deleteTodoItem`; geser posisi mengikuti pola Map
  (gerak di memori, satu tulis saat dilepas, snapshot undo sekali di awal
  drag — bila P4 disetujui). `toggleTodo` (todo utuh) **dihapus** di R1 —
  yang dicentang adalah baris, bukan judul.

### 4.2 UI tambah — seksi TODO di tab Paint

- Seksi baru di bawah kartu DRAWING TOOLS (cabang `SidebarTab.DRAW`,
  setelah kartu ditutup, sebelum `:5441`): header `SidebarSectionHeader`
  (pola seksi yang sama, dengan lipat/buka) + daftar template.
  **Lipat/buka (K10):** seperti DRAWING TOOLS — state sendiri
  `isTodoExpanded` (default `true`) + isi dibungkus `AnimatedVisibility`;
  slot `action` header dikosongkan (tidak ada Switch).
  **Penting (K9):** seksi ini di luar gate `if (viewModel.isDrawModeEnabled)`
  (`:5150`) — pemilih Todo bekerja walau draw mode mati; switch itu hanya
  mengunci kartu drawing tools ("Enable drawing mode to unlock pen
  options.").
- Daftar menampilkan **desain demo** (pratinjau visual kartu todo) dan
  satu entri yang bisa dipakai: **blank todo**. Entri demo lain tidak
  bisa diketuk (atau tidak ada — diputuskan saat membangun; v1 hanya
  blank todo yang berfungsi, sesuai K3).
- Alur ketuk (K4 + K5 + K6): ketuk blank todo → dialog konfirmasi
  ("Tambah todo kosong ke kanvas?") → ya → `addTodo` di **tengah tampilan**
  yang terlihat (kamera aktif: Board atau Map) → dialog isi judul (pola
  dialog note, judul "Judul todo") → kartu muncul; judul boleh
  dikosongkan dulu dan diisi belakangan (ketuk judul/kartu → dialog yang
  sama). Baris ditambah dari dalam kartu (tombol +) — lihat §4.3.

### 4.3 UI tampil — Board & Map

- **Board (R1, K11):** kartu todo digambar di atas kanvas Board
  (koordinat yang sama dengan gambar). Isi = `TodoListContent` (dipakai
  bersama Map): **judul tebal + garis pemisah + baris-baris** (centang +
  teks; P2: selesai = coret + pudar, per baris) + **tombol + di tengah**
  di bawah daftar. Tinggi kartu mengikuti isi (93 + 36 dp per baris).
  Ketuk judul → ubah judul; ketuk baris → ubah baris (hapus baris dari
  dalam dialog itu); ketuk centang → `toggleTodoItem`; ketuk + → tambah
  baris; ketuk area kosong kartu → ubah judul. Geser kartu → pindah
  (pola drag gambar/Map). Long-press → menu Ubah judul / Hapus todo
  (P3, judul bukan teks); hapus dikonfirmasi (todo belum ikut undo — T7).
- **Map (R1, K11):** todo dirender sebagai node (`TodoNodeCard`,
  sejajar `MapNodeCard`): **kartu yang sama seperti Board** (bukan
  ringkasan) + lingkaran port 12dp di sisi kartu, di tengah judul (22dp).
  Gesture Map yang sudah ada dipakai ulang: seret kartu = pindah node;
  seret dari port = buat edge; ketuk garis = hapus edge (konfirmasi).
  Dialog judul/baris yang sama seperti Board (testTag
  `map_todo_title_input` / `map_todo_item_input`).
- **Masalah edge (P5 — DIPUTUSKAN 2026-11-19: opsi (a)).** Hari ini
  `Edge.fromItemId`/`toItemId` ber-FK ke `canvas_items`, jadi todo
  **belum bisa** jadi ujung edge. Opsi:
  - (a) Edge boleh menunjuk todo: tambah kolom penanda jenis ujung
    (`fromKind`/`toKind`: `ITEM`/`TODO`); **kedua FK ke `canvas_items`
    DIBUANG** — FK SQLite tidak bisa bersyarat, jadi id todo di kolom
    ber-FK akan ditolak database (koreksi dari rancangan awal yang
    mengira FK bisa dipertahankan untuk ujung ITEM). Hapus berantai
    pindah ke repository (transaksi): hapus gambar/todo → hapus
    garisnya; `deleteEdgesForItem`/`deleteEdgesForTodo` WAJIB dibatasi
    kind (id gambar dan id todo bisa sama angkanya). FK `boardId` tetap
    (hapus papan tetap menghapus garis di database). — **DIPILIH.**
  - (b) Tabel edge terpisah untuk todo (`todo_edges`) — duplikasi seluruh
    logika edge (render, gesture, hapus).
  - (c) Satukan id node (todo memakai ruang id yang sama dengan item) —
    rapuh, ditolak sebagai pola (id Room autoGenerate per tabel).
  - Rekomendasi awal: **(a)** — satu tabel edge, satu alur render/gesture,
    dengan hapus kaskade yang ditangani eksplisit untuk todo.

### 4.4 Save/Open — manifest ikut todo

- Manifest v1 bertambah `todos[]` **opsional** (tiap entri, K11:
  `title`, `posX`, `posY`, `mapX`, `mapY`, `createdAt`, + `items[]`
  bersarang — tiap baris: `text`, `done`, `createdAt`; tanpa berkas —
  todo tidak punya gambar). Edge manifest bertambah `fromKind`/`toKind`
  (default `ITEM` bila absen — arsip lama tetap bisa dibuka).
  `formatVersion` **tetap 1** (preseden Map).
- `ProjectSaver`: tulis `todos[]` + garis todo dari daftar ViewModel
  (dulu Saver T5 melewati garis ke/dari todo — ditutup T6, 2026-11-19).
  `ProjectOpener`: insert todo + baris dalam transaksi yang sama dengan
  Board + item + edge; validasi: `title` harus string, `done` harus
  boolean, posisi harus angka, `kind` harus `ITEM`/`TODO`.
- Arsip `.dc` lama (tanpa `todos`) tetap bisa dibuka — `todos` default
  kosong.

## 5. To-do (T1–T8)

| # | Pekerjaan | Status |
|---|---|---|
| T1 | Data: entitas `Todo` + migrasi 10→11 + `TodoDao` + repository (parameter ke-3) | **Selesai (kode) 2026-11-19:** remote `5f6db69`; build sukses (run 44491159914); `data-model.md` diperbarui (DB v11) |
| T2 | ViewModel: `todos` Flow + `addTodo`/`updateTodo`/`toggleTodo`/`deleteTodo` + posisi tengah tampilan | **Selesai (kode) 2026-11-19:** `todos` Flow (pola `edges`), `updateTodo`/`toggleTodo`/`deleteTodo` fire-and-forget tanpa snapshot undo (T7), `addTodoAtCenter` (suspend, kembalikan id) mengisi pusat Board + pusat Map sekaligus; posisi = titik tengah kartu |
| T3 | UI tambah: seksi TODO di tab Paint (di bawah DRAWING TOOLS) + daftar template demo + dialog konfirmasi + dialog isi teks | **Selesai (kode) 2026-11-19:** seksi TODO (K10: lipat/buka, `isTodoExpanded`) di luar gate draw mode (K9); kartu demo statis + entri Blank Todo → dialog konfirmasi → `addTodoAtCenter` → dialog isi teks (`todo_text_input`, simpan kosong dibolehkan) |
| T4 | Board: kartu todo (centang + teks) + geser + long-press | **Selesai (kode) 2026-11-19:** `TodoCardView` (kartu 220dp, centang + teks, selesai = coret + pudar — P2) di ruang kanvas Board; geser pola gambar (offset lokal + satu tulis DB saat dilepas via `updateTodoPosition`); ketuk kartu → dialog isi teks; long-press (P3) → menu Ubah teks/Hapus; hapus dikonfirmasi (todo belum ikut undo — T7); ketuk centang tidak membuka dialog edit (`down.isConsumed`). **R1 (K11) 2026-11-19:** isi kartu diganti `TodoListContent` (judul + daftar + tombol +); `toggleTodo` dihapus; dialog isi teks → dialog isi judul; menu long-press "Ubah teks" → "Ubah judul". |
| T5 | Map: node todo + port + geser + edge ke/dari todo | **Selesai (kode) 2026-11-19:** migrasi 11→12 (tabel `edges` dibuat ulang: `fromKind`/`toKind`, FK ke `canvas_items` dibuang, FK `boardId` tetap); `deleteCanvasItem`/`deleteTodo` repository transaksional (hapus garis dibatasi kind); `todos` Flow direstruktur seperti `canvasItems` (db + override Map) + `updateTodoMapPositionInMemory`/`commitTodoMapPosition`; `TodoNodeCard` (180×96dp, centang + teks, port tengah vertikal 48dp) + grid/hit-test/render Map disatukan lewat `MapNode`/`MapEndpoint`; edge gambar↔todo, todo↔todo, duplikat dicek per (kind, id); test `EdgeTodoCascadeTest` 5 kasus (**belum dieksekusi** — butuh `:app:testDebugUnitTest` JDK 21). **Gap sementara:** Saver T5 melewati garis ke/dari todo (manifest belum punya `todos[]`) — ditutup T6. **R1 (K11) 2026-11-19:** `TodoNodeCard` 180×96dp diganti kartu daftar (tinggi 93+36N dp, port di tengah judul 22dp); `EdgeTodoCascadeTest` diperbarui (`text` → `title`). |
| R1 | Data + ViewModel (K11): Todo jadi judul + `TodoItem` + migrasi 12→13 | **Selesai (kode) 2026-11-19:** `Todo.text`/`done` dihapus → `Todo.title`; entitas baru `TodoItem` (tabel `todo_items`, FK CASCADE); migrasi 12→13 DROP + buat ulang `todos` (todo lama **direset**, tidak dimigrasi); `TodoDao` + 4 passthrough repository; ViewModel `todoItems` Flow + CRUD baris (`addTodoItem`/`updateTodoItem`/`toggleTodoItem`/`deleteTodoItem`); `addTodoAtCenter(title, …)`; test baru `TodoItemTest` 5 kasus (**belum dieksekusi** — butuh `:app:testDebugUnitTest` JDK 21). |
| R2 | Board (K11): kartu jadi daftar + bubble note di Board + hapus badge | **Selesai (kode) 2026-11-19:** `TodoCardView` memakai `TodoListContent` (judul + daftar + tombol +); dialog isi judul (`board_todo_title_input`) + dialog baris (`board_todo_item_input`, hapus baris dari dalam dialog) + konfirmasi hapus baris; seksi TODO sidebar: dialog judul (`todo_title_input`) + kartu demo bentuk baru (judul + garis + 2 baris); **bubble note di Board** (teks note di bawah gambar, 56dp, ketuk → ubah note; badge ikon komentar dihapus; O4 direvisi — lihat ADR-022). |
| R3 | Map (K11): node jadi daftar + thumbnail ikut rasio media | **Selesai (kode) 2026-11-19:** `TodoNodeCard` memakai `TodoListContent` (kartu sama seperti Board) + 3 dialog todo Map (`map_todo_title_input` / `map_todo_item_input` / konfirmasi hapus baris); `mapThumbHeightDp` — thumbnail Map **ikut rasio media** bila `mapImageFull` nyala (tinggi = 180dp × h/w; gambar rasio layar ponsel jadi node tinggi, tampil utuh selebar 180dp, bukan mengecil di kotak 120dp); `fitToContent` Map memakai tinggi node sebenarnya (gambar + todo). |
| T6 | Save/Open: `todos[]` di manifest + Saver + Opener (satu transaksi) | **Selesai (kode) 2026-11-19:** Saver menulis `todos[]` (`uuid` baru per todo + `title` + posisi + `items[]` bersarang) + edge menulis `fromKind`/`toKind` selalu (garis todo tidak lagi dilewati); Opener memvalidasi kind + ujung TODO (aturan 7 + 9, kind absen = ITEM) + insert todo + baris + edge dalam transaksi yang sama; arsip tanpa `todos[]` tetap terbuka bersih. Test: `ProjectSaverTest` 3 baru (round-trip todo + 2 penolakan) + `ProjectOpenerTest` 3 baru (round-trip todo + 2 penolakan) — **belum dieksekusi** (butuh `:app:testDebugUnitTest` JDK 21). |
| T7 | Undo: todo ikut snapshot (butuh baca kode undo dulu — P4) | Belum |
| T8 | Uji manual di perangkat: tambah dari Board + dari Map, geser, centang, hapus gambar (todo tetap), hapus todo (edge ikut hilang), Save → Open (todo utuh) | Belum |

## 6. Di luar lingkup (v1)

- Tanggal tenggat, prioritas, pengingat/notifikasi sistem — v1 hanya teks +
  selesai + posisi.
- Daftar todo global / tampilan daftar (bukan kanvas).
- Template selain blank todo (desain demo hanya pratinjau, tidak bisa
  dipakai — K3).
- Sinkronisasi cloud / akun — aplikasi ini tanpa backend.
