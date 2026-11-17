# Rancangan: Tablet Optimization

> **Status:** Rancangan — belum disetujui, belum dikerjakan ·
> **Terakhir ditinjau:** 2026-11-17 · **HEAD:** `95da467` ·
> **Merujuk:** [roadmap.md](../roadmap.md) langkah 3 · [ui.md](../ui.md) · [canvas-engine.md](../canvas-engine.md)

## 1. Tujuan

Di tablet, aplikasi terasa seperti aplikasi tablet — bukan aplikasi ponsel
yang diperbesar. Hari ini satu-satunya adaptasi tablet adalah satu
breakpoint; selebihnya ponsel dan tablet menjalankan UI yang sama persis.

## 2. Keadaan sekarang (fakta kode)

- Satu breakpoint: `isWideScreen = maxWidth >= 720.dp`
  (`DroidCanvasScreen.kt:320`), di dalam `BoxWithConstraints`.
- Lebar: `ModalNavigationDrawer` 300.dp + `gesturesEnabled =
  !isWideScreen` (`:321-329`); sempit: drawer. Lebar: `Row` dengan
  sidebar tetap 301.dp + divider 1.dp (`:374-391`).
- Tidak ada dukungan stylus/pen khusus, tidak ada layout landscape
  khusus, tidak ada penanganan multi-window — semua itu tidak ada di kode,
  bukan hanya belum diuji.
- `viewportWidth` / `viewportHeight` sudah dilacak dari ukuran viewport
  (`DroidCanvasScreen.kt:391+`), jadi resize (mis. split-screen) sudah
  mengalir ke layout — tapi tidak ada yang bereaksi khusus terhadapnya.

## 3. Keputusan yang harus diambil dulu

"Optimal untuk tablet" harus didefinisikan sebelum satu baris kode
ditulis. Cakupan dipecah jadi tiga fase supaya bisa berhenti di tengah
tanpa menyisakan setengah fitur:

| Fase | Isi | Keputusan |
|---|---|---|
| **1 — Layout** | Panel ganda + tier lebar yang jelas | Wajib. Ini inti langkah ini. |
| **2 — Stylus** | Tekanan → lebar goresan, palm rejection | Opsional. Butuh perangkat stylus untuk menguji. |
| **3 — Multi-window** | Split-screen / freeform yang enak dipakai | Opsional. Bisa sesudah fase 1. |

| # | Keputusan | Pilihan | Rekomendasi |
|---|---|---|---|
| K1 | Fase yang dikerjakan | Fase 1 saja / 1+2 / 1+2+3 | **Fase 1 dulu**, putuskan 2–3 sesudahnya |
| K2 | Tier lebar | 600/840.dp (Material) / 720.dp saja | **600/840.dp** — 720.dp hari ini terlalu kasar |
| K3 | Panel ganda di tier atas | Sidebar + inspector LAYERS bersamaan | **Ya** — itu pembeda utama tablet |
| K4 | Perangkat uji | Tablet 7" / 10" / keduanya | **10" dulu** (paling umum untuk papan referensi) |

## 4. Rancangan

### 4.1 Tier lebar (fase 1)

| Tier | Lebar | Layout |
|---|---|---|
| Ponsel | < 600.dp | Seperti hari ini: drawer 300.dp |
| Tablet portrait | 600–839.dp | Sidebar tetap 301.dp (seperti `isWideScreen` hari ini) |
| Tablet landscape | ≥ 840.dp | **Panel ganda**: sidebar 301.dp + panel inspector (tab LAYERS) di sisi kanan, kanvas di tengah |

- Keputusan tier dipindah ke satu fungsi murni `LayoutMode(widthDp)` —
  bisa diuji tanpa perangkat, seperti `StrokeOptimizer`.
- `isWideScreen` (720.dp) diganti pemakaian `LayoutMode`; angka 720.dp
  tidak tersebar lagi di `DroidCanvasScreen.kt`.

### 4.2 Panel ganda (fase 1)

- Panel inspector memakai konten tab LAYERS yang sudah ada — **tanpa
  menduplikasi UI**: `SidebarContent` dipecah supaya tab LAYERS bisa
  dirender di dua tempat (drawer/sidebar dan panel kanan).
- Panel kanan bisa ditutup (tombol silang) → kembali ke satu sidebar.
- Kanvas tidak boleh mengecil sampai tidak terpakai: lebar minimum kanvas
  dijaga; bila panel ganda tidak muat, fallback ke satu sidebar.

### 4.3 Stylus (fase 2)

- Deteksi stylus lewat `PointerType.Stylus` di `pointerInput` yang sudah
  ada (mode gambar). Tekanan (`PointerInputChange.pressure`) dipetakan ke
  pengali lebar goresan — rentang diputuskan saat implementasi, bukan di
  dokumen ini.
- Palm rejection: saat stylus aktif, sentuhan jari di kanvas diabaikan
  (toggle di Pengaturan, default mati sampai teruji).
- Tanpa stylus fisik, fase ini **tidak bisa diuji** — jangan mulai tanpa
  perangkat.

### 4.4 Multi-window (fase 3)

- Tidak ada kode baru yang besar: `viewportWidth`/`viewportHeight` sudah
  mengalir. Pekerjaan fase ini adalah **pengujian + perbaikan**: resize
  ke split-screen tidak boleh merusak posisi kamera, sidebar tidak boleh
  menutupi kanvas di jendela sempit, dan tier lebar harus mengikuti lebar
  jendela — bukan lebar layar.

## 5. To-do

| # | Kerja | Target / bukti selesai |
|---|---|---|
| T1 | Putuskan K1–K4 (bagian 3) | Cakupan tercatat di `decisions.md` (ADR baru) |
| T2 | `LayoutMode(widthDp)` + ganti `isWideScreen` | Unit test: 599 → ponsel, 600 → tablet portrait, 840 → landscape |
| T3 | Pecah `SidebarContent` supaya tab LAYERS bisa dipakai ulang | Tidak ada duplikasi konten LAYERS |
| T4 | Panel ganda ≥ 840.dp + tombol tutup + fallback | Uji manual: 10" landscape → dua panel; tutup panel → satu sidebar |
| T5 | (Fase 2) Tekanan stylus → lebar goresan + toggle palm rejection | Uji manual dengan stylus fisik |
| T6 | (Fase 3) Uji split-screen / resize jendela | Kamera + sidebar benar di semua ukuran jendela |
| T7 | Matriks uji manual: ponsel, tablet 7", tablet 10", portrait/landscape | Semua tier menampilkan layout yang benar |
| T8 | Perbarui `roadmap.md` (langkah 3 → selesai), `ui.md`, `decisions.md` | Dokumen konsisten dengan kode |

## 6. Di luar lingkup

- Foldable / layar lipat — bukan tablet.
- Shortcut keyboard — fitur lain.
- Mengubah lebar sidebar 301.dp di tier lama tanpa alasan (itu perilaku
  yang sudah teruji).
