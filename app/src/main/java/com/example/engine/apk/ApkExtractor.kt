package com.example.engine.apk

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.model.ApkFileInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedDexItem(
    val dexName: String,
    val file: File
)

data class ExtractionResult(
    val apkInfo: ApkFileInfo,
    val dexFiles: List<ExtractedDexItem>,
    val tempDir: File
)

class ApkExtractor(private val context: Context) {

    /**
     * Reads quick metadata without full extraction.
     * Extracts Application Label, Package Name, Version Name, Version Code, Size and DEX count.
     */
    fun inspectFile(file: File): ApkFileInfo {
        val isApks = file.name.lowercase(Locale.ROOT).endsWith(".apks")
        val fileSize = file.length()
        val sizeFormatted = formatFileSize(fileSize)

        var dexCount = 0
        var packageName = "-"
        var versionName = "-"
        var versionCode = 0L
        var appLabel: String? = null

        // 1. Try Android PackageManager getPackageArchiveInfo for standalone APK
        if (!isApks) {
            try {
                val pm = context.packageManager
                val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
                if (pi != null) {
                    val appInfo = pi.applicationInfo
                    if (appInfo != null) {
                        appInfo.sourceDir = file.absolutePath
                        appInfo.publicSourceDir = file.absolutePath

                        val label = try {
                            appInfo.loadLabel(pm)?.toString()
                        } catch (_: Exception) { null }

                        if (!label.isNullOrBlank() && label != pi.packageName) {
                            appLabel = label.trim()
                        }
                    }

                    if (!pi.packageName.isNullOrBlank()) {
                        packageName = pi.packageName
                    }
                    val vName = pi.versionName
                    if (!vName.isNullOrBlank()) {
                        versionName = vName
                    }
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pi.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pi.versionCode.toLong()
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Scan ZIP stream for DEX count, Manifest, and resources if not yet resolved
        var rawManifestBytes: ByteArray? = null
        var resourcesArscBytes: ByteArray? = null

        try {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis, 64 * 1024).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (name.matches(Regex("classes\\d*\\.dex"))) {
                                dexCount++
                            } else if (name == "AndroidManifest.xml" && (packageName == "-" || appLabel == null)) {
                                rawManifestBytes = zis.readBytes()
                            } else if (name == "resources.arsc" && appLabel == null && resourcesArscBytes == null) {
                                // Limit reading resources.arsc to 2MB to avoid OOM
                                val buffer = ByteArray(2 * 1024 * 1024)
                                var totalRead = 0
                                var read = zis.read(buffer, 0, buffer.size)
                                while (read != -1 && totalRead < buffer.size) {
                                    totalRead += read
                                    read = zis.read(buffer, totalRead, buffer.size - totalRead)
                                }
                                resourcesArscBytes = buffer.copyOf(totalRead)
                            } else if (isApks && (name.endsWith(".apk") || name.contains("base"))) {
                                // Nested APK inspection for APKS bundles
                                val subZip = ZipInputStream(BufferedInputStream(zis, 32 * 1024))
                                var subEntry = subZip.nextEntry
                                while (subEntry != null) {
                                    val subName = subEntry.name
                                    if (subName.matches(Regex("classes\\d*\\.dex"))) {
                                        dexCount++
                                    } else if (subName == "AndroidManifest.xml" && (packageName == "-" || appLabel == null)) {
                                        rawManifestBytes = subZip.readBytes()
                                    }
                                    subEntry = subZip.nextEntry
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. If manifest was read, parse AXML
        if (rawManifestBytes != null) {
            val manifestInfo = parseBinaryManifest(rawManifestBytes!!)
            if (packageName == "-" && manifestInfo.packageName.isNotBlank()) {
                packageName = manifestInfo.packageName
            }
            if (versionName == "-" && manifestInfo.versionName.isNotBlank()) {
                versionName = manifestInfo.versionName
            }
            if (versionCode == 0L && manifestInfo.versionCode > 0) {
                versionCode = manifestInfo.versionCode
            }
            if (appLabel == null && manifestInfo.labelString.isNotBlank()) {
                appLabel = manifestInfo.labelString
            }
        }

        // 4. If label is still null, try looking in resources.arsc strings
        if (appLabel == null && resourcesArscBytes != null) {
            val resolved = extractPotentialAppNameFromArsc(resourcesArscBytes!!)
            if (!resolved.isNullOrBlank()) {
                appLabel = resolved
            }
        }

        // 5. Final fallback for app name
        val finalAppName = when {
            !appLabel.isNullOrBlank() -> appLabel
            packageName != "-" -> cleanPackageNameAsTitle(packageName)
            else -> cleanFilename(file.nameWithoutExtension)
        }

        return ApkFileInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            appName = finalAppName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            fileSizeFormatted = sizeFormatted,
            fileSizeBytes = fileSize,
            fileType = if (isApks) "APKS" else "APK",
            dexCount = dexCount.coerceAtLeast(1),
            status = "جاهز للفحص",
            isApks = isApks
        )
    }

    /**
     * Extracts DEX files from the APK / APKS into a temporary directory using streaming.
     */
    fun extractDexFiles(
        file: File,
        onProgress: (dexName: String) -> Unit = {}
    ): ExtractionResult {
        val apkInfo = inspectFile(file)
        val tempDir = File(context.cacheDir, "dex_extract_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        val dexList = mutableListOf<ExtractedDexItem>()
        val isApks = file.name.lowercase(Locale.ROOT).endsWith(".apks")

        try {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis, 64 * 1024).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (name.matches(Regex("classes\\d*\\.dex"))) {
                                val outFile = File(tempDir, name)
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos, 64 * 1024)
                                }
                                dexList.add(ExtractedDexItem(name, outFile))
                                onProgress(name)
                            } else if (isApks && name.endsWith(".apk")) {
                                val subZip = ZipInputStream(BufferedInputStream(zis, 32 * 1024))
                                var subEntry = subZip.nextEntry
                                var subDexIndex = dexList.size + 1
                                while (subEntry != null) {
                                    if (subEntry.name.matches(Regex("classes\\d*\\.dex"))) {
                                        val dexName = "classes${if (subDexIndex == 1) "" else subDexIndex}.dex"
                                        val outFile = File(tempDir, dexName)
                                        FileOutputStream(outFile).use { fos ->
                                            subZip.copyTo(fos, 64 * 1024)
                                        }
                                        dexList.add(ExtractedDexItem(dexName, outFile))
                                        onProgress(dexName)
                                        subDexIndex++
                                    }
                                    subEntry = subZip.nextEntry
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort DEX files naturally: classes.dex, classes2.dex, classes3.dex...
        dexList.sortBy { item ->
            val numStr = item.dexName.filter { it.isDigit() }
            if (numStr.isEmpty()) 1 else numStr.toIntOrNull() ?: 999
        }

        return ExtractionResult(
            apkInfo = apkInfo.copy(dexCount = dexList.size),
            dexFiles = dexList,
            tempDir = tempDir
        )
    }

    /**
     * Copies an imported URI to a local cache file for inspection.
     */
    fun copyUriToTemp(uri: Uri, displayName: String): File {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(context.cacheDir, "input_${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        return outFile
    }

    /**
     * Cleans up temporary directory.
     */
    fun cleanup(tempDir: File) {
        try {
            tempDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun cleanFilename(name: String): String {
        return name.replace(Regex("[._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun cleanPackageNameAsTitle(pkg: String): String {
        val parts = pkg.split(".").filter { it.isNotBlank() && it != "com" && it != "org" && it != "net" && it != "app" }
        return parts.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
    }

    private data class ParsedManifest(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val labelString: String
    )

    /**
     * Fast Android binary XML (AXML) parser to extract package, versionName, versionCode, label.
     */
    private fun parseBinaryManifest(bytes: ByteArray): ParsedManifest {
        if (bytes.size < 8) return ParsedManifest("", "", 0L, "")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val header = buf.short.toInt() and 0xFFFF
        if (header != 0x0003) return ParsedManifest("", "", 0L, "")

        var packageName = ""
        var versionName = ""
        var versionCode = 0L
        var labelString = ""

        try {
            buf.position(8)
            val strings = mutableListOf<String>()

            while (buf.remaining() >= 8) {
                val chunkType = buf.short.toInt() and 0xFFFF
                val headerSize = buf.short.toInt() and 0xFFFF
                val chunkSize = buf.int

                if (chunkType == 0x0001) { // RES_STRING_POOL_TYPE
                    val stringCount = buf.int
                    val styleCount = buf.int
                    val flags = buf.int
                    val stringsStart = buf.int
                    val isUtf8 = (flags and (1 shl 8)) != 0

                    val stringOffsets = IntArray(stringCount)
                    for (i in 0 until stringCount) {
                        stringOffsets[i] = buf.int
                    }

                    val baseStringsPos = 8 + stringsStart
                    for (i in 0 until stringCount) {
                        val pos = baseStringsPos + stringOffsets[i]
                        if (pos < bytes.size) {
                            val str = readAxmlString(bytes, pos, isUtf8)
                            strings.add(str)
                        } else {
                            strings.add("")
                        }
                    }
                    break
                }
                if (chunkSize <= 0) break
                buf.position(buf.position() + chunkSize - 8)
            }

            for (s in strings) {
                if (packageName.isEmpty() && s.contains(".") && !s.contains("http") && !s.contains("/") && !s.startsWith("android.")) {
                    if (s.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
                        packageName = s
                    }
                }
                if (versionName.isEmpty() && s.matches(Regex("^\\d+\\.\\d+.*"))) {
                    versionName = s
                }
                // Check if string could be an application title
                if (labelString.isEmpty() && s.length in 3..40 && !s.contains("/") && !s.contains(".") && !s.startsWith("android")) {
                    if (s.any { it.isLetter() } && !s.contains("theme", ignoreCase = true) && !s.contains("style", ignoreCase = true)) {
                        labelString = s
                    }
                }
            }
        } catch (_: Exception) {}

        return ParsedManifest(packageName, versionName, versionCode, labelString)
    }

    private fun extractPotentialAppNameFromArsc(bytes: ByteArray): String? {
        try {
            // Find app_name string in strings pool
            val content = String(bytes, Charsets.ISO_8859_1)
            val appNameIdx = content.indexOf("app_name")
            if (appNameIdx != -1 && appNameIdx + 64 < content.length) {
                val sub = content.substring(appNameIdx, appNameIdx + 64)
                val clean = sub.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' }
                val candidate = clean.removePrefix("app name").trim()
                if (candidate.length in 3..30) return candidate
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readAxmlString(bytes: ByteArray, offset: Int, isUtf8: Boolean): String {
        var p = offset
        if (p >= bytes.size) return ""
        return try {
            if (isUtf8) {
                val len1 = bytes[p++].toInt() and 0xFF
                val len = if ((len1 and 0x80) != 0) {
                    ((len1 and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF)
                } else len1
                p++
                val end = (p + len).coerceAtMost(bytes.size)
                String(bytes, p, end - p, Charsets.UTF_8)
            } else {
                val len1 = (bytes[p++].toInt() and 0xFF) or ((bytes[p++].toInt() and 0xFF) shl 8)
                val len = if ((len1 and 0x8000) != 0) {
                    val len2 = (bytes[p++].toInt() and 0xFF) or ((bytes[p++].toInt() and 0xFF) shl 8)
                    ((len1 and 0x7FFF) shl 16) or len2
                } else len1
                val end = (p + len * 2).coerceAtMost(bytes.size)
                String(bytes, p, end - p, Charsets.UTF_16LE)
            }
        } catch (_: Exception) {
            ""
        }
    }
}
