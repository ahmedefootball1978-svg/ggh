package com.example.engine.export

import com.example.model.AnalysisResult
import com.example.model.ApkFileInfo

object ExportHelper {

    fun exportToTxt(apkInfo: ApkFileInfo, results: List<AnalysisResult>): String {
        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("BLACK PREMIUM ANALYZER - ANALYSIS REPORT\n")
        sb.append("=========================================\n")
        sb.append("App Name: ").append(apkInfo.appName).append("\n")
        sb.append("Package: ").append(apkInfo.packageName).append("\n")
        sb.append("Version: ").append(apkInfo.versionName).append(" (").append(apkInfo.versionCode).append(")\n")
        sb.append("File Size: ").append(apkInfo.fileSizeFormatted).append("\n")
        sb.append("DEX Count: ").append(apkInfo.dexCount).append("\n")
        sb.append("Total Results: ").append(results.size).append("\n")
        sb.append("=========================================\n\n")

        for ((idx, r) in results.withIndex()) {
            sb.append("#").append(idx + 1).append(" - [").append(r.score).append("%] ")
                .append(r.confidenceLevel.labelEn).append(" (").append(r.classification.label).append(")\n")
            sb.append("DEX: ").append(r.dexName).append("\n")
            sb.append("Class: ").append(r.className).append("\n")
            sb.append("Method: ").append(r.methodName).append("\n")
            sb.append("Signature: ").append(r.signature).append("\n")
            sb.append("Keywords: ").append(r.matchedKeywords.joinToString(", ")).append("\n")
            sb.append("Reasons:\n")
            for (reason in r.reasons) {
                val sign = if (reason.scoreDelta >= 0) "+" else ""
                sb.append("  * ").append(reason.description).append(" (").append(sign).append(reason.scoreDelta).append(")\n")
            }
            sb.append("\nSmali:\n")
            sb.append(r.smaliSnippet)
            sb.append("\n-----------------------------------------\n\n")
        }

        return sb.toString()
    }

    fun exportToJson(apkInfo: ApkFileInfo, results: List<AnalysisResult>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": {\n")
        sb.append("    \"name\": \"").append(escapeJson(apkInfo.appName)).append("\",\n")
        sb.append("    \"package\": \"").append(escapeJson(apkInfo.packageName)).append("\",\n")
        sb.append("    \"version\": \"").append(escapeJson(apkInfo.versionName)).append("\",\n")
        sb.append("    \"dexCount\": ").append(apkInfo.dexCount).append("\n")
        sb.append("  },\n")
        sb.append("  \"resultsCount\": ").append(results.size).append(",\n")
        sb.append("  \"results\": [\n")

        for ((idx, r) in results.withIndex()) {
            sb.append("    {\n")
            sb.append("      \"rank\": ").append(idx + 1).append(",\n")
            sb.append("      \"score\": ").append(r.score).append(",\n")
            sb.append("      \"confidence\": \"").append(r.confidenceLevel.name).append("\",\n")
            sb.append("      \"classification\": \"").append(r.classification.name).append("\",\n")
            sb.append("      \"dex\": \"").append(escapeJson(r.dexName)).append("\",\n")
            sb.append("      \"class\": \"").append(escapeJson(r.className)).append("\",\n")
            sb.append("      \"method\": \"").append(escapeJson(r.methodName)).append("\",\n")
            sb.append("      \"signature\": \"").append(escapeJson(r.signature)).append("\",\n")
            sb.append("      \"keywords\": [").append(r.matchedKeywords.joinToString(", ") { "\"${escapeJson(it)}\"" }).append("],\n")
            sb.append("      \"reasons\": [\n")
            for ((rIdx, reason) in r.reasons.withIndex()) {
                sb.append("        {\"desc\": \"").append(escapeJson(reason.description)).append("\", \"delta\": ").append(reason.scoreDelta).append("}")
                if (rIdx < r.reasons.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("      ]\n")
            sb.append("    }")
            if (idx < results.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun exportToCsv(apkInfo: ApkFileInfo, results: List<AnalysisResult>): String {
        val sb = StringBuilder()
        sb.append("Rank,Score,Confidence,Classification,DEX,Class,Method,Signature,Keywords,Reasons\n")

        for ((idx, r) in results.withIndex()) {
            sb.append(idx + 1).append(",")
            sb.append(r.score).append(",")
            sb.append("\"").append(r.confidenceLevel.labelEn).append("\",")
            sb.append("\"").append(r.classification.label).append("\",")
            sb.append("\"").append(r.dexName).append("\",")
            sb.append("\"").append(r.className).append("\",")
            sb.append("\"").append(r.methodName).append("\",")
            sb.append("\"").append(r.signature).append("\",")
            sb.append("\"").append(r.matchedKeywords.joinToString("; ")).append("\",")
            val reasonsSummary = r.reasons.joinToString("; ") { "${it.description} (${it.scoreDelta})" }
            sb.append("\"").append(reasonsSummary.replace("\"", "\"\"")).append("\"\n")
        }

        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
