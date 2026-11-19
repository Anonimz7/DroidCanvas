# Rancangan: Grayscale Toggle

> **Status:** Rancangan — belum disetujui, belum dikerjakan ·
> **Terakhir ditinjau:** 2026-11-17 · **HEAD:** `95da467` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 4 · [decisions.md](../decisions.md) ADR-010 · [gaps.md](../gaps.md) · [data-model.md](../data-model.md)
>
> **Implementasi 2026-11-17 (working tree, belum commit, belum uji perangkat):**
> flag persisten + migrasi 8→9 + `ColorFilter` di `AsyncImage` kanvas dan
> thumbnail layer + tombol di tab LAYERS. Keputusan yang dipakai: K1
> persisten, K2 sebaris flip, K3 values menang, K4 implementasikan.

## 1. Tujuan

Satu gambar bisa ditampilkan abu-abu (grayscale) dengan satu toggle —
tanpa mengubah berkasnya, tanpa merusak values study. README pernah
mencentang fitur ini sebagai selesai; kenyataannya tidak ada kodenya sama
sekali (lihat ADR-010).

## 2. Keadaan sekarang (fakta kode)

- `isGrayscale` pernah ada: ditambah migrasi 5→6, **dihapus** migrasi 7→8
  saat tabel `canvas_items` dibangun ulang
  (`DroidCanvasDatabase.kt:135`, `:148+`). Entity hari ini tidak punya
  field itu.
- `ColorMatrix` / `ColorFilter` diimpor tapi **tidak dipakai**
  (`DroidCanvasScreen.kt:164-165`).
- Render gambar: `AsyncImage` dengan `colorFilter = null`
  (`DroidCanvasScreen.kt:3228`). Di sinilah filter akan dipasang.
- **Grayscale ≠ values study.** Values study (`isValuesEnabled`) mengganti
  gambar dengan bitmap posterized dari `ImageValueProcessor` (blur +
  luminance Rec.709 + rentang tonal, `DroidCanvasScreen.kt:3214`) dan
  punya toggle sendiri ("Study Values (Posterized)", `:3538`). Grayscale
  adalah luminance polos — lebih sederhana, tujuan berbeda.
- Pola toggle yang ada untuk ditiru: flip (`flipHorizontal` /
  `flipVertical`, kolom persisten + toggle di UI) dan values study
  (`toggleValues`, `DroidCanvasViewModel.kt:1210`).

## 3. Keputusan yang harus diambil dulu

| # | Keputusan | Pilihan | Rekomendasi |
|---|---|---|---|
| K1 | Sifat flag | Persisten per item (seperti flip) / hanya sesi | **Persisten** — konsisten dengan flip; ikut tersimpan dan ikut diekspor |
| K2 | Tempat toggle | Tab LAYERS, sebaris flip / menu per item | **Tab LAYERS, sebaris flip** — satu tempat untuk semua transformasi per item |
| K3 | Bila values study aktif | Values menang / grayscale menimpa | **Values menang** — values study mengganti gambar; grayscale hanya berlaku di jalur render normal |
| K4 | Alternatif: hapus saja | Implementasikan / nyatakan values study sebagai pengganti | **Implementasikan** — values study bukan pengganti (posterized ≠ abu-abu polos) |

## 4. Rancangan

### 4.1 Data

- Tambah `isGrayscale: Boolean = false` di `CanvasItem` + migrasi 8→9
  (`ALTER TABLE canvas_items ADD COLUMN isGrayscale INTEGER NOT NULL
  DEFAULT 0`). Satu kolom, satu migrasi — jangan mengulang pola
  tambah-lalu-hapus migrasi 5→8.
- Flag ini ikut manifest Save/Open (lihat `save-open.md` —
  skema sudah mencantumkannya).

### 4.2 Render

```kotlin
colorFilter = if (latestItem.isGrayscale)
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
else null
```

- `setToSaturation(0f)` = grayscale luminance Rec.709 — standar, tanpa
  proses bitmap baru (berbeda dengan values study yang memproses bitmap).
- Pasang di `AsyncImage` (`DroidCanvasScreen.kt:3228`), jalur normal
  saja. Jalur values study (`:3214`, `Image` bitmap posterized) tidak
  disentuh (K3).
- `graphicsLayer` flip tetap seperti sekarang — flip dan grayscale
  saling bebas (flip membalik, grayscale mengubah warna).

### 4.3 UI + ViewModel

- Toggle di tab LAYERS, sebaris dengan flip (K2). Label polos:
  "Grayscale".
- `ViewModel.toggleGrayscale(item)` mengikuti pola `toggleValues`
  (`:1210`): balik flag, simpan ke Room. **Sebelum mengubah, cek pola
  toggle flip/values — apakah mereka memanggil
  `saveCurrentStateToUndo()`; bila ya, grayscale wajib sama (undo harus
  bisa membatalkan toggle ini).**
- Toggle ini mengubah satu item; tidak ada toggle massal di v1
  (toggle massal values study ada di `:776` — grayscale massal bukan
  bagian fitur ini).

## 5. To-do

| # | Kerja | Target / bukti selesai |
|---|---|---|
| T1 | Putuskan K1–K4 (bagian 3) | Keputusan tercatat di `decisions.md` (perbarui ADR-010) |
| T2 | Cek pola undo pada toggle flip/values | Jawaban 2026-11-17: **ya** — `toggleFlipHorizontal`/`toggleFlipVertical` (`DroidCanvasViewModel.kt:1191/1200`) dan `toggleValuesEnabled` (`:1210`) semuanya memanggil `saveCurrentStateToUndo()` dulu; `toggleGrayscale` mengikuti pola yang sama |
| T3 | Migrasi 8→9 + field `isGrayscale` di entity | DB lama (v8) naik ke v9 tanpa kehilangan data |
| T4 | Render `ColorFilter` di `AsyncImage` (`:3228`) | Gambar abu-abu saat toggle nyala; flip tetap berfungsi bersamaan |
| T5 | Toggle di tab LAYERS + `toggleGrayscale()` + undo | Toggle bisa di-undo; state bertahan setelah restart (persisten) |
| T6 | Uji interaksi dengan values study | Values aktif → gambar posterized (bukan abu-abu polos); values mati → grayscale berlaku |
| T7 | Uji manual: nyala/mati, flip, restart, Save/Open | Flag ikut dalam arsip proyek dan kembali utuh |
| T8 | Perbarui `roadmap.md` (langkah 4 → selesai), `data-model.md`, `decisions.md` (ADR-010) | Dokumen konsisten dengan kode |

## 6. Di luar lingkup

- Grayscale untuk goresan (strokes) — hanya gambar.
- Slider saturasi / abu-abu sebagian — satu toggle dulu.
- Mengubah values study — fitur yang sudah jadi, jangan disentuh.
- Menghapus klaim grayscale dari roadmap — langkah ini yang
  mengembalikannya sebagai selesai, dengan kode.
