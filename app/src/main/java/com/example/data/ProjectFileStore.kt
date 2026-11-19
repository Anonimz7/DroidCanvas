package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Penyimpanan berkas proyek `.dc` di folder publik `Download/droidcanvas`
 * (Save ulang, 2026-11-19 — keputusan user: Save menyimpan ke penyimpanan,
 * bukan membuka share sheet).
 *
 * - API 29+: lewat MediaStore (`MediaStore.Downloads`, `RELATIVE_PATH`
 *   `Download/droidcanvas`) — tanpa izin apa pun, berkas milik aplikasi
 *   sendiri, kelihatan di file manager, dan TIDAK hilang saat aplikasi
 *   di-uninstall (tidak seperti `cacheDir` / `getExternalFilesDir`).
 * - Di bawah API 29: lewat File API ke folder publik yang sama
 *   (`WRITE_EXTERNAL_STORAGE` dibutuhkan di API 23–28; bila tidak ada,
 *   tulis gagal dengan pesan yang jelas).
 * - Nama berkas yang sama DITIMPA (Save menimpa target yang sama, seperti
 *   Save di desktop; Save As ke nama yang sudah ada juga menimpa).
 *   Caranya (2026-11-19): Uri berkas disimpan per papan dan dipakai
 *   langsung saat Save — tanpa lookup — karena lookup MediaStore
 *   (DISPLAY_NAME + RELATIVE_PATH) gagal di perangkat user, dua ejaan
 *   garis miring sudah dicoba. Lookup hanya jadi cadangan (berdasarkan
 *   nama saja, folder dicocokkan di kode).
 * - Kelas murni tanpa Room/ViewModel/Compose — hanya `ContentResolver` +
 *   `java.io`, seperti `ProjectSaver`.
 */
object ProjectFileStore {
    /** Nama folder di dalam Download. */
    const val FOLDER_NAME = "droidcanvas"

    /** `RELATIVE_PATH` MediaStore untuk folder di atas. */
    const val RELATIVE_PATH = "Download/droidcanvas"

    // MIME generik — sengaja BUKAN "application/zip": MediaStore
    // menempelkan ekstensi sesuai MIME bila ekstensi nama berkas tidak
    // dikenal untuk MIME itu, dan `.dc` bukan ekstensi yang dikenal untuk
    // `application/zip` — berkas jadi `X.dc.zip` (ditemukan 2026-11-19).
    // `application/octet-stream` tidak punya ekstensi kanonis, jadi nama
    // dibiarkan apa adanya. Isi berkas tetap ZIP; berbagi (Share) tetap
    // memakai `application/zip`.
    const val MIME_TYPE = "application/octet-stream"

    /**
     * Slug nama berkas: huruf kecil, spasi/whitespace → `_`, karakter
     * ilegal → `_`, `_` beruntun digabung. "Number 1" → "number_1.dc".
     * Akhiran `.dc.zip` (bekas penempelan MediaStore dulu) dibuang dulu
     * supaya Open berkas lama menargetkan `number_1.dc`, bukan
     * `number_1.dc.zip.dc`. Nama kosong → [fallback]; akhiran `.dc`
     * ditambahkan bila belum ada.
     */
    fun sanitizeFileName(raw: String, fallback: String): String {
        fun slug(s: String): String = s.trim().lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.')
        var base = slug(raw).ifEmpty { slug(fallback).ifEmpty { "board" } }
        if (base.endsWith(".dc.zip", ignoreCase = true)) base = base.dropLast(4)
        return if (base.endsWith(".dc", ignoreCase = true)) base else "$base.dc"
    }

    /**
     * Tulis [sourceFile] sebagai `Download/droidcanvas/<displayName>`.
     * Mengembalikan Uri konten berkas. Gagal → exception dengan pesan
     * yang menyebut sebabnya (K6).
     *
     * [existingUri] = Uri berkas target yang sudah disimpan sebelumnya
     * (bila ada): timpa langsung tanpa lookup. Uri basi (berkas dihapus
     * dari luar) jatuh ke lookup/insert di bawah.
     */
    @Throws(Exception::class)
    fun writeProjectFile(
        resolver: ContentResolver,
        displayName: String,
        sourceFile: File,
        existingUri: Uri? = null
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(resolver, displayName, sourceFile, existingUri)
        } else {
            writeViaFileApi(displayName, sourceFile)
        }
    }

    /** Cari Uri berkas tersimpan dengan nama itu (null bila belum ada). */
    fun findProjectFileUri(resolver: ContentResolver, displayName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreUri(resolver, displayName)
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$FOLDER_NAME/$displayName"
            )
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    private fun findMediaStoreUri(resolver: ContentResolver, displayName: String): Uri? {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.RELATIVE_PATH
        )
        // HANYA nama yang dicocokkan di SQL: bentuk RELATIVE_PATH yang
        // tersimpan tidak konsisten antar perangkat (dengan/tanpa garis
        // miring di ujung) dan dua ejaan sudah gagal di perangkat user
        // (2026-11-19) — folder dicocokkan di kode setelah dinormalisasi.
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(displayName)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                if (cursor.getString(pathCol)?.trimEnd('/') == RELATIVE_PATH) {
                    val id = cursor.getLong(idCol)
                    return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                    )
                }
            }
        }
        return null
    }

    private fun writeViaMediaStore(
        resolver: ContentResolver,
        displayName: String,
        sourceFile: File,
        existingUri: Uri?
    ): Uri {
        // 1. Uri tersimpan → timpa isinya langsung, tanpa lookup.
        if (existingUri != null) {
            try {
                resolver.openOutputStream(existingUri, "w")?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Tidak bisa menulis berkas $displayName")
                return existingUri
            } catch (e: FileNotFoundException) {
                // Uri basi (berkas dihapus dari luar) → lanjut ke
                // lookup/insert di bawah; hanya ini yang ditangkap —
                // gagal tulis lain (mis. disk penuh) tetap gagal jujur.
            }
        }
        // 2. Cadangan: cari berdasarkan nama, timpa bila ketemu.
        val existing = findMediaStoreUri(resolver, displayName)
        if (existing != null) {
            resolver.openOutputStream(existing, "w")?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Tidak bisa menulis berkas $displayName")
            return existing
        }
        // 3. Belum ada → sisipkan baris baru.
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Tidak bisa membuat berkas $displayName")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Tidak bisa menulis berkas $displayName")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
        } catch (e: Exception) {
            // Gagal di tengah jalan → jangan tinggalkan entri setengah jadi.
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun writeViaFileApi(displayName: String, sourceFile: File): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Tidak bisa membuat folder $FOLDER_NAME")
        }
        val target = File(dir, displayName)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(target).use { out -> input.copyTo(out) }
        }
        return Uri.fromFile(target)
    }
}
