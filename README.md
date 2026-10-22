
# openHAB SoundCloud Binding

A community binding that connects openHAB to SoundCloud.  
Search tracks, view cover art and stream audio directly to a Chromecast — all from a single MainUI widget.

> **Status: Beta (v1.6.1)** — core features are working. Feedback welcome in the openHAB community forum.
> https://community.openhab.org/t/openhab-soundcloud-binding-new/169324/2

---

## Features (v1.6.1)

- **Search** tracks by keyword — results appear instantly in the widget list
- **Cover art** at 500 × 500 px displayed alongside title and artist
- **Chromecast playback built into the binding** — select a Chromecast, tap a track; every newly loaded track starts automatically
- **Pause / Play / Stop, volume and mute** — the binding controls the selected Chromecast directly, no rules or proxy items needed
- **Volume and mute mirrored back** — changes made on the Chromecast (e.g. in the Google Home app) show up in openHAB within ~2 s
- **Reliable start** — if the Chromecast first has to launch its cast app, the stream URL is resent automatically after 5 s
- **Stop keeps the track** — Play after Stop restarts the same track with a fresh stream URL
- **Seek / 30-second skip** — progressive MP3 range request, or an HLS playlist proxy with CORS headers for Chromecast
- **Playlist queue** with Next / Previous, auto-advance, repeat (OFF / TRACK / PLAYLIST) and shuffle
- **Progress bar** and **track counter** (e.g. 3 / 12)
- **OAuth 2.0** — authorise once in the browser, tokens are stored and refreshed automatically
- **MainUI widget** — now-playing card with search, progress bar and Chromecast selection

---

<img src="docs/screenshot-player.png" alt="Now-playing widget" width="380"> <img src="docs/screenshot-search.png" alt="Search panel" width="370">

---

## Requirements

