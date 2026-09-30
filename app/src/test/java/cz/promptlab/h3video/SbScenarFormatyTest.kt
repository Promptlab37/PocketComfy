package cz.promptlab.h3video

import cz.promptlab.h3video.data.SbFilmPlan
import cz.promptlab.h3video.data.SbFilmPrepis
import cz.promptlab.h3video.data.SbFilmScene
import cz.promptlab.h3video.data.SbScenar
import cz.promptlab.h3video.data.SbScenarPokryti
import cz.promptlab.h3video.data.SbZdroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Jeden příběh v mnoha zápisech (ChatGPT se šablony nedrží — Špatný stůl 30. 9. 2026:
 * „S1 – mladý muž…“ místo „Jméno: věk…“). Pro každý zápis musí vyjít stejná okna,
 * stejné repliky u stejných mluvčích, hlas pro každého mluvčího, rekvizita bez hlasu
 * a každá replika doslova v zadání pro H3.
 */
class SbScenarFormatyTest {

    /** Čtení obrázku jako ze serveru: mladý muž, mladá žena, číšník. */
    private val obrazek = SbFilmPlan.precti(
        "TITLE: none | TOTAL: none | SHOTS: 3 | GRID: 1x3 | VOICES: Man = a man in his 30s with a warm, low voice; " +
            "Woman = a woman in her 30s with a bright, clear voice; Waiter = a man in his 50s with a calm, deep voice | " +
            "LOOKS: Man = dark hair, green suit; Woman = red hair, cream blouse | MUSIC: romantic, piano, 70 BPM " +
            "PANEL 1 | none | medium | static | A couple at a table. | none " +
            "PANEL 2 | none | medium | static | The man hands a box to the waiter. | none " +
            "PANEL 3 | none | medium | static | The waiter walks to another table. | none",
    )

    private data class Zapis(val nazev: String, val text: String, val muz: String, val zena: String, val cisnik: String, val rekvizita: String? = null)

