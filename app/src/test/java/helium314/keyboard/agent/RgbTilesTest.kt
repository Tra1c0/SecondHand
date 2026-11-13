// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Review of stage 29 (M1, M2, L1): the detail tiles of the texture patterns are made on a background
 * thread, drawing never waits for them, the cache keeps every tiled pattern and is dropped when the
 * light is switched off or memory runs low.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RgbTilesTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before @After fun reset() {
        RgbTiles.executor = null
        RgbTiles.awaitIdle()
        RgbTiles.clear()
        app.prefs().edit().clear().commit()
        RgbLighting.load(app)
    }

    private fun engine(pattern: RgbPattern) = RgbEngine(RgbSettings(enabled = true, pattern = pattern)).apply { aspect = 0.6f; advance(0) }

    @Test fun drawingNeverWaitsAndTheTileIsMadeOffTheMainThread() {
        val held = mutableListOf<Runnable>()
        RgbTiles.executor = Executor { held += it }
        var told = 0
        val listener = { told++; Unit }
        RgbTiles.addListener(listener)
        try {
            val builds = RgbTiles.builds
            val texture = RgbTexture()
            val canvas = Canvas(Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888))
            // first frames: the light is drawn without its detail, nothing is made on this thread
            texture.update(engine(RgbPattern.DIAMONDS), 0f, 0f, 400f, 260f, 0L, -1)
            texture.drawRect(canvas, 0f, 0f, 400f, 260f, 1f)
            assertTrue(texture.hasField)
            assertFalse(texture.hasTile, "no tile before it is made")
            assertEquals(0, texture.tileMean)
            assertEquals(builds, RgbTiles.builds, "nothing was made while drawing")
            assertEquals(1, held.size, "the tile was handed to the background")
            // a second frame while it is being made asks only once
            texture.update(engine(RgbPattern.DIAMONDS), 0f, 0f, 400f, 260f, 33L, -1)
            assertEquals(1, held.size)
            assertFalse(texture.hasTile)

            // the background thread makes it
            thread(name = "test-tiles") { held.single().run() }.join()
            assertEquals(builds + 1, RgbTiles.builds)
            val maker = assertNotNull(RgbTiles.lastBuildThread)
            assertNotSame(Looper.getMainLooper().thread, maker, "made off the main thread")
            assertEquals("test-tiles", maker.name)
            assertTrue(RgbPattern.DIAMONDS in RgbTiles.cached())
            // the drawers are told on the main thread, then the next frame has it
            assertEquals(0, told)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, told, "the listener runs on the main thread after the tile is made")
            texture.update(engine(RgbPattern.DIAMONDS), 0f, 0f, 400f, 260f, 66L, -1)
            assertTrue(texture.hasTile)
            assertNotEquals(0, texture.tileMean)
            texture.drawRect(canvas, 0f, 0f, 400f, 260f, 1f)
            assertEquals(1, held.size, "no second build")
        } finally {
            RgbTiles.removeListener(listener)
        }
    }

    @Test fun theRealExecutorIsABackgroundThread() {
        assertNull(RgbTiles.ready(RgbPattern.TOPO), "not made yet: no waiting")
        assertTrue(RgbTiles.awaitIdle())
        val maker = assertNotNull(RgbTiles.lastBuildThread)
        assertNotSame(Looper.getMainLooper().thread, maker)
        assertNotSame(Thread.currentThread(), maker)
        assertNotNull(RgbTiles.ready(RgbPattern.TOPO))
    }

    @Test fun theCacheHoldsEveryTiledPatternAndNothingIsMadeTwice() {
        assertEquals(5, RgbTiles.TILED.size)
        RgbTiles.request(RgbPattern.entries)
        assertTrue(RgbTiles.awaitIdle())
        assertEquals(RgbTiles.TILED.toSet(), RgbTiles.cached())
        val builds = RgbTiles.builds
        val first = RgbTiles.TILED.associateWith { assertNotNull(RgbTiles.ready(it)) }
        // every pattern again, in any order: the same tiles, none made again
        for (p in RgbTiles.TILED.reversed() + RgbTiles.TILED) assertSame(first.getValue(p), RgbTiles.ready(p))
        assertEquals(builds, RgbTiles.builds)
        // patterns without detail have no tile and ask for none
        assertNull(RgbTiles.ready(RgbPattern.CIRCLE))
        assertEquals(builds, RgbTiles.builds)
    }

    @Test fun theTilesAreDroppedWhenTheLightIsOffOrMemoryRunsLow() {
        RgbLighting.write(app.prefs(), RgbSettings(enabled = true, pattern = RgbPattern.LED))
        RgbLighting.load(app)
        assertTrue(RgbTiles.awaitIdle())
        assertEquals(setOf(RgbPattern.LED), RgbTiles.cached(), "requested when the setting is read, before the first frame")
        RgbLighting.write(app.prefs(), RgbSettings(enabled = false, pattern = RgbPattern.LED))
        RgbLighting.load(app)
        assertTrue(RgbTiles.cached().isEmpty(), "switched off: no tiles kept")
        // low memory
        RgbTiles.request(listOf(RgbPattern.HATCH))
        assertTrue(RgbTiles.awaitIdle())
        assertEquals(setOf(RgbPattern.HATCH), RgbTiles.cached())
        RgbLighting.onTrimMemory()
        assertTrue(RgbTiles.cached().isEmpty())
    }

    @Test fun aTileMadeBeforeAClearIsNotKept() {
        val held = mutableListOf<Runnable>()
        RgbTiles.executor = Executor { held += it }
        assertNull(RgbTiles.ready(RgbPattern.SLATS))
        RgbTiles.clear()
        held.single().run()
        assertTrue(RgbTiles.cached().isEmpty())
        // asked again, it is made again
        RgbTiles.executor = null
        assertNull(RgbTiles.ready(RgbPattern.SLATS))
        assertTrue(RgbTiles.awaitIdle())
        assertNotNull(RgbTiles.ready(RgbPattern.SLATS))
    }
}
