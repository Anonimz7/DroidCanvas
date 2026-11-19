# Skema Manifest v1 — Save/Open Project File

> **Status:** Skema v1 — selesai (Fase 1, T2, 2026-11-19) ·
> **Merujuk:** [save-open.md](save-open.md) §4.1–§4.2 (rancangan) ·
> [../decisions.md](../decisions.md) ADR-015 (K1–K6) · [map.md](map.md) (data Map) ·
> **Contoh:** [manifest-example.json](manifest-example.json) (valid terhadap skema ini)

Dokumen ini adalah sumber kebenaran formal untuk `manifest.json` di dalam
arsip `.dc`. `save-open.md` §4.1 menjelaskan formatnya secara
naratif; di sini tiap field didefinisikan dengan tipe, kewajiban, nilai
default, dan aturan validasi yang dipakai `ProjectOpener` saat Open.

Istilah tipe mengikuti JSON: `string`, `number` (angka, boleh desimal),
`integer` (bilangan bulat), `boolean`, `object`, `array`.

## 1. Arsip (ZIP)

```
nama-papan.dc
├── manifest.json
└── images/
    ├── <item-uuid>_full.<ext>
    └── <item-uuid>_thumb.jpg
```

- `manifest.json` wajib ada di root arsip.
- `images/` berisi satu pasang berkas per item. Stem nama berkas
  **sama dengan `uuid` item** (`<uuid>_full.<ext>`,
  `<uuid>_thumb.jpg`). Ekstensi `_full` mengikuti berkas aslinya
  (jpg/png/gif/webp); `_thumb` selalu `.jpg`.
- Path absolut **dilarang** di manifest: semua rujukan berkas adalah nama
  relatif di dalam arsip. Nilai yang diawali `/` atau mengandung `..`
  ditolak saat Open.
- Id baris DB (`boardId`, `id` item) **tidak pernah** ada di manifest —
  id baru selalu dibuat saat Open (`save-open.md` §4.2).

## 2. Tingkat atas

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `formatVersion` | integer | Ya | Harus `1`. Nilai lain ditolak (K6). |
| `appVersion` | string | Ya | Versi aplikasi penulis arsip, mis. `"0.2.0"`. Hanya informasi. |
| `board` | object | Ya | §3. |
| `items` | array | Ya | §4. Boleh kosong (papan tanpa gambar). |
| `edges` | array | Tidak (default `[]`) | §5. Tidak ada = tanpa edge (arsip pra-Map). |
| `todos` | array | Tidak (default `[]`) | §6. Tidak ada = tanpa todo (arsip pra-T6). |
| `strokes` | array | Tidak (default `[]`) | §7. Tidak ada = tanpa goresan. |

Penulis (Save) selalu menulis ketujuh kunci; pembaca (Open) mentolerir
ketiadaan `edges`/`todos`/`strokes`.

## 3. `board`

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `name` | string | Ya | Nama papan. |
| `createdAt` | integer | Ya | Epoch milidetik saat papan dibuat. |
| `camera` | object | Ya | Kamera Board: `scale` (number), `translateX` (number), `translateY` (number). Sumber: SharedPreferences + ViewModel saat Save, bukan kolom mati `Board` (K1). |
| `mapCamera` | object | Tidak (default `{"scale": 1, "translateX": 0, "translateY": 0}`) | Kamera Map, bentuk sama dengan `camera`. Tidak ada = tampilan Map default. |

## 4. `items[]` — satu entri per gambar

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `uuid` | string | Ya | Kunci stabil di dalam arsip; sama dengan stem nama berkas gambar. Harus unik antar item. |
| `posX`, `posY` | number | Ya | Posisi di kanvas Board. |
| `width`, `height` | number | Ya | Ukuran item. |
| `scale` | number | Ya | Skala item. |
| `rotation` | number | Ya | Rotasi dalam derajat. |
| `zIndex` | integer | Ya | Urutan tumpuk. |
| `isPinned` | boolean | Ya | Item terkunci. |
| `flipHorizontal`, `flipVertical` | boolean | Ya | Cermin horizontal/vertikal. |
| `isValuesEnabled` | boolean | Ya | Values study aktif untuk item ini. |
| `simplicity` | integer | Ya | Nilai simplicity values study (0–100, sesuai `CanvasItem.simplicity: Int` + `coerceIn(0, 100)` di ViewModel). |
| `stopsCount` | integer | Ya | Jumlah stops values study. |
| `stopsJson` | string | Ya | Boleh `""`. Format dalamnya milik kode values study, bukan bagian skema ini. |
| `isGrayscale` | boolean | Ya | Tampilan grayscale aktif. |
| `mapX`, `mapY` | number | Tidak (default `0`) | Posisi node di Map. `0, 0` = belum diposisikan (sentinel grid Map) — Open mempertahankan `0` apa adanya. |
| `note` | string | Tidak (default `""`) | Catatan node Map. Kosong = tanpa catatan. |
| `fullFile` | string | Ya | `"images/<uuid>_full.<ext>"`. |
| `thumbFile` | string | Ya | `"images/<uuid>_thumb.jpg"`. |

