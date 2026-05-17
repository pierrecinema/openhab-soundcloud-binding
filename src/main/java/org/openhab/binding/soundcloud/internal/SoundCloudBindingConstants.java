package org.openhab.binding.soundcloud.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

@NonNullByDefault
public class SoundCloudBindingConstants {

    public static final String BINDING_ID = "soundcloud";

    // Thing type
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");

    // Player channel group + channels  (group#channel)
    public static final String CHANNEL_TITLE          = "player#title";
    public static final String CHANNEL_ARTIST         = "player#artist";
    public static final String CHANNEL_ARTWORK_URL    = "player#artwork-url";
    public static final String CHANNEL_DURATION       = "player#duration";
    public static final String CHANNEL_STREAM_URL     = "player#stream-url";
    public static final String CHANNEL_PLAYBACK_STATE = "player#playback-state";
    public static final String CHANNEL_TRACK_ID       = "player#track-id";
    public static final String CHANNEL_PLAYLIST_ID    = "player#playlist-id";

    // Search channel group + channels
    public static final String CHANNEL_SEARCH_QUERY   = "search#query";
    public static final String CHANNEL_SEARCH_RESULTS = "search#results";

    // Config property keys
    public static final String CONFIG_CLIENT_ID   = "clientId";
    public static final String CONFIG_OAUTH_TOKEN = "oauthToken";

    // SoundCloud API v2
    public static final String API_BASE_URL = "https://api-v2.soundcloud.com";
}
