# Rancangan: Map view — node graph di atas Board

> **Status:** Rancangan lengkap — keputusan O1–O5 disetujui user 2026-11-18;
> belum dibangun · **Terakhir ditinjau:** 2026-11-18 · **HEAD:** `76f5f13` ·
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

- `Board` + `CanvasItem` (DB v9, 2 tabel): item punya `boardId`, `fullPath`,
  `thumbPath`, `posX/posY`, ukuran, skala, rotasi, `zIndex`, flip, `isGrayscale`
  — **tanpa satu pun field relasi antar item** (`DroidCanvasDatabase.kt:45-63`).
- Satu tampilan saja: kanvas tak terbatas. Tidak ada tab/pengalih tampilan
  Board vs Map.
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

| # | Tugas |
|---|---|
| T1 | Entity `Edge` (`id`, `boardId`, `fromItemId`, `toItemId`, `type`) + DAO + migrasi berikutnya (9→10 bila dikerjakan sebelum pembersihan kolom mati; geser bila kolom mati didahulukan — lihat `roadmap.md` langkah 0) |
| T2 | Kolom `mapX`/`mapY` di `CanvasItem` (O1) — migrasi |
| T3 | Kolom teks `note` di `CanvasItem` (O2) — migrasi |
| T4 | Enum `ConnectionType`: 8 jenis + warna + label (K3) |
| T5 | Pengalih tampilan Board/Map + kartu node (thumbnail, nama, label IMAGE, tombol +, port di sisi) |
| T6 | Render edge: kurva bezier antar port, warna per jenis, label jenis di tengah garis; tanpa panah (K4, K5) |
| T7 | Gesture buat edge: tarik dari port → picker jenis → simpan; hapus edge (tap garis / menu) |
| T8 | UI notes: tap + → input teks → balon di bawah kartu + ikon komentar (K6) |
| T9 | Hapus gambar → edge + note + posisi Map ikut hapus (O3); export/import: edge, notes, dan posisi Map ikut format proyek — lihat `designs/export-import.md` (format harus mencakup kolom baru ini) |
| T10 | Uji manual di perangkat (tidak ada tes UI otomatis hari ini): buat edge 8 jenis, geser node (garis ikut), tambah note, hapus gambar (edge ikut hilang) |

## 6. Yang sengaja tidak dirancang di sini

- Tab **Scratch** (Kanvaz) — mekanismenya belum dieksplorasi user.
- Layout otomatis node Map — K2 memutuskan layout sendiri (manual); auto-layout
  adalah fitur terpisah bila nanti diminta.
- Counter `1/8` di kartu — lihat O5.
