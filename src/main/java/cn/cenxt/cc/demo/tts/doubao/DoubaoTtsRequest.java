package cn.cenxt.cc.demo.tts.doubao;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoubaoTtsRequest {
    @JSONField(name = "app")
    private App app;
    @JSONField(name = "user")
    private User user;
    @JSONField(name = "audio")
    private Audio audio;
    @JSONField(name = "request")
    private Request request;

    @Data
    @Builder
    public static class App {
        @JSONField(name = "appid")
        private String appid;
        @JSONField(name = "token")
        private String token; // 目前未生效，使用默认值即可
        @JSONField(name = "cluster")
        private String cluster;
    }

    @Data
    @Builder
    public static class User {
        @JSONField(name = "uid")
        private String uid;
    }

    @Data
    @Builder
    public static class Audio {
        @JSONField(name = "voice_type")
        private String voiceType;
        @JSONField(name = "voice")
        private String voice;
        @JSONField(name = "encoding")
        private String encoding;
        @JSONField(name = "rate")
        private Integer rate;
        @JSONField(name = "speed_ratio")
        private Double speedRatio;
        @JSONField(name = "volume_ratio")
        private Double volumeRatio;
        @JSONField(name = "pitch_ratio")
        private Double pitchRatio;
        @JSONField(name = "emotion")
        private String emotion;
        @JSONField(name = "language")
        private String language;
    }

    @Data
    @Builder
    public static class Request {
        @JSONField(name = "reqid")
        private String reqID;
        @JSONField(name = "text")
        private String text;
        @JSONField(name = "text_type")
        private String textType;
        @JSONField(name = "operation")
        private String operation;
        @JSONField(name = "silence_duration")
        private Integer silenceDuration;
    }
}