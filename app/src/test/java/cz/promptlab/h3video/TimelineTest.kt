package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.WorkflowBuilder
import cz.promptlab.h3video.data.GenParams
import cz.promptlab.h3video.data.Mode
import cz.promptlab.h3video.data.SegmentMode
import cz.promptlab.h3video.data.TimelineScene
import cz.promptlab.h3video.data.TimelineSegment
import cz.promptlab.h3video.data.buildLsiTimeline
import cz.promptlab.h3video.data.timelineProblem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Karta Časová osa nad LSI nody. Schéma JSONu je autorovo
 * (`{"version":1,"segments":[…]}`), takže se testuje doslova to, co uzel čte:
 * režim segmentu, navázání na předchozí a média jako `{"path","name"}`.
 */
class TimelineTest {

    private val template: String =
        File("src/main/res/raw/workflow_h3_ultra.json").readText()

    private fun osa() = TimelineScene(
        segments = listOf(
            TimelineSegment(key = 1, prompt = "muz jde po molu", seconds = 8f),
            TimelineSegment(
                key = 2, mode = SegmentMode.IMAGE, prompt = "pokracuje dal",
                seconds = 8f, inheritPrevious = true,
            ),
        )
    )

    @Test fun `prvni segment je textovy, druhy navazuje na predchozi`() {
        val json = JSONObject(buildLsiTimeline(osa(), emptyList()))
        assertEquals(1, json.getInt("version"))
        val segs = json.getJSONArray("segments")
        assertEquals("T2V", segs.getJSONObject(0).getString("mode"))
        assertEquals("I2V", segs.getJSONObject(1).getString("mode"))
        assertTrue(segs.getJSONObject(1).getBoolean("use_previous_last_frame"))
        // Uzel odmítne oboje najednou.
        assertFalse(segs.getJSONObject(1).getBoolean("first_frame_only"))
    }

    @Test fun `segment s vlastnim snimkem posle cestu i jmeno`() {
        val s = TimelineScene(
            segments = listOf(
                TimelineSegment(
                    key = 1, mode = SegmentMode.IMAGE, prompt = "zacatek",
                    seconds = 6f, image = File("start.jpg"),
                )
            )
        )
        val json = JSONObject(buildLsiTimeline(s, listOf("h3app/start.jpg")))
        val seg = json.getJSONArray("segments").getJSONObject(0)
        assertTrue(seg.getBoolean("first_frame_only"))
        assertFalse(seg.getBoolean("use_previous_last_frame"))
        val obr = seg.getJSONArray("images").getJSONObject(0)
        assertEquals("h3app/start.jpg", obr.getString("path"))
        assertEquals("start.jpg", obr.getString("name"))
    }

    @Test fun `prvni segment nemuze navazovat, uzel by to odmitl`() {
        val s = TimelineScene(
            segments = listOf(
                TimelineSegment(
                    key = 1, mode = SegmentMode.IMAGE, prompt = "zacatek",
                    seconds = 6f, inheritPrevious = true,
                )
            )
        )
        // Ověření to zachytí dřív, než se to vůbec odešle…
        assertNotNull(timelineProblem(s))
        // …a i kdyby prošlo, do JSONu se navázání nedostane.
        val seg = JSONObject(buildLsiTimeline(s, emptyList())).getJSONArray("segments")
            .getJSONObject(0)
        assertFalse(seg.getBoolean("use_previous_last_frame"))
    }

    @Test fun `segment delsi nez 15 s se nepusti`() {
        val s = osa().let {
            it.copy(segments = it.segments.map { seg -> seg.copy(seconds = 20f) })
        }
        assertNotNull(timelineProblem(s))
    }

    @Test fun `vyplnena osa projde`() = assertNull(timelineProblem(osa()))

    @Test fun `graf posila osu uzlum LSI a video bere od nich`() {
        val p = GenParams(
            mode = Mode.TIMELINE, prompt = "cinematic",
            timelineProject = "test", timelineOnlySegment = 0,
        )
        val wf = WorkflowBuilder.build(template, p, buildLsiTimeline(osa(), emptyList()))
        val plan = wf.getJSONObject(WorkflowBuilder.N_LSI_TIMELINE)
        assertEquals("LSIMinimaxTimeline", plan.getString("class_type"))
        assertEquals(
            "test",
            wf.getJSONObject(WorkflowBuilder.N_LSI_RENDER).getJSONObject("inputs")
                .getString("cache_project")
        )
        // Skládání videa musí brát obraz i zvuk od renderu osy, ne z původní větve.
        val out = wf.getJSONObject(WorkflowBuilder.N_VIDEO_OUT).getJSONObject("inputs")
        assertEquals(WorkflowBuilder.N_LSI_RENDER, out.getJSONArray("images").getString(0))
        assertEquals(WorkflowBuilder.N_LSI_RENDER, out.getJSONArray("audio").getString(0))
    }

    @Test fun `pregenerovani jednoho segmentu se zapne az kdyz je vybrany`() {
        val bezVyberu = WorkflowBuilder.build(
            template, GenParams(mode = Mode.TIMELINE, prompt = "x"),
            buildLsiTimeline(osa(), emptyList()),
        ).getJSONObject(WorkflowBuilder.N_LSI_RENDER).getJSONObject("inputs")
        assertFalse(bezVyberu.getBoolean("render_selected_segment"))

        val sVyberem = WorkflowBuilder.build(
            template,
            GenParams(mode = Mode.TIMELINE, prompt = "x", timelineOnlySegment = 2),
            buildLsiTimeline(osa(), emptyList()),
        ).getJSONObject(WorkflowBuilder.N_LSI_RENDER).getJSONObject("inputs")
        assertTrue(sVyberem.getBoolean("render_selected_segment"))
        assertEquals(2, sVyberem.getInt("selected_segment"))
    }

    @Test fun `kroky hlasi vzorkovac ULTRA i LSI render - jinak odhad casu nikdy nenaskoci`() {
        // Časová osa původní vzorkovací větev obchází a vzorkuje uzel LSI
        // render. Do 2.62 se kroky poznávaly jen podle N_SAMPLING, takže osa
        // ukazovala „krok 0/8" a odhad „Zbývá" se neukázal nikde.
        assertTrue(WorkflowBuilder.reportsSteps(WorkflowBuilder.N_SAMPLING))
        assertTrue(WorkflowBuilder.reportsSteps(WorkflowBuilder.N_LSI_RENDER))
        assertFalse(WorkflowBuilder.reportsSteps(WorkflowBuilder.N_VIDEO_OUT))
        assertFalse(WorkflowBuilder.reportsSteps(null))
    }
}
