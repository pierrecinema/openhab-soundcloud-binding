# openHAB SoundCloud Binding

A community binding that connects openHAB to SoundCloud.  
Search tracks, browse playlists, display cover art and stream audio to a Chromecast or any openHAB audio sink.

---

## Features (v0.1.0)

- **Search** tracks by keyword → results delivered as JSON
- **Load** any track or playlist by ID
- **Stream URL** resolved to a direct MP3 link (progressive format, Chromecast-compatible)
- **Playback state** channel (PLAYING / PAUSED / STOPPED)
- **Cover art** at 500 × 500 px
- Supports SoundCloud **Client ID** auth + optional OAuth token

---

## Requirements

| Requirement | Notes |
|-------------|-------|
| openHAB **5.1.3 or newer** | older versions are not supported |
| Java 21+ | bundled with openHAB 5.x |
| SoundCloud API key | apply at [soundcloud.com/you/apps](https://soundcloud.com/you/apps) — free for personal/home use |

---

## Installation

1. Build the JAR:
   ```bash
   mvn clean package -DskipTests
   ```
2. Copy `target/org.openhab.binding.soundcloud-0.1.0.jar` to your openHAB `addons/` folder.
3. openHAB detects and loads the bundle automatically (check **Settings → Bindings**).

---

## Configuration

### 1. Thing — `conf/things/soundcloud.things`

Create this file in your openHAB `conf/things/` directory:

```
Thing soundcloud:account:myaccount "SoundCloud" [
    clientId     = "YOUR_CLIENT_ID",
    clientSecret = "YOUR_CLIENT_SECRET",
    redirectUri  = "http://localhost",
    oauthToken   = "YOUR_OAUTH_TOKEN"
]
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `clientId` | ✅ | Client ID from [soundcloud.com/you/apps](https://soundcloud.com/you/apps) |
| `clientSecret` | ✅ | Client Secret from your registered app |
| `redirectUri` | ✅ | Redirect URI registered in your app (e.g. `http://localhost`) |
| `oauthToken` | optional | OAuth access token for stream URL resolution |

### 2. Items — `conf/items/soundcloud.items`

Create this file in your openHAB `conf/items/` directory:

```
Group gSoundCloud "SoundCloud" <music>

// Player
String   SC_TrackID       "Track ID"           (gSoundCloud) { channel="soundcloud:account:myaccount:player#track-id" }
String   SC_PlaylistID    "Playlist ID"         (gSoundCloud) { channel="soundcloud:account:myaccount:player#playlist-id" }
String   SC_Title         "Titel [%s]"          (gSoundCloud) { channel="soundcloud:account:myaccount:player#title" }
String   SC_Artist        "Artist [%s]"         (gSoundCloud) { channel="soundcloud:account:myaccount:player#artist" }
String   SC_ArtworkURL    "Cover URL [%s]"      (gSoundCloud) { channel="soundcloud:account:myaccount:player#artwork-url" }
Number   SC_Duration      "Dauer [%d s]"        (gSoundCloud) { channel="soundcloud:account:myaccount:player#duration" }
String   SC_StreamURL     "Stream URL [%s]"     (gSoundCloud) { channel="soundcloud:account:myaccount:player#stream-url" }
String   SC_State         "Status [%s]"         (gSoundCloud) { channel="soundcloud:account:myaccount:player#playback-state" }

// Suche
String   SC_Query         "Suche [%s]"          (gSoundCloud) { channel="soundcloud:account:myaccount:search#query" }
String   SC_Results       "Ergebnisse [%s]"     (gSoundCloud) { channel="soundcloud:account:myaccount:search#results" }
```

openHAB picks up both files automatically — no restart needed.

---

## Chromecast integration

When the binding resolves a stream URL it updates `player#stream-url`.  
Use a simple rule to forward it to the openHAB Chromecast binding:

```
rule "SoundCloud → Chromecast"
when
    Item SC_StreamURL changed
then
    // Replace with your Chromecast thing channel
    ChromeCast_LivingRoom_MediaUri.sendCommand(SC_StreamURL.state.toString)
end
```

The Chromecast binding will start playing the MP3 stream immediately.

---

## Usage examples

### Search and play

```
// In a rule or via UI:
SC_Query.sendCommand("lofi hip hop")
// SC_Results now contains JSON: [{id, title, artist, artwork, duration}, ...]

// Pick track id from results and load it:
SC_TrackID.sendCommand("1234567890")
// SC_StreamURL is updated → Chromecast rule fires
```

### Play/Pause/Stop

```
SC_State.sendCommand("PAUSE")
SC_State.sendCommand("PLAY")
SC_State.sendCommand("STOP")
```

---

## Channel reference

| Channel | Type | R/W | Description |
|---------|------|-----|-------------|
| `player#track-id` | String | RW | Write track ID to load it |
| `player#playlist-id` | String | W | Write playlist ID to load first track |
| `player#title` | String | R | Current track title |
| `player#artist` | String | R | Current track artist |
| `player#artwork-url` | String | R | 500×500 cover art URL |
| `player#duration` | Number | R | Duration in seconds |
| `player#stream-url` | String | R | Direct MP3 URL for Chromecast |
| `player#playback-state` | String | RW | PLAYING / PAUSED / STOPPED |
| `search#query` | String | W | Send query to search |
| `search#results` | String | R | JSON array of up to 20 results |

---

## Roadmap

- [ ] v0.2.0 — MainUI widget (search box, result list, cover art, controls)
- [ ] v0.3.0 — Direct audio sink / Chromecast cast action
- [ ] v0.4.0 — Playlist queue and next/previous track

---

## License

MIT
