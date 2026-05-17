package org.openhab.binding.soundcloud.internal.api.dto;

import com.google.gson.annotations.SerializedName;

public class SoundCloudUser {
    public long id;
    public String username = "";

    @SerializedName("avatar_url")
    public String avatarUrl = "";
}
