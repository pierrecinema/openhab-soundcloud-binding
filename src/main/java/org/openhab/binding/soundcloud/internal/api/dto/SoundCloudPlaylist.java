package org.openhab.binding.soundcloud.internal.api.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class SoundCloudPlaylist {
    public long id;
    public String title = "";

    @SerializedName("artwork_url")
    public String artworkUrl = "";

    public SoundCloudUser user = new SoundCloudUser();

    @SerializedName("track_count")
    public int trackCount;

    public List<SoundCloudTrack> tracks = List.of();
}
