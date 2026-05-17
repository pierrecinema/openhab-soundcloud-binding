package org.openhab.binding.soundcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class SoundCloudConfiguration {
    /** SoundCloud API Client ID (required). */
    public String clientId = "";
    /** SoundCloud API Client Secret (required). */
    public String clientSecret = "";
    /** OAuth redirect URI registered in your SoundCloud app (required). */
    public String redirectUri = "";
    /**
     * One-time authorization code from the OAuth redirect URL.
     * Paste the value of the "code" parameter after authorizing in the browser.
     * The binding exchanges it for tokens automatically and does not need it again.
     */
    public @Nullable String authorizationCode;
}
