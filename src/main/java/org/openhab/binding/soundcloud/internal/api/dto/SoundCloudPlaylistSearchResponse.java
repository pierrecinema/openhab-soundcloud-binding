package org.openhab.binding.soundcloud.internal.api.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class SoundCloudPlaylistSearchResponse {
    public List<SoundCloudPlaylist> collection = List.of();

    @SerializedName("total_results")
    public int totalResults;
}
