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
        // both publish steps (private pre-release, public repository) upload all four files, nothing less
        val filesLine = "FILES=(\"\$APK_NAME\" \"\$APK_NAME.sha256\" \"\$LEGACY_APK_NAME\" \"\$LEGACY_APK_NAME.sha256\")"
        assertEquals(2, workflow.lines().count { it.trim() == filesLine }, "the FILES line of both publish steps")
        for (call in listOf("gh release create", "gh release upload")) {
            val lines = workflow.lines().filter { call in it && "\"\$RELEASE_TAG\"" in it }
            assertEquals(2, lines.size, "$call in both publish steps")
            for (line in lines) assertTrue("\"\${FILES[@]}\"" in line, "$call uses all four files: $line")
        }
    }

    @Test fun theWorkflowPublishesInTheRepositoryTheAppAsks() {
        assertEquals(UpdateReleases.REPOSITORY, env("RELEASE_REPOSITORY"))
        // the read-back (list check in Python, file download in shell) must build the URL the app accepts
        val inPython = UpdateReleases.downloadUrl("{tag}", "{n}").replace(UpdateReleases.REPOSITORY, "{repo}")
        assertTrue("want = f\"$inPython\"" in workflow, "read-back URL pattern $inPython")
        val inShell = UpdateReleases.downloadUrl("\$RELEASE_TAG", "\$FILE").replace(UpdateReleases.REPOSITORY, "\$RELEASE_REPOSITORY")
        assertTrue("\"$inShell\"" in workflow, "read-back download URL $inShell")
        // the limits it applies are the app's
        assertEquals(4096L, UpdateReleases.MAX_CHECKSUM_BYTES)
        assertEquals(120L * 1024 * 1024, UpdateReleases.MAX_APK_BYTES)
        val limit = "limit = ${UpdateReleases.MAX_CHECKSUM_BYTES} if n.endswith(\".sha256\") else ${UpdateReleases.MAX_APK_BYTES / 1024 / 1024} * 1024 * 1024"
        assertTrue(limit in workflow, "read-back limits: $limit")
    }

    @Test fun theGatesSafetyNetsAreStillThere() {
        // the tag lookup that works for a missing tag (gh api printed the error body and stopped every new build)
        assertTrue("git ls-remote --tags origin" in workflow)
        assertFalse("git/ref/tags" in workflow, "no gh api tag lookup")
        // the test gate follows the paths of agent-tests.yml; these two must be among them
        val paths = repoFile(".github/workflows/agent-tests.yml").readLines().map { it.trim() }
        for (path in listOf(".github/workflows/agent-apk.yml", "docs/BUILD.md"))
            assertTrue("- '$path'" in paths, "agent-tests.yml lists $path")
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
