# openHAB SoundCloud Binding

A community binding that connects openHAB to SoundCloud.  
Search tracks, view cover art and stream audio directly to a Chromecast — all from a single MainUI widget.

> **Status: Alpha** — core features are working. Feedback welcome in the openHAB community forum.

---

## Features (v1.2.3)

- **Search** tracks by keyword — results appear instantly in the widget list
- **Cover art** at 500 × 500 px displayed alongside title and artist
- **Stream URL** resolved automatically to a direct MP3 link (progressive format, Chromecast-compatible)
- **Chromecast integration** built into the widget — select a Chromecast, press Play
- **Volume slider** for real-time Chromecast volume control
- **Pause / Stop / Mute** buttons mapped to the correct Chromecast binding channels
- **Playback state** channel (PLAYING / PAUSED / STOPPED)
- **OAuth 2.0** authentication — authorise once in the browser, tokens are stored and auto-refreshed

---




<img width="1919" height="922" alt="Screenshot 2026-05-19 033922" src="https://github.com/user-attachments/assets/f3472f6f-c59d-45cf-b3f4-fec435464925" />


<img width="1080" height="4652" alt="Screenshot_20260519_033512_openHAB" src="https://github.com/user-attachments/assets/aa5b6d75-f38c-44cd-833c-916dad4091ce" />


---

## Requirements