    private val zapisy = listOf(
        Zapis("chatgpt S-kódy s pomlčkou", """
            NÁZEV: ŠPATNÝ STŮL
            Formát: realistický krátký film, Reels 9:16, přibližně 10 sekund.
            Postavy:
            S1 – mladý muž v tmavě zeleném saku
            S2 – jeho přítelkyně v krémové halence
            S3 – číšník v bílé košili a černé zástěře
            Rekvizita: Jediná malá modrá krabička se zásnubním prstenem.
            [OKNO 1]
            Mladý pár večeří.
            S2, s úsměvem: „Jsi dneska nějaký nervózní.“
            S1: „Já? Vůbec.“
            [OKNO 2]
            Muž nenápadně předá krabičku číšníkovi.
            S1, šeptem: „Až přinesete dezert. Sem, prosím.“
            S3 přikývne.
            [OKNO 3]
            Číšník zamíří k vedlejšímu stolu.
            S3, potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "S1", "S2", "S3", "Rekvizita"),

        Zapis("šablona appky", """
            STORYBOARD – Špatný stůl
            Formát: 9:16, přibližně 10 sekund.
            Postavy: Tomáš: 30 let, tmavé vlasy, tmavě zelené sako; Jana: 28 let, zrzavé vlasy, krémová halenka; Číšník: 50 let, bílá košile, černá zástěra
            Rekvizity: Krabička: malá modrá sametová krabička s prstenem
            Kontinuita: Krabička je v celém filmu modrá.

            [OKNO 1 | 0–4 s] Večeře
            Obraz: Mladý pár sedí u stolu a večeří.
            Emoce: klidná
            Jana (s úsměvem): „Jsi dneska nějaký nervózní.“
            Tomáš: „Já? Vůbec.“
            Zvuk: tiché cinkání příborů

            [OKNO 2 | 4–7 s] Předání
            Obraz: Tomáš nenápadně předá krabičku číšníkovi.
            Tomáš (šeptem): „Až přinesete dezert. Sem, prosím.“

            [OKNO 3 | 7–10 s] Omyl
            Obraz: Číšník zamíří k vedlejšímu stolu.
            Číšník (potichu): „Já jsem spletl stůl, že?“
        """.trimIndent(), "Tomáš", "Jana", "Číšník", "Krabička"),

        Zapis("jména v závorkách", """
            Postavy: Tomáš (30 let, tmavě zelené sako) a jeho přítelkyně Jana (28 let, krémová halenka), číšník Karel (50 let, černá zástěra).

            OKNO 1 (0–4 s)
            Mladý pár večeří.
            Jana, s úsměvem: „Jsi dneska nějaký nervózní.“
            Tomáš: „Já? Vůbec.“

            OKNO 2 (4–7 s)
            Tomáš předá krabičku číšníkovi.
            Tomáš šeptem: „Až přinesete dezert. Sem, prosím.“

            OKNO 3 (7–10 s)
            Číšník zamíří k vedlejšímu stolu.
            Karel potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "Tomáš", "Jana", "Karel"),

        Zapis("velká písmena a záběry", """
            POSTAVY:
            TOMÁŠ – mladý muž, 30 let, zelené sako
            JANA – jeho přítelkyně, 28 let, krémová halenka
            ČÍŠNÍK – muž kolem padesáti, černá zástěra

            ZÁBĚR 1 (0–4 s)
            Mladý pár večeří.
            JANA (s úsměvem): Jsi dneska nějaký nervózní.
            TOMÁŠ: Já? Vůbec.

            ZÁBĚR 2 (4–7 s)
            Tomáš předá krabičku číšníkovi.
            TOMÁŠ (šeptem): Až přinesete dezert. Sem, prosím.

            ZÁBĚR 3 (7–10 s)
            Číšník zamíří k vedlejšímu stolu.
            ČÍŠNÍK (potichu): Já jsem spletl stůl, že?
        """.trimIndent(), "Tomáš", "Jana", "Číšník"),

        Zapis("S-kódy se jmény (H3)", """
            Postavy:
            S1 – Tomáš: mladý muž v zeleném saku
            S2 – Jana: jeho přítelkyně v krémové halence
            S3 – Karel: číšník v černé zástěře

            [OKNO 1]
            Mladý pár večeří.
            S2, s úsměvem: <d>[Czech] Jsi dneska nějaký nervózní.</d>
            S1: <d>[Czech] Já? Vůbec.</d>
            [OKNO 2]
            Tomáš předá krabičku číšníkovi.
            S1, šeptem: <d>[Czech] Až přinesete dezert. Sem, prosím.</d>
            [OKNO 3]
            Číšník zamíří k vedlejšímu stolu.
            S3, potichu: <d>[Czech] Já jsem spletl stůl, že?</d>
        """.trimIndent(), "Tomáš", "Jana", "Karel"),

        Zapis("markdown s odrážkami", """
            **Postavy:**
            - **S1** – mladý muž v tmavě zeleném saku
            - **S2** – jeho přítelkyně v krémové halence
            - **S3** – číšník v bílé košili

            ### OKNO 1
            - Mladý pár večeří.
            - **S2**, s úsměvem: „Jsi dneska nějaký nervózní.“
            - **S1**: „Já? Vůbec.“

            ### OKNO 2
            - Muž předá krabičku číšníkovi.
            - **S1**, šeptem: „Až přinesete dezert. Sem, prosím.“

            ### OKNO 3
            - Číšník zamíří k vedlejšímu stolu.
            - **S3**, potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "S1", "S2", "S3"),

        Zapis("S-kódy bez uvozovek", """
            Postavy:
            S1 – mladý muž v zeleném saku
            S2 – mladá žena v krémové halence
            S3 – číšník v černé zástěře

            OKNO 1
            Mladý pár večeří.
            S2 (s úsměvem): Jsi dneska nějaký nervózní.
            S1: Já? Vůbec.

            OKNO 2
            Muž předá krabičku číšníkovi.
            S1 (šeptem): Až přinesete dezert. Sem, prosím.

            OKNO 3
            Číšník zamíří k vedlejšímu stolu.
            S3 (potichu): Já jsem spletl stůl, že?
        """.trimIndent(), "S1", "S2", "S3"),

        Zapis("postavy v závorce za S-kódem", """
            Postavy:
            S1 (mladý muž, zelené sako)
            S2 (jeho přítelkyně, krémová halenka)
            S3 (číšník, černá zástěra)
            Props: malá modrá krabička

            [OKNO 1 | 0–4 s]
            Mladý pár večeří.
            S2, s úsměvem: „Jsi dneska nějaký nervózní.“
            S1: „Já? Vůbec.“

            [OKNO 2 | 4–7 s]
            Muž předá krabičku číšníkovi.
            S1, šeptem: „Až přinesete dezert. Sem, prosím.“

            [OKNO 3 | 7–10 s]
            Číšník zamíří k vedlejšímu stolu.
            S3, potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "S1", "S2", "S3"),

        Zapis("role místo jmen", """
            Postavy:
            MUŽ – 30 let, tmavě zelené sako
            ŽENA – 28 let, krémová halenka
            ČÍŠNÍK – 50 let, černá zástěra

            OKNO 1
            Mladý pár večeří.
            ŽENA s úsměvem: „Jsi dneska nějaký nervózní.“
            MUŽ: „Já? Vůbec.“

            OKNO 2
            Muž předá krabičku číšníkovi.
            MUŽ šeptem: „Až přinesete dezert. Sem, prosím.“

            OKNO 3
            Číšník zamíří k vedlejšímu stolu.
            ČÍŠNÍK potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "Muž", "Žena", "Číšník"),

        Zapis("S-kódy bez čárky před podáním", """
            Postavy:
            S1 – mladý muž v zeleném saku
            S2 – jeho přítelkyně v krémové halence
            S3 – číšník v černé zástěře

            [OKNO 1]
            Mladý pár večeří.
            S2 s úsměvem: „Jsi dneska nějaký nervózní.“
            S1: „Já? Vůbec.“
            [OKNO 2]
            Muž předá krabičku číšníkovi.
            S1 šeptem: „Až přinesete dezert. Sem, prosím.“
            [OKNO 3]
            Číšník zamíří k vedlejšímu stolu.
            S3 potichu: „Já jsem spletl stůl, že?“
        """.trimIndent(), "S1", "S2", "S3"),

        Zapis("anglická kostra, české repliky", """
            TITLE: The Wrong Table
            Format: 9:16, about 10 seconds.
            Characters:
            Tom – young man, 30, dark green suit
            Jane – his girlfriend, 28, cream blouse
            Waiter – man in his 50s, black apron

            WINDOW 1 (0–4 s)
            A young couple has dinner.
            Jane (smiling): "Jsi dneska nějaký nervózní."
            Tom: "Já? Vůbec."

            WINDOW 2 (4–7 s)
            Tom hands the box to the waiter.
            Tom (whispering): "Až přinesete dezert. Sem, prosím."

            WINDOW 3 (7–10 s)
            The waiter walks to the next table.
            Waiter (quietly): "Já jsem spletl stůl, že?"
        """.trimIndent(), "Tom", "Jane", "Waiter"),
    )

    private val repliky = listOf(
        "Jsi dneska nějaký nervózní.", "Já? Vůbec.", "Až přinesete dezert. Sem, prosím.", "Já jsem spletl stůl, že?",
    )

    @Test
    fun `kazdy zapis dava stejny film`() {
        val chyby = mutableListOf<String>()
        val log = StringBuilder()
        zapisy.forEach { z ->
            fun chyba(t: String) { chyby += "${z.nazev}: $t" }
            val s = SbScenar.rozeber(z.text)
            if (s == null) { chyba("nerozebráno"); return@forEach }
            log.append("==== ${z.nazev}\npostavy=${s.postavy}\n")
            if (s.okna.size != 3) chyba("oken ${s.okna.size}")
            val r = s.okna.flatMap { o -> o.repliky.map { o.cislo to it } }
            log.append(r.joinToString("\n") { "  ${it.first} ${it.second.kdo} [${it.second.podani}] ${it.second.text}" } + "\n")
            val ocek = listOf(1 to z.zena, 1 to z.muz, 2 to z.muz, 3 to z.cisnik)
            if (r.map { it.first to it.second.kdo } != ocek) chyba("mluvčí ${r.map { it.first to it.second.kdo }} ≠ $ocek")
            if (r.map { it.second.text } != repliky) chyba("repliky ${r.map { it.second.text }}")
            val podani = r.map { it.second.podani }
            if (podani[0].isBlank() || podani[2].isBlank() || podani[3].isBlank()) chyba("podání $podani")
            for (k in listOf(z.muz, z.zena, z.cisnik)) if (s.postavy[k].isNullOrBlank()) chyba("chybí popis postavy $k")
            val nepokryte = SbScenarPokryti.nepokryte(z.text, s)
            if (nepokryte.isNotEmpty()) chyba("nepokryté $nepokryte")

            val c = SbScenar.cteni(s, obrazek)
            val plan = SbFilmPlan.naplanuj(c)
            log.append("hlasy=${plan.hlasy}\n")
            for (k in listOf(z.muz, z.zena, z.cisnik)) if (plan.hlasy[k].isNullOrBlank()) chyba("chybí hlas $k")
            z.rekvizita?.let { if (plan.hlasy.containsKey(it)) chyba("rekvizita má hlas") }
            if (plan.hlasy[z.zena]?.contains("woman") != true) chyba("${z.zena} nemá ženský hlas: ${plan.hlasy[z.zena]}")
            if (plan.hlasy[z.muz]?.contains("woman") != false) chyba("${z.muz} má ženský hlas")
            if (plan.hlasy[z.cisnik]?.contains("woman") != false) chyba("${z.cisnik} má ženský hlas")

            val sc = SbFilmScene(storyboard = File("s.png"), zdroj = SbZdroj.SCENAR, scenar = z.text,
                panely = SbScenar.doplnPanely(plan.panely, s), hlasy = plan.hlasy, vzhled = plan.vzhled, kontinuita = s.kontinuita)
            val id = SbFilmPrepis.idMluvcich(sc.panely)
            // S-kódy si nechají své číslo; jinak má každý mluvčí vlastní značku.
            if (id.values.toSet().size != id.size) chyba("dvě postavy se stejnou značkou $id")
            id.forEach { (k, v) -> if (Regex("""S\d+""").matches(k) && k != v) chyba("$k dostal značku $v") }
            val vse = sc.useky.mapIndexed { k, u ->
                SbFilmPrepis.hlidka(1, u, k, sc.useky.size, true, id, SbFilmPrepis.jazykFilmu(sc),
                    sc.hlasy, predchozi = sc.useky.getOrNull(k - 1)?.panely?.lastOrNull(), vzhled = sc.vzhled, kontinuita = sc.kontinuita)
            }.joinToString("\n")
            repliky.forEach { t -> if (!Regex("""<d>\[[A-Za-z]+] ${Regex.escape(t)}</d>""").containsMatchIn(vse)) chyba("v zadání chybí „$t“") }
            if (vse.contains("<d>[Czech] <d>")) chyba("zdvojené <d>")
        }
        File(File("build/sbscenar").also { it.mkdirs() }, "formaty.txt").writeText(log.toString() + "\nCHYBY:\n" + chyby.joinToString("\n"))
        assertTrue(chyby.joinToString("\n"), chyby.isEmpty())
    }
}
