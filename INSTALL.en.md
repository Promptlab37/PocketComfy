*[Česky](INSTALACE.md) · **English***

# Installation, step by step

A complete walkthrough from zero to your first generated video. Three parts:
**A** — preparing the server (the PC with the GPU), **B** — building the app,
**C** — first launch and pairing. Troubleshooting is at the end.

---

## Part A — Server (the PC running ComfyUI)

You need a PC with an NVIDIA GPU (16 GB VRAM recommended, developed on an
RTX 4060 Ti) and [ComfyUI](https://github.com/comfyanonymous/ComfyUI) installed.

### A1. Start ComfyUI so it is reachable from the network

ComfyUI must listen on all interfaces, not just `localhost`:

```bash
python main.py --listen 0.0.0.0
```

(In ComfyUI Desktop the same option is in the server settings. The default port
is `8188`.)

Verify it: on **another** device on the same network open
`http://YOUR-PC-ADDRESS:8188` in a browser — the ComfyUI interface must load.
Find the address with `ipconfig` (Windows) or `ip a` (Linux); look for something
like `192.168.x.x`.

> **Windows:** the firewall may ask on first start — allow access for private
> networks. If the phone cannot connect, the firewall is suspect number one.

### A2. Install the custom nodes

**Fastest route (Windows):** download [`instalace-serveru.bat`](instalace-serveru.bat)
from this repository, copy it into your ComfyUI folder (where `main.py` lives)
and run it. The script clones every required custom node pack and offers to
download the publicly available models card by card (it states the size of each
and downloads nothing without asking). Then restart ComfyUI.

Manual route: the full list is in [REQUIREMENTS.en.md](REQUIREMENTS.en.md); the
most convenient way is [ComfyUI-Manager](https://github.com/Comfy-Org/ComfyUI-Manager):
**Manager → Custom Nodes Manager → search → Install**, then restart ComfyUI.

Minimum to get started (the Image and Music cards run on built-in nodes only,
so they need nothing extra):

| Pack | Cards |
|---|---|
| ComfyUI-ALLinONE-MinimaxH3 | All in One, Dialogue |
| rgthree-comfy | video cards, Face swap |
| ComfyUI-SeedVR2_VideoUpscaler | Upscale |
| ComfyUI-Inpaint-CropAndStitch | Face swap |
| Comfyui-QwenEditUtils | Photo restore |
| ComfyUI_essentials, KJNodes, VideoHelperSuite, Impact Pack | helper nodes |

### A3. Download the models

**You do not have to download everything up front — and you do not have to look
anything up.** In Settings the app has a **"Check server"** button that compares
its workflows against your ComfyUI and, for every missing item, tells you:

- for nodes: **which pack** to install, which cards need it and a GitHub link,
- for models: **the exact file name, the target folder, the card it serves and a
  download link**.

The **Copy list** button puts all of that on the clipboard so you can send it to
your PC and just follow the links. So feel free to start with an empty ComfyUI
and let the app tell you what to fetch — or grab only the models for the cards
you actually want (simply ignore the entries for the rest).

Exact file names and target folders (`models/diffusion_models`,
`models/text_encoders`, `models/vae`, `models/loras`, `models/checkpoints`) are
also listed in [REQUIREMENTS.en.md](REQUIREMENTS.en.md). SeedVR2 downloads its
own models on first use.

---

## Part B — Building the app

The app is installed by building it from source, which guarantees that what runs
on your phone is exactly what you see in this repository. It takes a few minutes
and there are two routes; **Android Studio** is the easier one for most people.

### B1. The easy route: Android Studio

1. Download and install [Android Studio](https://developer.android.com/studio)
   (free; it installs the Android SDK on first run).
2. Get this repository: the green **Code → Download ZIP** button, or
   `git clone https://github.com/Promptlab37/PocketComfy.git`.
3. In Android Studio: **Open** → select the project folder → wait for the Gradle
   sync to finish (a few minutes the first time, it downloads dependencies).
4. Connect your phone over USB and enable **USB debugging** on it
   (Settings → About phone → tap Build number 7× → Developer options → USB debugging).
5. Press the green **▶ Run** — the app installs itself and starts on the phone.

### B2. The route for the command line

You need JDK 17 and the Android SDK (either the `ANDROID_HOME` variable or a
`local.properties` file containing `sdk.dir=...`):

```bash
git clone https://github.com/Promptlab37/PocketComfy.git
cd PocketComfy
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

The result is `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install app-debug.apk`, or copy the file to the phone and open it (Android
will ask for permission to install from unknown sources).

> Debug builds are signed with an automatically generated key — no keystore to
> deal with. **Updating:** `git pull` and build again; the app replaces the old
> version and keeps all its settings.

---

## Part C — First launch and pairing

1. Open the app. The welcome screen asks for the **server address** — enter
   `http://192.168.x.x:8188` (the PC address from step A1; the scheme and port
   may be omitted, the app fills them in).
2. Tap **Test connection**. A green box with the ComfyUI version and your GPU
   name means success. Tap **Enter the app**.
3. Go to **Settings → What the server is missing → Check server**. The app
   compares its workflows with your server and lists missing nodes and models
   by name, with packs, folders and links. Install what it lists and check again.
4. Once it is green, switch to **Create**, pick the **Image** card, type a few
   words and press **Generate image**. That is the fastest first test — a few
   seconds. Then try the rest: music, video, photo restoration…

> The interface follows your phone's language. If your phone is not set to
> Czech you get English automatically; you can also force either language under
> **Settings → Language**. A few advanced strings are still Czech-only.

### Access from anywhere (optional, recommended)

At home a shared Wi-Fi is enough. To use the app away from home, install
[Tailscale](https://tailscale.com) (free for personal use) on both the PC and
the phone, sign both into the same account, and use the PC's Tailscale address
(`100.x.x.x`) in the app. Nothing is exposed to the internet — it is a private
VPN.

> Android tip: exclude Tailscale from battery optimization (Settings → Apps →
> Tailscale → Battery → Unrestricted), otherwise the system suspends it and the
> app will report that the computer is not responding.

### Higgs Audio — voicing the lines (optional)

The **Dialogue** card can make characters speak your lines. The voices are
synthesized by **Higgs Audio** — not part of ComfyUI, but a small separate
server (web UI on port 7860) that you run next to it:
[higgs-audio-studio-webui](https://github.com/Promptlab37/higgs-audio-studio-webui) —
a local interface for speech synthesis on top of the Higgs Audio model,
including voice cloning.

The app derives the Higgs address on its own (same PC as ComfyUI, port 7860) and
can start and stop it on demand so it does not fight with video generation over
GPU memory. **Everything else works without Higgs** — only voicing lines on the
Dialogue card requires it.

---

## When something does not work

| Symptom | Most common cause and fix |
|---|---|
| "Server is not responding" right at the test | ComfyUI is not running with `--listen 0.0.0.0`; wrong address or port; the PC firewall blocks port 8188 for private networks |
| Worked at home, not outside | the phone is not on the VPN — turn on Tailscale (and see the battery tip above) |
| "The ComfyUI-ALLinONE-MinimaxH3 pack is missing" | install the pack via Manager and **restart ComfyUI** — nodes are only loaded at startup |
| The server check lists missing models | download exactly those files (names match REQUIREMENTS.en.md) into the folders shown; a new file is picked up without a restart |
| The Timeline / Image edit card reports missing nodes | those two cards need packs that are not publicly available — the other seven cards work normally |
| Gradle sync fails in Android Studio | check your internet connection and that you use JDK 17 (File → Settings → Build Tools → Gradle → Gradle JDK) |
| Installing the APK from the command line fails | enable USB debugging on the phone and confirm the computer's fingerprint; or just copy the APK to the phone and open it |

If you get stuck, open an [Issue](https://github.com/Promptlab37/PocketComfy/issues)
and include the error text from the app.