## 5. `edges[]` — garis antar node Map

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `from`, `to` | string | Ya | `uuid` ujung garis — item atau todo, sesuai `fromKind`/`toKind`. Keduanya harus merujuk ke entri yang ada di manifest. |
| `type` | string | Ya | Nama `ConnectionType`, tepat satu dari 8: `RELATED_TO`, `INSPIRED_BY`, `DERIVED_FROM`, `ALTERNATIVE_TO`, `SUPPORTS`, `USED_IN`, `REFERENCES`, `PLAIN`. |
| `fromKind`, `toKind` | string | Tidak (default `ITEM`) | Jenis ujung: `ITEM` (merujuk `items[]`) atau `TODO` (merujuk `todos[]`). Arsip lama tanpa kind dibaca sebagai ITEM–ITEM. |

Garis tidak berarah (tanpa panah, K4 Map); `from`/`to` hanya menamai
kedua ujungnya. Duplikasi edge (pasangan + type sama) tidak ditolak
skema ini — penolakan duplikat adalah aturan UI saat membuat garis.

## 6. `todos[]` — todo berbasis kanvas (T6, K11)

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `uuid` | string | Ya | Kunci stabil di dalam arsip; kunci join edge + baris. Harus unik antar todo. Todo tidak punya berkas gambar. |
| `title` | string | Tidak (default `""`) | Judul todo (kepala, tidak bisa dicentang). Kosong = "Todo kosong" di UI. |
| `posX`, `posY` | number | Tidak (default `0`) | Posisi kartu di kanvas Board. |
| `mapX`, `mapY` | number | Tidak (default `0`) | Posisi node di Map. `0, 0` = belum diposisikan (sentinel grid Map). |
| `createdAt` | integer | Tidak (default `0`) | Epoch ms; menentukan urutan tampil todo. |
| `items` | array | Tidak (default `[]`) | Baris di bawah judul, urutan = urutan tampil. Tiap baris: `text` (string, default `""`), `done` (boolean, default `false`), `createdAt` (integer, default `0`). |

`todos[]` sendiri opsional di tingkat atas (absen = tanpa todo).
Posisi todo mengikuti pola item: posisi Board + posisi Map dipisah.

## 7. `strokes[]` — goresan

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `id` | string | Ya | Id goresan (unik di dalam arsip). |
| `color` | integer | Ya | Warna ARGB 32-bit sebagai bilangan bulat **bertanda** (sesuai `Int` Kotlin, mis. hitam = `-16777216`). |
| `width` | number | Ya | Lebar goresan. |
| `points` | array | Ya | Daftar pasangan `[x, y]` (number), minimal 1 pasangan, urutan = urutan goresan. |

Goresan ditulis sebagai JSON di manifest — format teks
`drawing_strokes_<id>.txt` tetap dipakai internal, format arsip tidak
mewarisi utangnya (G6).

## 8. Aturan validasi saat Open (K6)

`ProjectOpener` menolak arsip (tanpa papan setengah jadi) bila:

1. Berkas bukan ZIP valid, atau `manifest.json` tidak ada di root.
2. `manifest.json` bukan JSON valid.
3. `formatVersion` bukan `1` — pesan gagal menyebut versi yang ditemukan.
4. Field wajib hilang atau tipenya salah.
5. `uuid` item duplikat.
6. `fullFile`/`thumbFile` tidak ada di dalam arsip, stem-nya bukan
   `uuid` item, atau path-nya absolut / mengandung `..`.
7. Ujung `edges` merujuk ke `uuid` yang tidak ada di manifest (per
   kind: ITEM → `items[]`, TODO → `todos[]`), `fromKind`/`toKind`
   bukan `ITEM`/`TODO`, atau `type` bukan salah satu dari 8 nama di §5.
8. `points` goresan kosong atau berisi bukan pasangan angka.
9. `uuid` todo duplikat, atau baris todo (`text`/`done`/`createdAt`)
   tipenya salah.

Pesan gagal harus menyebut sebabnya, bukan "terjadi kesalahan".

## 9. Kompatibilitas

- **Arsip lama (pra-Map)** — tanpa `edges`/`mapX`/`mapY`/`note`/
  `mapCamera` — terbuka bersih: tanpa edge, `mapX`/`mapY` = 0,
  `note` = `""`, `mapCamera` default. Semua field Map opsional;
  `formatVersion` **tetap 1** (penambahan field opsional bukan versi
  format baru; K6 hanya menolak `formatVersion` yang tak dikenal).
- **Arsip pra-T6** — tanpa `todos[]` — terbuka bersih (tanpa todo);
  edge tanpa `fromKind`/`toKind` dibaca sebagai ITEM–ITEM.
- **Arsip baru dibuka aplikasi lama (pra-Map)** — kunci JSON yang tak
  dikenal diabaikan parser; papan terbuka tanpa data Map.
- Perubahan yang **menaikkan** `formatVersion` (bukan 1) hanya bila ada
  field wajib baru atau perubahan makna field lama — bukan untuk
  penambahan field opsional.
