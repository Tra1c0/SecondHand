// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class UpdateReleasesTest {
    private fun asset(tag: String, name: String, size: Long) = ReleaseAsset(name, size, UpdateReleases.downloadUrl(tag, name))
    private fun release(number: Long, draft: Boolean = false, apkSize: Long = 30_000_000, withChecksum: Boolean = true,
                        tag: String = "build-$number") = ReleaseInfo(tag, draft, listOfNotNull(
        asset(tag, UpdateReleases.apkName(number), apkSize),
        if (withChecksum) asset(tag, UpdateReleases.checksumName(number), 100) else null,
    ))

    @Test fun tagsAreOnlyPlainBuildNumbers() {
        assertEquals(7L, UpdateReleases.buildNumber("build-7"))
        assertEquals(123456789L, UpdateReleases.buildNumber("build-123456789"))
        for (tag in listOf("build-0", "build-07", "build-", "v7", "build-7a", "Build-7", " build-7", "build--7", "build-1234567890"))
            assertNull(UpdateReleases.buildNumber(tag), tag)
        assertEquals("AgentKeyboard-build-7.apk", UpdateReleases.apkName(7))
        assertEquals("AgentKeyboard-build-7.apk.sha256", UpdateReleases.checksumName(7))
        assertEquals("https://github.com/Tra1c0/AgenticKeyboard/releases/download/build-7/AgentKeyboard-build-7.apk",
            UpdateReleases.downloadUrl("build-7", UpdateReleases.apkName(7)))
    }

    @Test fun newestPicksTheHighestCompleteBuildNotTheFirstListed() {
        val newest = UpdateReleases.newest(listOf(release(6), release(9), release(8)))!!
        assertEquals(9L, newest.number)
        assertEquals(UpdateReleases.downloadUrl("build-9", "AgentKeyboard-build-9.apk"), newest.apk.downloadUrl)
        assertEquals(UpdateReleases.checksumName(9), newest.checksum.name)
    }

    @Test fun incompleteDraftForeignAndOversizedReleasesAreSkipped() {
        val releases = listOf(
            release(5),
            release(12, draft = true),
            release(11, withChecksum = false),
            release(10, apkSize = UpdateReleases.MAX_APK_BYTES + 1),
            release(9, apkSize = 0),
            release(20, tag = "v20"),
            // asset of another build under this tag
            ReleaseInfo("build-30", false, listOf(asset("build-30", UpdateReleases.apkName(29), 10),
                asset("build-30", UpdateReleases.checksumName(30), 10))),
            // two assets with the same name are ambiguous
            ReleaseInfo("build-31", false, listOf(asset("build-31", UpdateReleases.apkName(31), 10),
                asset("build-31", UpdateReleases.apkName(31), 10), asset("build-31", UpdateReleases.checksumName(31), 10))),
            // download URL pointing elsewhere
            ReleaseInfo("build-40", false, listOf(
                ReleaseAsset(UpdateReleases.apkName(40), 10, "https://example.com/AgentKeyboard-build-40.apk"),
                asset("build-40", UpdateReleases.checksumName(40), 10))),
            ReleaseInfo("build-41", false, listOf(
                ReleaseAsset(UpdateReleases.apkName(41), 10,
                    "https://github.com/Tra1c0/SomethingElse/releases/download/build-41/AgentKeyboard-build-41.apk"),
                asset("build-41", UpdateReleases.checksumName(41), 10))),
        )
        assertEquals(5L, UpdateReleases.newest(releases)?.number)
        assertNull(UpdateReleases.newest(emptyList()))
        assertNull(UpdateReleases.newest(listOf(release(4, draft = true))))
    }

    @Test fun onlyAStrictlyHigherBuildIsAnUpdate() {
        val build = UpdateReleases.newest(listOf(release(7)))
        assertTrue(UpdateReleases.isNewer(build, 6))
        assertFalse(UpdateReleases.isNewer(build, 7))
        assertFalse(UpdateReleases.isNewer(build, 8))
        assertFalse(UpdateReleases.isNewer(null, 1))
    }

    @Test fun checksumMustNameExactlyTheExpectedFile() {
        val hex = "ab".repeat(32)
        val name = UpdateReleases.apkName(7)
        assertEquals(hex, UpdateReleases.parseChecksum("$hex  $name\n", name))
        assertEquals(hex, UpdateReleases.parseChecksum("${hex.uppercase()} *$name", name))
        assertNull(UpdateReleases.parseChecksum("$hex  ${UpdateReleases.apkName(8)}\n", name))
        assertNull(UpdateReleases.parseChecksum("${hex.dropLast(2)}  $name", name))
        assertNull(UpdateReleases.parseChecksum("$hex  $name\n$hex  $name\n", name))
        assertNull(UpdateReleases.parseChecksum("", name))
        assertNull(UpdateReleases.parseChecksum("zz".repeat(32) + "  $name", name))
    }
}
