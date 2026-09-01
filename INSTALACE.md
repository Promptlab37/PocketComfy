# Instalace krok za krokem

Kompletní návod od nuly po první vygenerované video. Tři části:
**A** — příprava serveru (počítač s grafikou), **B** — sestavení aplikace,
**C** — první spuštění a propojení. Na konci je řešení nejčastějších potíží.

---

## Část A — Server (počítač s ComfyUI)

Potřebuješ počítač s grafickou kartou NVIDIA (doporučeno 16 GB VRAM,
vyvíjeno na RTX 4060 Ti) a nainstalované [ComfyUI](https://github.com/comfyanonymous/ComfyUI).

### A1. Spusť ComfyUI tak, aby bylo vidět ze sítě

ComfyUI musí poslouchat na všech rozhraních, ne jen na `localhost`:

```bash
python main.py --listen 0.0.0.0
```

(U ComfyUI Desktop najdeš totéž v nastavení serveru. Výchozí port je `8188`.)

Ověření: na **jiném** zařízení v síti otevři v prohlížeči
`http://ADRESA-POČÍTAČE:8188` — musí naskočit rozhraní ComfyUI.
Adresu počítače zjistíš příkazem `ipconfig` (Windows) nebo `ip a` (Linux) —
hledej něco jako `192.168.x.x`.

> **Windows:** při prvním spuštění se možná zeptá firewall — povol přístup
> pro privátní sítě. Když se z telefonu nepřipojíš, je firewall podezřelý č. 1.

### A2. Nainstaluj custom nody

Úplný seznam je v [POZADAVKY.md](POZADAVKY.md). Nejpohodlnější cesta je přes
[ComfyUI-Manager](https://github.com/Comfy-Org/ComfyUI-Manager):
**Manager → Custom Nodes Manager → vyhledat → Install**, potom restart ComfyUI.

Minimum pro start (karty Obrázek a Hudba jedou jen na vestavěných uzlech,
takže bez čehokoli dalšího):

| Balík | Karty |
|---|---|
| ComfyUI-ALLinONE-MinimaxH3 | All in One, Dialogy |
| rgthree-comfy | video karty, Výměna tváře |
| ComfyUI-SeedVR2_VideoUpscaler | Zvětšit |
| ComfyUI-Inpaint-CropAndStitch | Výměna tváře |
| Comfyui-QwenEditUtils | Oprava fotky |
| ComfyUI_essentials, KJNodes, VideoHelperSuite, Impact Pack | pomocné uzly |

### A3. Stáhni modely

Přesné názvy souborů a cílové složky (`models/diffusion_models`,
`models/text_encoders`, `models/vae`, `models/loras`, `models/checkpoints`)
najdeš v [POZADAVKY.md](POZADAVKY.md). SeedVR2 si své modely stáhne sám při
prvním použití.

**Nemusíš stahovat všechno předem** — aplikace má v Nastavení tlačítko
**„Zkontrolovat server"**, které přesně vypíše, co ti ještě chybí.
Klidně začni jen s modely pro karty, které chceš používat.

---

## Část B — Sestavení aplikace

Aplikace se instaluje sestavením ze zdrojových kódů — máš tak stoprocentní
jistotu, že v telefonu běží přesně to, co vidíš v tomhle repozitáři. Zabere
to pár minut a jsou dvě cesty; pro většinu lidí je jednodušší **Android Studio**.

### B1. Cesta pro každého: Android Studio

1. Stáhni a nainstaluj [Android Studio](https://developer.android.com/studio)
   (zdarma; při prvním spuštění si samo doinstaluje Android SDK).
2. Stáhni tento repozitář: zelené tlačítko **Code → Download ZIP** a rozbal,
   nebo `git clone https://github.com/Promptlab37/H3VideoApp.git`.
3. V Android Studiu: **Open** → vyber složku projektu → počkej, až doběhne
   „Gradle sync" (poprvé pár minut, stahují se závislosti).
4. Připoj telefon USB kabelem a povol na něm **ladění USB**
   (Nastavení → Info o telefonu → 7× klepni na Číslo sestavení →
   Pro vývojáře → Ladění USB).
5. Stiskni zelené **▶ Run** — appka se sama nainstaluje a spustí v telefonu.

### B2. Cesta pro otrlé: příkazová řádka

Potřebuješ JDK 17 a Android SDK (proměnná `ANDROID_HOME`, nebo soubor
`local.properties` s řádkem `sdk.dir=...`):

```bash
git clone https://github.com/Promptlab37/H3VideoApp.git
cd H3VideoApp
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

Výsledek je `app/build/outputs/apk/debug/app-debug.apk`. Do telefonu ho
dostaneš přes `adb install app-debug.apk`, nebo soubor zkopíruj do telefonu
a otevři (Android se zeptá na povolení instalace z neznámých zdrojů).

> Debug build se podepisuje automaticky vygenerovaným klíčem — žádný keystore
> nemusíš řešit. **Aktualizace:** `git pull` a přeložit znovu; appka nahradí
> starou verzi a všechno nastavení zůstane.

---

## Část C — První spuštění a propojení

1. Otevři appku. Uvítací obrazovka chce **adresu serveru** — zadej
   `http://192.168.x.x:8188` (adresa počítače z kroku A1; stačí i bez
   `http://` a portu, appka si je doplní).
2. Klepni na **Otestovat spojení**. Zelený rámeček s verzí ComfyUI a názvem
   grafické karty = vyhráno. Klepni na **Vstoupit do appky**.
3. Jdi do **Nastavení → Co serveru chybí → Zkontrolovat server**. Appka
   porovná svá workflow s tvým serverem a vypíše jmenovitě chybějící nody
   a modely. Doinstaluj podle toho (Manager + POZADAVKY.md) a zkontroluj znovu.
4. Až je zelená — přepni na **Tvořit**, vyber kartu **Obrázek**, napiš pár
   slov a stiskni **Vygenerovat obrázek**. To je nejrychlejší první test
   (pár sekund). Pak zkoušej dál: Hudba, video, oprava fotek…

### Přístup odkudkoli (volitelné, doporučené)

Doma stačí společná Wi-Fi. Aby appka fungovala i mimo domov, dej na počítač
i do telefonu [Tailscale](https://tailscale.com) (zdarma pro osobní použití),
přihlas oba stejným účtem a v appce použij tailscalovou adresu počítače
(`100.x.x.x`). Nic se nevystavuje do internetu — je to soukromá VPN.

> Tip pro Android: dej Tailscale výjimku z optimalizace baterie
> (Nastavení → Aplikace → Tailscale → Baterie → Neomezeno), jinak ho systém
> po čase uspí a appka ohlásí „počítač neodpovídá".

---

## Když něco nejde

| Příznak | Nejčastější příčina a řešení |
|---|---|
| „Server neodpovídá" hned při testu | ComfyUI neběží s `--listen 0.0.0.0`; špatná adresa/port; firewall na počítači blokuje port 8188 pro privátní síť |
| Fungovalo doma, venku ne | telefon není připojený k VPN — zapni Tailscale (a viz tip s baterií výše) |
| „Na serveru chybí balík ComfyUI-ALLinONE-MinimaxH3" | nainstaluj balík přes Manager a **restartuj ComfyUI** — načítá nody jen při startu |
| Kontrola serveru vypíše chybějící modely | stáhni přesně ty soubory (názvy sedí na POZADAVKY.md) do uvedených složek `models/…`; nový soubor se pozná bez restartu |
| Karta Časová osa / Úprava obrázku hlásí chybějící nody | tyhle dvě karty potřebují balíky, které nejsou veřejně dostupné — ostatních sedm karet funguje normálně |
| Gradle sync v Android Studiu selže | zkontroluj připojení k internetu a že máš JDK 17 (File → Settings → Build Tools → Gradle → Gradle JDK) |
| Instalace APK z příkazové řádky selže | v telefonu povol Ladění USB a potvrď otisk počítače; nebo APK prostě zkopíruj a otevři v telefonu |

Když si nevíš rady, otevři [Issue](https://github.com/Promptlab37/H3VideoApp/issues)
a přilož text chyby z aplikace.
