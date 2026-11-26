package com.safeer.mobile.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class MobileFileProvider : ContentProvider() {

    private lateinit var authority: String

    override fun onCreate(): Boolean {
        return true
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        authority = info.authority
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Pot zunaj podrocij prenosov: brez vrstice (ne izjema v klicatelja).
        val file = try { getFileForUri(uri) } catch (_: FileNotFoundException) { return null }
        val proj = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(proj, 1)
        val row = cursor.newRow()
        for (col in proj) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val file = try { getFileForUri(uri) } catch (_: FileNotFoundException) { return null }
        val ext = file.extension
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        // Samo branje (tedenski pregled 4. 11. 2026): prej sta w/rw ustvarila ali prepisala datoteko v mapi aplikacije.
        if (!PonudnikPravila.samoBranje(mode)) throw SecurityException("Ponudnik je samo za branje")
        return ParcelFileDescriptor.open(getFileForUri(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /**
     * Datoteka za URI samo v podrocjih prenosov (PonudnikPravila.razcleni), po kanonicni poti znotraj korena podrocja,
     * obstojeca navadna datoteka. Nic se ne ustvari (prej mkdirs za vsako zahtevano pot).
     */
    private fun getFileForUri(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException("Context ni na voljo")
        val (podrocje, rel) = PonudnikPravila.razcleni(uri.path) ?: throw FileNotFoundException("Pot ni dovoljena")
        val f = when (podrocje) {
            PonudnikPravila.Podrocje.JAVNO -> {
                val mapa = rel.substringBefore('/')
                if (mapa !in PrenosiUi.JAVNE_MAPE || !rel.contains('/')) throw FileNotFoundException("Neznana javna mapa")
                PonudnikPravila.datoteka(android.os.Environment.getExternalStoragePublicDirectory(mapa), rel.substringAfter('/'))
            }
            PonudnikPravila.Podrocje.ZUNANJI_PRENOSI ->
                PonudnikPravila.datoteka(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), rel)
            PonudnikPravila.Podrocje.NOTRANJI_PRENOSI ->
                PonudnikPravila.datoteka(File(ctx.filesDir, PonudnikPravila.NOTRANJA_PODMAPA), rel)
            PonudnikPravila.Podrocje.PREDPOMNILNIK -> PonudnikPravila.datoteka(ctx.cacheDir, rel)
        }
        if (f == null || !f.isFile) throw FileNotFoundException("Datoteke ni")
        return f
    }
}
