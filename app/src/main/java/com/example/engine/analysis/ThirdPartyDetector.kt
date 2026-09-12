package com.example.engine.analysis

import com.example.model.CodeClassification

class ThirdPartyDetector(
    private val appPackageName: String? = null,
    private val userExcludedPackages: List<String> = emptyList(),
    private val userTrustedPackages: List<String> = emptyList()
) {
    private val frameworkPrefixes = listOf(
        "Landroid/",
        "Ljava/",
        "Ljavax/",
        "Lkotlin/",
        "Lkotlinx/",
        "Ldalvik/"
    )

    private val knownThirdPartyPrefixes = listOf(
        "Landroidx/",
        "Lcom/google/",
        "Lcom/android/billingclient/",
        "Lcom/google/android/billingclient/",
        "Lcom/google/android/gms/",
        "Lcom/google/firebase/",
        "Lcom/google/protobuf/",
        "Lcom/google/gson/",
        "Lcom/squareup/",
        "Lokhttp3/",
        "Lretrofit2/",
        "Lcom/onesignal/",
        "Lcom/adapty/",
        "Lcom/revenuecat/",
        "Lcom/appsflyer/",
        "Lcom/adjust/",
        "Lcom/facebook/",
        "Lio/reactivex/",
        "Lorg/chromium/",
        "Lorg/apache/",
        "Lcom/unity3d/"
    )

    fun classify(rawClassName: String): CodeClassification {
        // Class names in DEX start with 'L' and end with ';' e.g. Lcom/example/MyClass;
        val normalized = rawClassName.trim()

        // Check if explicitly trusted by user
        for (trusted in userTrustedPackages) {
            val prefix = toDexPackagePrefix(trusted)
            if (normalized.startsWith(prefix)) return CodeClassification.APP_CODE
        }

        // Check if user explicitly excluded
        for (excluded in userExcludedPackages) {
            val prefix = toDexPackagePrefix(excluded)
            if (normalized.startsWith(prefix)) return CodeClassification.THIRD_PARTY
        }

        // Framework check
        for (prefix in frameworkPrefixes) {
            if (normalized.startsWith(prefix)) return CodeClassification.FRAMEWORK
        }

        // App package check
        if (!appPackageName.isNullOrBlank() && appPackageName != "-") {
            val appPrefix = toDexPackagePrefix(appPackageName)
            if (normalized.startsWith(appPrefix)) {
                return CodeClassification.APP_CODE
            }
        }

        // Third party check
        for (prefix in knownThirdPartyPrefixes) {
            if (normalized.startsWith(prefix)) return CodeClassification.THIRD_PARTY
        }

        // If package doesn't match known third party or framework, and isn't the main package:
        return if (!appPackageName.isNullOrBlank() && appPackageName != "-") {
            CodeClassification.UNKNOWN
        } else {
            CodeClassification.APP_CODE
        }
    }

    private fun toDexPackagePrefix(packageName: String): String {
        val slash = packageName.replace('.', '/')
        return if (slash.startsWith("L")) slash else "L$slash"
    }
}
