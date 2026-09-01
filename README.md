# H3 Video — dálkové ovládání ComfyUI z mobilu

Nativní Android aplikace (Kotlin + Jetpack Compose), která pouští generování
na **tvém vlastním ComfyUI serveru**. Nic neběží v cloudu, nikam se neposílají
data — telefon je jen dálkový ovladač počítače s grafickou kartou.

Appka si nevymýšlí vlastní grafy: každá karta jede na hotovém, vyladěném
workflow (šablona 1:1) a dosazuje do něj jen vstupy uživatele — fotky, text,
seed. Díky tomu se nemůže rozejít s tím, co funguje v ComfyUI.

## Devět karet

| Karta | Model | Co dělá |
|---|---|---|
| All in One | MiniMax H3 | video z textu, obrázku, referencí, klíčových snímků; prodloužení; zvětšení |
| Dialogy | MiniMax H3 + Higgs Audio | postavy z fotek řeknou, co napíšeš (volitelné) |
| Časová osa | MiniMax H3 + LSI nody | dlouhé video složené ze segmentů |
| Obrázek | Z-Image Turbo | nová fotka z textu za pár sekund |
| Úprava obrázku | Krea 2 + Identity Edit LoRA | změní fotku podle popisu, tvář zůstane |
| Oprava fotky | Qwen Image Edit 2511 | stará/poškozená fotka jako nová, i barevně |
| Výměna tváře | Flux Fill + ACE++ | prstem začmáráš obličej, vybereš novou tvář |
| Zvětšit | SeedVR2 | gigapixel upscale po dlaždicích |
| Hudba | ACE-Step 1.5 | celá píseň z textu, i s českým zpěvem |

Obrázkové karty na sebe navazují: Obrázek → Úprava → Zvětšit na jedno klepnutí.

## Co potřebuješ

1. **Počítač s ComfyUI** a grafikou (vyvíjeno na RTX 4060 Ti 16 GB),
   spuštěné s `--listen 0.0.0.0`.
2. **Custom nody a modely** podle [POZADAVKY.md](POZADAVKY.md). Appka má
   v Nastavení tlačítko **„Zkontrolovat server"**, které vypíše, co přesně
   na tvém serveru chybí.
3. **Telefon na stejné síti**, nebo VPN (např. Tailscale), Android 8+.

Při prvním spuštění se appka zeptá na adresu serveru a otestuje spojení.

## Jak sestavit APK

Hotové APK se tu nevydává — sestavíš si ho sám ze zdrojáků:

```
git clone https://github.com/Promptlab37/H3VideoApp.git
cd H3VideoApp
gradlew assembleDebug
```

Potřebuješ JDK 17 a Android SDK (nejjednodušeji přes
[Android Studio](https://developer.android.com/studio) — otevřít složku
projektu a dát Run). Hotové APK je v
`app/build/outputs/apk/debug/app-debug.apk`; debug build se podepisuje sám,
žádný klíč nepotřebuješ. Nové verze: `git pull` a přeložit znovu.

## Licence a upozornění

- Kód aplikace: [MIT](LICENSE).
- **Aplikace neobsahuje ani nedistribuuje žádné modely, váhy ani kód
  třetích stran.** Je to čistě klient: posílá HTTP požadavky na ComfyUI
  (a volitelně Higgs Audio Studio), které si instaluješ a provozuješ sám.
  Licence modelů se proto vztahují na tebe jako jejich provozovatele.
- **Modely mají vlastní licence, které si musíš ohlídat sám.** Zejména:
  komunitní licence **MiniMax H3** vylučuje užití v EU (včetně výstupů);
  licence **Krea 2** vyžaduje content filtering a platí do 1M USD ročních
  příjmů; SeedVR2 je Apache-2.0; **Higgs Audio** má vlastní licenci
  s povinnou atribucí — čti licence na stránkách modelů.
- Workflow šablony v `app/src/main/res/raw/` jsou funkční grafy (zapojení
  uzlů a parametry) vycházející z oficiálních šablon ComfyUI a komunitních
  workflow; výměna tváře staví na ACE++ inpaint workflow od
  [Sebastiana Kamphe](https://www.patreon.com/sebastiankamph). Díky všem
  autorům.
- Pozor: některé custom nody z POZADAVKY.md **nejsou veřejně dostupné**
  (LSI Timeline nody, balík s Krea2Edit/H3 pomocnými uzly) — karty Časová
  osa a Úprava obrázku pojedou jen tam, kde tyhle balíky jsou. Zbytek
  aplikace funguje bez nich.

## Architektura (pro zvědavé)

- `comfy/*Builder.kt` — dosazování hodnot do šablon (testy hlídají, že se
  nemění nic jiného)
- `engine/GenerationEngine.kt` — celý běh: upload → fronta → sledování přes
  WebSocket → stažení; výpadek sítě nikdy neshodí úlohu
- `engine/RunTexts.kt` — texty průběhu, jedna matice pro všechny karty
- `comfy/ServerAudit.kt` — porovnání šablon s `/object_info` serveru
  („co serveru chybí")