| Requirement | Notes |
|-------------|-------|
| openHAB **5.1.3 or newer** | older versions are not supported |
| Java 21+ | bundled with openHAB 5.x |
| SoundCloud API credentials | register an app at [soundcloud.com/you/apps](https://soundcloud.com/you/apps) — free for personal/home use |
| openHAB Chromecast Binding | optional, required for cast functionality |

---

## Installation

### 1. Download the JAR

Download `org.openhab.binding.soundcloud-1.2.3.jar` from the [latest release](https://github.com/pierrecinema/openhab-soundcloud-binding/releases/latest) and copy it to your openHAB `addons/` folder.

openHAB detects and loads the bundle automatically — no restart needed. Check **Settings → Bindings** to confirm it appears.

### 2. Install the widget

1. In the openHAB MainUI open **Developer Tools → Widgets**
2. Click the **+** button → import
3. Paste the content of `soundcloud_search.yaml` (also in the release assets)
4. Save

### 3. Build from source (optional)

```bash
git clone https://github.com/pierrecinema/openhab-soundcloud-binding.git
cd openhab-soundcloud-binding
mvn clean package -DskipTests
# copy target/org.openhab.binding.soundcloud-1.2.3.jar to your addons/ folder
```

---

## Configuration

### Thing

Add a Thing in **Settings → Things → + → SoundCloud Account**, or create `conf/things/soundcloud.things`:

```
Thing soundcloud:account:myaccount "SoundCloud" [
    clientId     = "YOUR_CLIENT_ID",
    clientSecret = "YOUR_CLIENT_SECRET",
    redirectUri  = "http://YOUR_OPENHAB_HOST:8080/soundcloud/callback"
]
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `clientId` | ✅ | Client ID from [soundcloud.com/you/apps](https://soundcloud.com/you/apps) |
| `clientSecret` | ✅ | Client Secret from your registered app |
| `redirectUri` | ✅ | Must match exactly what is registered in your SoundCloud app |
| `webClientId` | optional | Internal SoundCloud web client ID for api-v2 (default value included) |

### OAuth authorisation

After the Thing is created it goes **OFFLINE** and prints an authorisation URL in the log. Open the URL in any browser, log in to SoundCloud and grant access. The binding stores the tokens automatically and the Thing goes **ONLINE**. Tokens are refreshed in the background — you only need to authorise once.

### Items

Create `conf/items/soundcloud.items` (or add via the UI):

```
// Player
String   SC_TrackID       "Track ID"        { channel="soundcloud:account:myaccount:player#track-id" }
String   SC_Title         "Titel [%s]"      { channel="soundcloud:account:myaccount:player#title" }
String   SC_Artist        "Artist [%s]"     { channel="soundcloud:account:myaccount:player#artist" }
String   SC_ArtworkURL    "Cover [%s]"      { channel="soundcloud:account:myaccount:player#artwork-url" }
Number   SC_Duration      "Dauer [%.0f s]"  { channel="soundcloud:account:myaccount:player#duration" }
String   SC_StreamURL     "Stream URL [%s]" { channel="soundcloud:account:myaccount:player#stream-url" }
String   SC_State         "Status [%s]"     { channel="soundcloud:account:myaccount:player#playback-state" }
String   SC_CCTarget      "CC Target [%s]"  { channel="soundcloud:account:myaccount:player#chromecast-target" }

// Search
String   SC_Query         "Suche [%s]"      { channel="soundcloud:account:myaccount:search#query" }
String   SC_Results       "Ergebnisse [%s]" { channel="soundcloud:account:myaccount:search#results" }
```

For Chromecast, you also need the items from the **Chromecast Binding** already linked to your Chromecast thing. The widget uses these directly — no additional items need to be created manually.  
Tag your Chromecast `playuri` items with `Chromecast` so the widget discovers them automatically:

```
String MyChromecast_PlayURI "Chromecast Wohnzimmer" [ "Chromecast" ] { channel="chromecast:chromecast:abc123:playuri" }
```

---

## Widget setup

Add the `soundcloud_search` widget to a page and configure the following properties:

| Property | Required | Description |
|----------|----------|-------------|
| `queryItem` | ✅ | String Item → `search#query` |
| `resultsItem` | ✅ | String Item → `search#results` |
| `trackIdItem` | ✅ | String Item → `player#track-id` |
| `streamUrlItem` | | String Item → `player#stream-url` |
| `titleItem` | | String Item → `player#title` |
| `artistItem` | | String Item → `player#artist` |
| `artworkItem` | | String Item → `player#artwork-url` |
| `stateItem` | | String Item → `player#playback-state` |
| `chromecastTargetItem` | | String Item linked to `player#chromecast-target` — stores the selected Chromecast (e.g. `SC_CCTarget`) |
| `chromecastTag` | | Tag on your Chromecast `playuri` items (default: `Chromecast`) |

> **Note:** Pause, Stop, Volume and Mute are routed via four fixed proxy items (`SC_CC_Control`, `SC_CC_Stop`, `SC_CC_Volume`, `SC_CC_Mute`) and a routing rule — see [Chromecast routing rule](#chromecast-routing-rule) below. No per-device configuration is needed in the widget.

---

## Channel reference

| Channel | Type | R/W | Description |
|---------|------|-----|-------------|
| `player#track-id` | String | RW | Write a track ID to load it; reads back the current track ID |
| `player#playlist-id` | String | W | Write a playlist ID to load its first track |
| `player#title` | String | R | Current track title |
| `player#artist` | String | R | Current track artist / uploader |
| `player#artwork-url` | String | R | 500×500 px cover art URL |
| `player#duration` | Number | R | Track duration in seconds |
| `player#stream-url` | String | R | Direct MP3 URL (resolved via api-v2 transcodings) |
| `player#playback-state` | String | RW | PLAYING / PAUSED / STOPPED |
| `player#chromecast-target` | String | RW | Name of the selected Chromecast playuri item |
| `search#query` | String | W | Send a search term |
| `search#results` | String | R | JSON array: `[{id, title, artist, artwork, duration}, …]` |

---

## Chromecast workflow

1. **Search** — type a term and press Enter
2. **Select a track** — tap a result to load cover, title, artist and stream URL
3. **Select a Chromecast** — tap a chip at the bottom of the card (discovered automatically via item tags)
4. **Press Play** — the stream URL is sent directly to the selected Chromecast
5. **Volume / Mute / Pause / Stop** — dedicated buttons and slider in the widget

---

## Chromecast routing rule

The widget's Pause, Stop, Volume and Mute buttons always send to four fixed proxy items (`SC_CC_Control`, `SC_CC_Stop`, `SC_CC_Volume`, `SC_CC_Mute`). A JS rule reads which Chromecast is currently selected and forwards the command to the correct device-specific item.

**Why is a rule needed?**  
Widget expressions can only use fixed item names. They cannot dynamically look up which control item belongs to the currently selected Chromecast. The routing rule bridges this gap — one rule handles all devices, no per-device widget configuration required.

> **Planned for v1.3:** This logic will move into the binding itself, eliminating the need for any rule.

### Setup

**Step 1 — Create the four proxy items** (once, in Settings → Items or via `.items` file):

```
Player SC_CC_Control "SoundCloud CC Control"
Switch SC_CC_Stop    "SoundCloud CC Stop"
Dimmer SC_CC_Volume  "SoundCloud CC Volume"
Switch SC_CC_Mute    "SoundCloud CC Mute"
```

Also create the target item and link it to the binding channel:

```
String SC_CCTarget "SoundCloud CC Target" { channel="soundcloud:account:myaccount:player#chromecast-target" }
```

**Step 2 — Create the routing rule** in Settings → Rules → + (ECMAScript 2021, triggered by command on each of the four proxy items):

```javascript
var triggerItem = event.itemName;
var cmd         = event.receivedCommand.toString();
var targetName  = items.getItem('SC_CCTarget').state;

if (!targetName || targetName === 'NULL') return;

// Map each Chromecast playuri item to its control items.
// Add a new entry here whenever you add a Chromecast device.
var MAPPING = {
  'BuroCast_Play_URI': {
    control : 'BuroCast_Media_Control',
    stop    : 'BuroCast_Stop',
    volume  : 'BuroCast_Volume',
    mute    : 'BuroCast_Mute'
  },
  'Hifigruppe_Play_URI': {
    control : 'Hifigruppe_Media_Control',
    stop    : 'Hifigruppe_Stop',
    volume  : 'Hifigruppe_Volume',
    mute    : 'Hifigruppe_Mute'
  }
  // Add more devices here:
  // 'YourDevice_Play_URI': { control: '...', stop: '...', volume: '...', mute: '...' }
};

var map = MAPPING[targetName];
if (!map) { logger.warn('SC Router: no mapping for ' + targetName); return; }

var destName = null;
if      (triggerItem === 'SC_CC_Control') destName = map.control;
else if (triggerItem === 'SC_CC_Stop')    destName = map.stop;
else if (triggerItem === 'SC_CC_Volume')  destName = map.volume;
else if (triggerItem === 'SC_CC_Mute')    destName = map.mute;

if (destName) {
  items.getItem(destName).sendCommand(cmd);
  logger.info('SC Router: ' + triggerItem + ' → ' + destName + ' (' + cmd + ')');
}
```

Configure the rule triggers as **Item Command** on: `SC_CC_Control`, `SC_CC_Stop`, `SC_CC_Volume`, `SC_CC_Mute`.

**Step 3 — Configure the widget:**

- `chromecastTargetItem` = `SC_CCTarget`
- All other Chromecast props: leave empty

### Adding a new Chromecast

1. Tag the device's `playuri` item with `Chromecast` — it appears automatically as a chip in the widget
2. Add one entry to the `MAPPING` object in the routing rule with the item names for `control`, `stop`, `volume` and `mute`

---

## Roadmap

| Version | Status | Description |
|---------|--------|-------------|
| v1.0.x | ✅ done | Core binding: search, track load, stream URL via api-v2 |
| v1.1.x | ✅ done | Widget: search results list, track selection, cover art |
| v1.2.x | ✅ done | Chromecast integration: chip selection, play/pause/stop/mute/volume |
| v1.3.x | planned | Auto-cast when track changes (no manual Play press needed) |
| v1.4.x | planned | Playlist queue: next / previous track navigation |
| v1.5.x | planned | Progress bar / elapsed time display |
| v2.0.0 | planned | Submit to openHAB add-ons repository |

---

## Known limitations (Alpha)

- Only the first track of a playlist is played (queue not yet implemented)
- Stream URLs are time-limited by SoundCloud and expire after some time; reloading the track generates a fresh URL
- The SoundCloud API requires a registered app — streaming is only available when a valid Client ID / OAuth token is configured

---

## License

MIT
