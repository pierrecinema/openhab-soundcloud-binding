package org.openhab.binding.soundcloud.internal.api.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class SoundCloudTrack {
    public long id;
    public String title = "";

    /** Duration in milliseconds. */
    public long duration;

    @SerializedName("artwork_url")
    public String artworkUrl = "";

    @SerializedName("permalink_url")
    public String permalinkUrl = "";

    @SerializedName("stream_url")
    public String streamUrl = "";

    public SoundCloudUser user = new SoundCloudUser();
    public Media media = new Media();

    public static class Media {
        public List<Transcoding> transcodings = List.of();
    }

    public static class Transcoding {
        public String url = "";
        public Format format = new Format();
    }

    public static class Format {
        /** "progressive" (direct MP3) or "hls". */
        public String protocol = "";

        @SerializedName("mime_type")
        public String mimeType = "";
    }
}
