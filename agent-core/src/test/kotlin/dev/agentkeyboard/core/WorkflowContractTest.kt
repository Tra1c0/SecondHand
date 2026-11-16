// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import java.io.File
import kotlin.test.*

/**
 * Stage 63: the release workflow and the app's update check must agree on the tag and the file names,
 * or the owner's phone silently finds no update. The workflow only runs on GitHub, so this test reads
 * it as text and pins the names to [UpdateReleases].
 */
class WorkflowContractTest {
    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.isFile } ?: fail("$path not found from ${File(".").absoluteFile}")

    private val workflow by lazy { repoFile(".github/workflows/agent-apk.yml").readText() }

    /** The value of a top-level `env:` entry of the workflow, with the run number filled in. */
    private fun env(name: String, runNumber: Long = 42): String {
        val line = workflow.lines().firstOrNull { it.startsWith("  $name: ") } ?: fail("env $name missing in agent-apk.yml")
        return line.substringAfter("$name: ").trim().replace("\${{ github.run_number }}", runNumber.toString())
    }

    @Test fun theTagIsTheOneTheAppParses() {
        for (n in listOf(1L, 28L, 123456L)) {
            assertEquals(n, UpdateReleases.buildNumber(env("RELEASE_TAG", n)), "tag for run number $n")
        }
    }

    @Test fun theAssetNamesAreTheOnesTheAppLooksFor() {
        for (n in listOf(1L, 28L, 123456L)) {
            val apk = env("APK_NAME", n)
            val legacy = env("LEGACY_APK_NAME", n)
            assertEquals(UpdateReleases.apkName(n), apk)
            assertEquals(UpdateReleases.legacyApkName(n), legacy)
            // the workflow writes "<file>.sha256" for each file (see the publish steps)
            assertEquals(UpdateReleases.checksumName(n), "$apk.sha256")
            assertEquals(UpdateReleases.legacyChecksumName(n), "$legacy.sha256")
        }
        for (name in listOf("\"\$APK_NAME.sha256\"", "\"\$LEGACY_APK_NAME.sha256\"", "\"\$APK_NAME\"", "\"\$LEGACY_APK_NAME\""))
            assertTrue(name in workflow, "the publish step lists $name")
    }

    @Test fun theWorkflowPublishesInTheRepositoryTheAppAsks() {
        assertEquals(UpdateReleases.REPOSITORY, env("RELEASE_REPOSITORY"))
        // the read-back checks the download URLs the app accepts
        assertEquals(
            "https://github.com/${UpdateReleases.REPOSITORY}/releases/download/build-42/${UpdateReleases.apkName(42)}",
            "https://github.com/${env("RELEASE_REPOSITORY")}/releases/download/${env("RELEASE_TAG")}/${env("APK_NAME")}",
        )
    }

    @Test fun theOwnersCertificateInTheWorkflowIsTheOneInBuildMd() {
        val line = repoFile("docs/BUILD.md").readLines().first { "Zertifikat des Owners (SHA-256)" in it }
        val documented = repoFile("docs/BUILD.md").readLines().dropWhile { it != line }.drop(1).first { "`" in it }
            .substringAfter('`').substringBefore('`')
        assertEquals(documented.replace(":", "").lowercase(), env("OWNER_CERT_SHA256"))
        assertEquals(64, env("OWNER_CERT_SHA256").length)
    }

    @Test fun theVerifyStepHasNoWayAround() {
        assertEquals("dev.personal.agentkeyboard.debug", env("APP_PACKAGE"))
        assertFalse(workflow.lines().any { "apksigner" in it && "|| true" in it }, "apksigner must fail the run")
        for (needed in listOf("apksigner\" verify", "application-debuggable", "github.run_number }}\" ]", "OWNER_CERT_SHA256"))
            assertTrue(needed in workflow, "verify step: $needed")
    }

    @Test fun thePublicPublishFailsWithoutTokenAndIsReadBack() {
        assertFalse("::warning::Secret RELEASES_TOKEN" in workflow, "a missing token is an error, not a warning")
        assertTrue("RELEASES_TOKEN is missing" in workflow)
        assertTrue("releases?per_page=10" in workflow)
        assertTrue("--clobber" in workflow, "a re-run uploads again")
    }
}
