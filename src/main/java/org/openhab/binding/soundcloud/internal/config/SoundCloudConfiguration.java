package org.openhab.binding.soundcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class SoundCloudConfiguration {
    /** SoundCloud API Client ID (required). */
    public String clientId = "";
    /** SoundCloud API Client Secret (required). */
    public String clientSecret = "";
    /** OAuth Redirect URI registered in your SoundCloud app (required). */
    public String redirectUri = "";
}
