package com.flux.reader.data.backup

import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Lectura/escritura del zip. Sin dependencias de Android para poder probarlo en JVM. */
object BackupCodec {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    /** [files]: ruta dentro del zip -> proveedor del contenido (se llama una vez; null = omitir). */
    fun write(out: OutputStream, manifest: BackupManifest, files: List<Pair<String, () -> InputStream?>>) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION) // libros y portadas ya vienen comprimidos
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((path, supplier) in files) {
                val input = supplier() ?: continue
                zip.putNextEntry(ZipEntry(path))
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    class Reader(file: File) : Closeable {
        private val zip = ZipFile(file)
        val manifest: BackupManifest

        init {
            val entry = zip.getEntry("manifest.json") ?: throw IllegalArgumentException("manifest.json no encontrado")
            manifest = zip.getInputStream(entry).use {
                json.decodeFromString(BackupManifest.serializer(), it.readBytes().toString(Charsets.UTF_8))
            }
            require(manifest.format == BACKUP_FORMAT) { "Formato de copia no válido" }
            require(manifest.version <= BACKUP_VERSION) { "Copia creada con una versión más nueva de FLUX" }
        }

        fun open(path: String): InputStream? = zip.getEntry(path)?.let { zip.getInputStream(it) }
        fun size(path: String): Long = zip.getEntry(path)?.size ?: -1L
        override fun close() = zip.close()
    }
}
