package org.openhab.binding.soundcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class SoundCloudConfiguration {
    /** SoundCloud API Client ID (required). */
    public String clientId = "";
    /** OAuth access token (optional, extends API access). */
    public @Nullable String oauthToken;
}
