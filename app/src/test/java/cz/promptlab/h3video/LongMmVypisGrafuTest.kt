package cz.promptlab.h3video

import cz.promptlab.h3video.comfy.LongMmBuilder
import cz.promptlab.h3video.data.LongMmModel
import cz.promptlab.h3video.data.LongMmPomer
import cz.promptlab.h3video.data.LongMmRef
import cz.promptlab.h3video.data.LongMmPozornost
import cz.promptlab.h3video.data.LongMmRezim
import cz.promptlab.h3video.data.LongMmRozliseni
import cz.promptlab.h3video.data.LongMmScene
import org.junit.Test
import java.io.File

/**
 * Vypíše grafy, které karta **Long MiniMax** opravdu posílá, do souborů.
 *
 * Není to test v běžném smyslu — nic netvrdí. Je to podklad pro kontrolu
 * proti běžícímu ComfyUI: grafy staví tentýž kód jako appka, takže se
 * kontrola nemůže rozejít s tím, co uživatel skutečně spustí. Jiná cesta,
 * jak dostat výstup stavitele ven bez telefonu, není.
 *
 * Soubory jdou do `build/longmm-grafy/`; do repozitáře se nic nezapisuje.
 */
class LongMmVypisGrafuTest {

    private val prvni: String =
        File("src/main/res/raw/workflow_longmm_start.json").readText()
    private val dalsi: String =
        File("src/main/res/raw/workflow_longmm_dalsi.json").readText()

    @Test fun `vypis grafy vsech kombinaci`() {
        val cil = File("build/longmm-grafy").apply { mkdirs() }
        cil.listFiles()?.forEach { it.delete() }

        var pocet = 0
        for (model in LongMmModel.entries) {
            for (pozornost in LongMmPozornost.entries) {
                for (realismus in listOf(false, true)) {
                    for (referenceVNavazani in listOf(false, true)) {
                        val zaklad = LongMmScene(
                            prompt = "žena u okna",
                            sekundy = 7,
                            rozliseni = LongMmRozliseni.R768,
                            pomer = LongMmPomer.NAVYSKU,
                            nazev = "kontrola",
                            latent = "kontrola_00003.h3latent.safetensors",
                            zdroj = File("celek.mp4"),
                            reference = listOf(LongMmRef(File("a.png")), LongMmRef(File("b.png"))),
                            model = model,
                            kroky = model.kroky,
                            pozornost = pozornost,
                            realismus = realismus,
                            referenceVNavazani = referenceVNavazani,
                        )
                        val jmeno = listOf(
                            model.name.lowercase(),
                            pozornost.name.lowercase(),
                            if (realismus) "real" else "bezreal",
                            if (referenceVNavazani) "refy" else "bezrefu",
                        ).joinToString("-")

                        File(cil, "prvni-$jmeno.json").writeText(
                            LongMmBuilder.buildPrvni(
                                prvni, zaklad.copy(rezim = LongMmRezim.PRVNI),
                                123456789L, listOf("a.png", "b.png"),
                            ).toString(1)
                        )
                        File(cil, "dalsi-$jmeno.json").writeText(
                            LongMmBuilder.buildDalsi(
                                dalsi, zaklad.copy(rezim = LongMmRezim.NAVAZANI),
                                999_999_999_999_999L, "celek.mp4", listOf("a.png", "b.png"),
                            ).toString(1)
                        )
                        pocet += 2
                    }
                }
            }
        }
        // Každá dvojice rozlišení dvou průchodů: nízké se odvozuje z cíle,
        // takže kontrola proti serveru musí vidět všechny kombinace.
        for (r in LongMmRozliseni.entries) {
            val sc = LongMmScene(
                prompt = "žena u okna", sekundy = 5, rozliseni = r,
                nazev = "kontrola", latent = "kontrola_00003.h3latent.safetensors",
                zdroj = File("celek.mp4"),
                model = LongMmModel.TRIPLUSDVA, kroky = LongMmModel.TRIPLUSDVA.kroky,
            )
            File(cil, "prvni-dvapruchody-${r.kod}.json").writeText(
                LongMmBuilder.buildPrvni(prvni, sc, 1L, emptyList()).toString(1)
            )
            File(cil, "dalsi-dvapruchody-${r.kod}.json").writeText(
                LongMmBuilder.buildDalsi(
                    dalsi, sc.copy(rezim = LongMmRezim.NAVAZANI), 1L, "celek.mp4", emptyList(),
                ).toString(1)
            )
            pocet += 2
        }

        // Bez referencí — jiná větev stavitele, taky musí projít kontrolou.
        File(cil, "prvni-bezreferenci.json").writeText(
            LongMmBuilder.buildPrvni(
                prvni,
                LongMmScene(prompt = "z textu", nazev = "kontrola", sekundy = 5),
                1L, emptyList(),
            ).toString(1)
        )
        pocet++
        println("vypsano grafu: $pocet do ${cil.absolutePath}")
    }
}
