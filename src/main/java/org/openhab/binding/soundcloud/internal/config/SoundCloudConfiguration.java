package org.openhab.binding.soundcloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class SoundCloudConfiguration {
    public String clientId = "";
    public String clientSecret = "";
    public String redirectUri = "";
    /** SoundCloud web client_id for api-v2 search (update here if SoundCloud rotates it). */
    public String webClientId = "gxPRNsEq7CDD7Wvem4iymWOq3YfU7KS8";
}
