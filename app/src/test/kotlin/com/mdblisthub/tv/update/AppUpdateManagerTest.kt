package com.mdblisthub.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun releaseTagComparisonAcceptsTheRepositoriesTagFormat() {
        assertEquals(1, compareVersions("v.0.6.5", "0.5.9"))
        assertEquals(0, compareVersions("v.0.6.5", "0.6.5"))
        assertEquals(-1, compareVersions("v.0.6.5", "0.7.0"))
        assertNull(compareVersions("latest", "0.7.0"))
    }

    @Test
    fun updateIsOfferedOnlyForANumericallyNewerVersion() {
        assertTrue(isNewerVersion("v.0.9.2", "0.9.1"))
        assertFalse(isNewerVersion("v.0.9.1", "0.9.1"))
        assertFalse(isNewerVersion("v.0.9.0", "0.9.1"))
        assertFalse(isNewerVersion("latest", "0.9.1"))
    }

    @Test
    fun matchingAbiIsPreferredOverUniversal() {
        val universal = asset("app-universal-release.apk")
        val arm64 = asset("app-arm64-v8a-release.apk")
        val x86 = asset("app-x86-release.apk")

        assertEquals(arm64, selectApkAsset(listOf(universal, x86, arm64), listOf("arm64-v8a")))
    }

    @Test
    fun universalIsUsedWhenTheDeviceAbiIsNotPublished() {
        val arm64 = asset("app-arm64-v8a-release.apk")
        val universal = asset("app-universal-release.apk")

        assertEquals(universal, selectApkAsset(listOf(arm64, universal), listOf("riscv64")))
    }

    @Test
    fun releaseNotesAreConvertedFromMarkdownToReadableText() {
        val markdown = """
            <!-- generated -->
            ## What changed

            - Added **Croatian** subtitles
            - Read the [full notes](https://example.test/notes)
        """.trimIndent()

        assertEquals(
            "What changed\n\n• Added Croatian subtitles\n• Read the full notes",
            formatReleaseNotes(markdown),
        )
        assertNull(formatReleaseNotes("   "))
    }

    private fun asset(name: String) = ReleaseAsset(
        name = name,
        downloadUrl = "https://example.test/$name",
        size = 1L,
        sha256 = null,
    )
}
