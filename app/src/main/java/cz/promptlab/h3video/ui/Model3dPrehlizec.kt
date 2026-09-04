package cz.promptlab.h3video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.android.filament.gltfio.FilamentInstance
import cz.promptlab.h3video.data.t
import cz.promptlab.h3video.ui.theme.Surface2
import cz.promptlab.h3video.ui.theme.TextLow
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** Rychlost samovolného otáčení. Pomalu, ať to neruší při prohlížení. */
private const val STUPNU_ZA_VTERINU = 22f

/** Kolik stupňů otočení připadne na jeden pixel tahu prstem. */
private const val STUPNU_NA_PIXEL = 0.35f

/**
 * Nejvyšší překlopení. Za devadesáti stupni by se model převrátil vzhůru
 * nohama a další tah by šel na opačnou stranu.
 */
private const val MAX_PREKLOPENI = 89f

/**
 * Natočení modelu jako u gramofonu: [yaw] je otočení kolem svislé osy,
 * [pitch] překlopení kolem vodorovné osy OBRAZOVKY (kladné = vršek k divákovi).
 *
 * Pořadí skládání je tu to podstatné. Dřív se natočení počítalo jako trojice
 * Eulerových úhlů, kterou si uzel přebral po svém, takže svislý tah točil
 * kolem osy MODELU. Jakmile se model pootočil do strany — a samovolné otáčení
 * ho pootočí hned po otevření — mířila jeho vodorovná osa jinam a překlápění
 * se obrátilo: tah nahoru sklápěl model dolů. Překlopení se proto skládá
 * až NAD otočením, aby zůstalo svázané s obrazovkou, ne s modelem.
 *
 * Dvojice úhlů se drží i proto, že se model nemůže postupně „naklánět
 * doboku" — na to by při skládání přírůstků došlo.
 *
 * Mínus u překlopení není překlep: otočení kolem osy „doprava" sklápí přední
 * plochu DOLŮ, kdežto [pitch] je kladný nahoru. Hlídá to Model3dOtaceniTest.
 */
fun natoceniModelu(yaw: Float, pitch: Float): Quaternion = normalize(
    Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), -pitch) *
        Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), yaw)
)

/**
 * Prohlížeč hotového 3D modelu: otáčení tahem prstu, přiblížení štípnutím
 * a pomalé samovolné otáčení, dokud se ho uživatel nedotkne.
 *
 * Vykresluje se **nativně** přes Google Filament (knihovna SceneView) —
 * Sceneform Google v roce 2021 zahodil a holý Filament je stovky řádků na
 * jednu kostku, takže tohle je jediná udržovaná cesta.
 *
 * **Gesta si obsluhuje tenhle soubor sám** a nespoléhá na ovladač kamery
 * z knihovny. Ten sice bral štípnutí (model šel zvětšit a zmenšit), ale tah
 * prstem na otáčení nemapoval — model se ani nehnul. Otáčení i měřítko se
 * proto počítají přímo do uzlu modelu, což je zároveň to, co uživatel čeká:
 * hýbe se model, ne kamera kolem něj.
 *
 * Dřívější pokus přes WebView a `<model-viewer>` skončil černou plochou
 * (knihovna si model stahuje přes `fetch()`, který WebView u adres `file://`
 * blokuje) — nevracet se k němu.
 *
 * Pozor na verzi SceneView: 4.x je přeložené Kotlinem 2.4 a projekt jede na
 * 2.0, takže překlad padá na „incompatible version of Kotlin". Držíme 2.2.1.
 */
@Composable
fun Model3dPrehlizec(soubor: File, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    var instance by remember(soubor.absolutePath) { mutableStateOf<FilamentInstance?>(null) }
    var chyba by remember(soubor.absolutePath) { mutableStateOf<String?>(null) }

    // Soubor má jednotky megabajtů, takže se čte mimo hlavní vlákno.
    LaunchedEffect(soubor.absolutePath) {
        runCatching {
            val bajty = withContext(Dispatchers.IO) { soubor.readBytes() }
            // Filament chce PŘÍMÝ buffer — z obyčejného pole by spadl v nativní
            // vrstvě, a to bez použitelné hlášky.
            val buffer = ByteBuffer.allocateDirect(bajty.size).apply {
                put(bajty)
                rewind()
            }
            modelLoader.createModelInstance(buffer)
        }.onSuccess { instance = it }
            .onFailure { chyba = it.message ?: it::class.java.simpleName }
    }

    val uzly = rememberNodes { }
    var uzel by remember(soubor.absolutePath) { mutableStateOf<ModelNode?>(null) }
    LaunchedEffect(instance) {
        uzly.clear()
        uzel = instance?.let { ModelNode(modelInstance = it, scaleToUnits = 1f) }
        uzel?.let { uzly.add(it) }
    }

    // Dokud se člověk modelu nedotkne, pomalu se otáčí sám — ať je hned vidět,
    // že je to opravdu prostorový model.
    //
    // Krokuje se podle SNÍMKŮ OBRAZOVKY (`withFrameNanos`), ne pevným čekáním
    // 16 ms: to se s překreslováním nesejde a otáčení viditelně trhá.
    var otacetSam by remember(soubor.absolutePath) { mutableStateOf(true) }
    var yaw by remember(soubor.absolutePath) { mutableFloatStateOf(0f) }
    var pitch by remember(soubor.absolutePath) { mutableFloatStateOf(0f) }

    LaunchedEffect(uzel, otacetSam) {
        val n = uzel ?: return@LaunchedEffect
        n.quaternion = natoceniModelu(yaw, pitch)
        if (!otacetSam) return@LaunchedEffect
        var minule = 0L
        while (true) {
            withFrameNanos { cas ->
                if (minule != 0L) {
                    val dt = (cas - minule) / 1_000_000_000f
                    // Stejný směr, jakým model jde za tahem doprava.
                    yaw += STUPNU_ZA_VTERINU * dt
                    n.quaternion = natoceniModelu(yaw, pitch)
                }
                minule = cas
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = uzly,
        )

        // Vrstva gest NAD scénou: kreslí se později, takže dostane doteky dřív
        // než scéna a nepere se s ní o ně.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(uzel) {
                    val n = uzel ?: return@pointerInput
                    detectTransformGestures { _, posun, priblizeni, _ ->
                        otacetSam = false
                        // Model jde ZA PRSTEM: doprava se otočí doprava,
                        // nahoru se překlopí nahoru. Na obrazovce roste
                        // svislá souřadnice DOLŮ, proto je u překlopení mínus.
                        yaw += posun.x * STUPNU_NA_PIXEL
                        pitch = (pitch - posun.y * STUPNU_NA_PIXEL)
                            .coerceIn(-MAX_PREKLOPENI, MAX_PREKLOPENI)
                        n.quaternion = natoceniModelu(yaw, pitch)
                        if (priblizeni != 1f) {
                            val m = (n.scale.x * priblizeni).coerceIn(0.3f, 5f)
                            n.scale = Float3(m, m, m)
                        }
                    }
                }
        )

        chyba?.let {
            Text(
                t("Model se nepodařilo načíst: %s").format(it),
                style = MaterialTheme.typography.bodySmall, color = TextLow,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}
