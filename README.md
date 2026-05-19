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
| `chromecastTargetItem` | | String Item → `player#chromecast-target` |
| `chromecastTag` | | Tag on your Chromecast playuri items (default: `Chromecast`) |
| `chromecastControlItem` | | Player Item → `chromecast:...:control` (Play/Pause) |
| `chromecastStopItem` | | Switch Item → `chromecast:...:stop` |
| `chromecastVolumeItem` | | Dimmer Item → `chromecast:...:volume` (0–100) |
| `chromecastMuteItem` | | Switch Item → `chromecast:...:mute` |

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

The widget handles the full Chromecast flow without any rules:

1. **Search** — type a term and press Enter
2. **Select a track** — tap a result to load cover, title, artist and stream URL
3. **Select a Chromecast** — tap a chip at the bottom of the card (chips are discovered automatically via item tags)
4. **Press Play** — the stream URL is sent directly to the selected Chromecast
5. **Volume / Mute / Pause / Stop** — dedicated buttons and slider in the widget

If you prefer a rule-based approach (without the widget), you can still react to `player#stream-url` changes directly:

```javascript
// rules/soundcloud.js
rules.when().item("SC_StreamURL").changed().then(event => {
    items.getItem("YourChromecast_PlayURI").sendCommand(event.newState);
}).build("SoundCloud → Chromecast");
```

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
