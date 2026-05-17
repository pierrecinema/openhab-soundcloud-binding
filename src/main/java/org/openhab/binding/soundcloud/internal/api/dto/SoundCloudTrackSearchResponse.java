package org.openhab.binding.soundcloud.internal.api.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class SoundCloudTrackSearchResponse {
    public List<SoundCloudTrack> collection = List.of();

    @SerializedName("total_results")
    public int totalResults;
}