| Requirement | Notes |
|-------------|-------|
| openHAB **5.1.3 or newer** | older versions are not supported |
| Java 21+ | bundled with openHAB 5.x |
| SoundCloud API credentials | every user registers their own app at [soundcloud.com/you/apps](https://soundcloud.com/you/apps) and gets their own Client ID and Client Secret — free for personal/home use |
| openHAB Chromecast Binding | required for playback |

No rule engine and no JavaScript Scripting add-on are needed (since v1.6.0).

---

## Installation

### 1. Download the JAR

Download `org.openhab.binding.soundcloud-1.6.1.jar` from the [latest release](https://github.com/pierrecinema/openhab-soundcloud-binding/releases/latest) and copy it to your openHAB `addons/` folder. **Remove older versions of the JAR first.**

openHAB detects and loads the bundle automatically — no restart needed. Check **Settings → Bindings** to confirm it appears.

> After swapping the JAR a thing may stay `UNINITIALIZED`. Disable and re-enable it once (Settings → Things → the thing → ⋮).

### 2. Install the widget

1. In the openHAB MainUI open **Developer Tools → Widgets**
2. Click **+** and paste the content of [`soundcloud_nowplaying.yaml`](openhab-conf/widgets/soundcloud_nowplaying.yaml) (also in the release assets)
3. Save

The now-playing card: background follows the cover colours, scrolling title, search panel, seekable progress bar, Chromecast chips. Its texts follow the openHAB language (German or English — Settings → Regional Settings → Language).

### 3. Build from source (optional)

```bash
git clone https://github.com/pierrecinema/openhab-soundcloud-binding.git
cd openhab-soundcloud-binding
mvn clean package -DskipTests
# copy target/org.openhab.binding.soundcloud-1.6.1.jar to your addons/ folder (remove older versions first)
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
| `clientId` | ✅ | Client ID of your app from [soundcloud.com/you/apps](https://soundcloud.com/you/apps) |
| `clientSecret` | ✅ | Client Secret of your app |
| `redirectUri` | ✅ | Must match exactly what is registered in your SoundCloud app |
| `webClientId` | optional | Web client ID used for the search (SoundCloud api-v2). A default is included — see below if SoundCloud rotates it |
| `playUriItem` | advanced | Leave empty. Only for setups that still forward stream URLs through their own router rule |

**Finding the current web client ID** (only needed if the search stops working):

1. Open soundcloud.com in a desktop browser and press **F12** to open the developer tools
2. Go to the **Network** tab and type `client_id` into the filter box
3. Reload the page (F5) and play any track if nothing appears
4. Click a request to `api-v2.soundcloud.com/...` and copy the 32-character value of the `client_id=` parameter

### OAuth authorisation

After the Thing is created it goes **OFFLINE** and prints an authorisation URL in the log. Open the URL in any browser, log in to SoundCloud and grant access. The binding stores the tokens automatically and the Thing goes **ONLINE**. Tokens are refreshed in the background — you only need to authorise once.

### Items

Create `conf/items/soundcloud.items` (or add via the UI):

```
// Player
String   SC_TrackID       "Track ID"          { channel="soundcloud:account:myaccount:player#track-id" }
String   SC_Title         "Title [%s]"        { channel="soundcloud:account:myaccount:player#title" }
String   SC_Artist        "Artist [%s]"       { channel="soundcloud:account:myaccount:player#artist" }
String   SC_ArtworkURL    "Cover [%s]"        { channel="soundcloud:account:myaccount:player#artwork-url" }
Number   SC_Duration      "Duration [%.0f s]" { channel="soundcloud:account:myaccount:player#duration" }
Number   SC_Elapsed       "Elapsed [%.0f s]"  { channel="soundcloud:account:myaccount:player#elapsed-time" }
String   SC_StreamURL     "Stream URL [%s]"   { channel="soundcloud:account:myaccount:player#stream-url" }
String   SC_State         "Status [%s]"       { channel="soundcloud:account:myaccount:player#playback-state" }
String   SC_CCTarget      "Chromecast [%s]"   { channel="soundcloud:account:myaccount:player#chromecast-target" }
Dimmer   SC_Volume        "Volume [%d %%]"    { channel="soundcloud:account:myaccount:player#volume" }
Switch   SC_Mute          "Mute"              { channel="soundcloud:account:myaccount:player#mute" }
Number   SC_QueueIndex    "Track [%d]"        { channel="soundcloud:account:myaccount:player#queue-index" }
Number   SC_QueueSize     "Tracks [%d]"       { channel="soundcloud:account:myaccount:player#queue-size" }
String   SC_Repeat        "Repeat [%s]"       { channel="soundcloud:account:myaccount:player#repeat" }
Switch   SC_Shuffle       "Shuffle"           { channel="soundcloud:account:myaccount:player#shuffle" }

// Search
String   SC_Query         "Search [%s]"       { channel="soundcloud:account:myaccount:search#query" }
String   SC_Results       "Results [%s]"      { channel="soundcloud:account:myaccount:search#results" }
```

### Chromecasts

The binding controls your Chromecasts through the items of the **Chromecast binding**. For every Chromecast you want to use:

1. Link its `playuri`, `control`, `stop`, `volume` and `mute` channels to items
2. Tag the `playuri` item with `Chromecast` — it appears as a chip in the widgets

```
String Wohnzimmer_PlayURI "Wohnzimmer" [ "Chromecast" ] { channel="chromecast:chromecast:abc123:playuri" }
Player Wohnzimmer_Control "Wohnzimmer Control"          { channel="chromecast:chromecast:abc123:control" }
Switch Wohnzimmer_Stop    "Wohnzimmer Stop"             { channel="chromecast:chromecast:abc123:stop" }
Dimmer Wohnzimmer_Volume  "Wohnzimmer Volume"           { channel="chromecast:chromecast:abc123:volume" }
Switch Wohnzimmer_Mute    "Wohnzimmer Mute"             { channel="chromecast:chromecast:abc123:mute" }
```

Selecting a chip writes the name of the `playuri` item to `player#chromecast-target`. The binding finds the other items of that Chromecast through the channel links.

---

## Widget setup

### Widget properties (`soundcloud_nowplaying`)

The blurred cover fills the background, long titles scroll, the search opens from the magnifier icon (Enter or ✓ searches, tapping a result loads and plays it), the progress bar is seekable and the selected Chromecast is shown as a chip — tap it to pick another device.

| Property | Required | Item |
|----------|----------|------|
| `titleItem` | ✅ | `player#title` |
| `stateItem` | ✅ | `player#playback-state` |
| `trackIdItem` | ✅ | `player#track-id` |
| `queryItem` | ✅ | `search#query` |
| `resultsItem` | ✅ | `search#results` |
| `targetItem` | ✅ | `player#chromecast-target` |
| `volumeItem`, `muteItem` | | `player#volume`, `player#mute` |
| `artistItem`, `artworkItem`, `streamUrlItem` | | `player#artist`, `player#artwork-url`, `player#stream-url` |
| `elapsedItem`, `durationItem` | | `player#elapsed-time`, `player#duration` (progress bar) |
| `seekPctItem` | | Number item 0–100 for seeking with the progress bar — needs the [seek rule](#seek-rule-optional) |
| `queueIndexItem`, `queueSizeItem` | | `player#queue-index`, `player#queue-size` (shows "3 / 12") |
| `shuffleItem`, `repeatItem` | | `player#shuffle`, `player#repeat` |
| `chromecastTag` | | Tag on your Chromecast `playuri` items (default `Chromecast`) |

### Seek rule (optional)

The progress bar of `soundcloud_nowplaying` works in percent. To seek with it, create a `Number SC_SeekPct` item, set it as `seekPctItem` and add a rule with an **Item State Change** trigger on `SC_Elapsed` and an **Item Command** trigger on `SC_SeekPct`, using [`openhab-conf/rules/soundcloud_seek_sync.js`](openhab-conf/rules/soundcloud_seek_sync.js). Without it, the progress bar is display-only.

---

## Channel reference

| Channel | Type | R/W | Description |
|---------|------|-----|-------------|
| `player#track-id` | String | RW | Write a track ID to load and play it; reads back the current track ID |
| `player#playlist-id` | String | W | Write a playlist ID to load it into the queue |
| `player#title` | String | R | Current track title |
| `player#artist` | String | R | Current track artist / uploader |
| `player#artwork-url` | String | R | 500×500 px cover art URL |
| `player#duration` | Number | R | Track duration in seconds |
| `player#elapsed-time` | Number | RW | Elapsed time in seconds (updates every second); write seconds to seek |
| `player#stream-url` | String | R | Current stream URL (HLS or progressive MP3) |
| `player#playback-state` | String | RW | PLAYING / PAUSED / STOPPED; accepts PLAY, PAUSE, STOP, NEXT, PREVIOUS |
| `player#chromecast-target` | String | RW | Name of the selected Chromecast `playuri` item |
| `player#volume` | Dimmer | RW | Volume of the selected Chromecast |
| `player#mute` | Switch | RW | Mute of the selected Chromecast |
| `player#queue-index` | Number | R | Current track position in the queue (1-based) |
| `player#queue-size` | Number | R | Total number of tracks in the queue |
| `player#repeat` | String | RW | OFF / TRACK / PLAYLIST |
| `player#shuffle` | Switch | RW | Shuffle playback order |
| `search#query` | String | W | Send a search term |
| `search#results` | String | R | JSON array: `[{id, title, artist, artwork, duration}, …]` |

---

## Upgrading from v1.5.x

v1.6.0 moved the Chromecast routing into the binding. Existing things get the new `volume` / `mute` channels automatically (and the old `my-playlists` channel is removed).

1. Replace the JAR (remove the old one first)
2. Create and link items for `player#volume` and `player#mute`, set them as `volumeItem` / `muteItem` in the widget
3. Clear the thing parameter `playUriItem` (Settings → Things → the thing → show advanced)
4. Re-import the widgets from this release
5. **Disable** the router rule and the auto-play rule; the proxy items `SC_CC_Control`, `SC_CC_Stop`, `SC_CC_Volume`, `SC_CC_Mute` and `SC_CC_PlayURI` are no longer used

The old rule scripts stay in [`openhab-conf/rules`](openhab-conf/rules) for reference.

---

## Roadmap

| Version | Status | Description |
|---------|--------|-------------|
| v1.0.x  | ✅ done | Core binding: search, track load, stream URL via api-v2 |
| v1.1.x  | ✅ done | Widget: search results list, track selection, cover art |
| v1.2.x  | ✅ done | Chromecast integration: chip selection, play/pause/stop/mute/volume |
| v1.3.x  | ✅ done | Auth resilience: token never deleted on transient errors; immediate ONLINE on restart |
| v1.4.1  | ✅ done | Progress bar: elapsed time channel, MM:SS display in widget |
| v1.4.10 | ✅ done | Seek: 30-second skip and slider via progressive MP3 + HTTP Range-Request |
| v1.5.0  | ✅ done | Playlist queue: next / previous, auto-advance, repeat (OFF/TRACK/PLAYLIST), shuffle |
| v1.5.4  | ✅ done | Stability: removed experimental playlist-browse feature |
| v1.5.5  | ✅ done | Widget redesign: Spotify-like layout; responsive mobile support |
| v1.5.7  | ✅ done | Binding sends new tracks to the Chromecast itself; Stop keeps the track; HLS seek fix (CORS); new `soundcloud_nowplaying` widget |
| v1.6.1  | ✅ done | Chromecast routing inside the binding: `volume` / `mute` channels mirrored from the device, pause / play / stop via `playback-state`, retry after cast-app launch — no rules or proxy items needed |
| v1.7.x  | planned | Likes / stream feed — browse your own liked tracks |
| v2.0.0  | planned | Submit to openHAB add-ons repository |

---

## Known limitations

- Stream URLs are time-limited by SoundCloud; reloading the track (or Play after Stop) generates a fresh URL
- The playback state reflects the binding, not the Chromecast — if another app takes over the Chromecast, the state may still show PLAYING
- The search uses SoundCloud's web API (api-v2); if SoundCloud rotates the web client ID, update `webClientId` (see [Thing](#thing))

---

for help https://community.openhab.org/t/openhab-soundcloud-binding-new/169324

## License

MIT
