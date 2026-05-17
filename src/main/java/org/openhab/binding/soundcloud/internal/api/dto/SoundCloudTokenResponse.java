package org.openhab.binding.soundcloud.internal.api.dto;

import com.google.gson.annotations.SerializedName;

public class SoundCloudTokenResponse {

    @SerializedName("access_token")
    public String accessToken = "";

    @SerializedName("refresh_token")
    public String refreshToken = "";

    /** Token lifetime in seconds (typically 3599). */
    @SerializedName("expires_in")
    public long expiresIn = 3600;
}
