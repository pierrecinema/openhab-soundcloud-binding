package org.openhab.binding.soundcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class SoundCloudConfiguration {
    /** SoundCloud API Client ID (required). */
    public String clientId = "";
    /** SoundCloud API Client Secret (required for OAuth flow). */
    public String clientSecret = "";
    /** OAuth redirect URI registered in your SoundCloud app (required for OAuth flow). */
    public String redirectUri = "";
    /** OAuth access token — filled automatically after auth or entered manually. */
    public @Nullable String oauthToken;
}
