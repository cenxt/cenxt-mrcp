package cn.cenxt.cc.demo.asr.doubao;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoubaoAsrParams {
    private App app;
    private User user;
    private Request request;
    private Audio audio;

    @Builder
    @Data
    public static class App {
        private String appid;
        private String cluster;
        private String token;
    }

    @Builder
    @Data
    public static class User {
        private String uid;
    }

    @Builder
    @Data
    public static class Request {
        private String reqid;
        private String workflow;
        private int nbest;
        private boolean show_utterances;
        private String result_type;
        private int sequence;
        private boolean vad_signal;
        private String start_silence_time;
        private String vad_silence_time;
    }

    @Builder
    @Data
    public static class Audio {
        private String format;
        private String codec;
        private int rate;
        private int bits;
        private int channels;

    }


    public static class MessageType {
        static public int FULL_CLIENT_REQUEST = 0b0001;
        static public int AUDIO_ONLY_CLIENT_REQUEST = 0b0010;
        static public int FULL_SERVER_RESPONSE = 0b1001;
        static public int SERVER_ACK = 0b1011;
        static public int ERROR_MESSAGE_FROM_SERVER = 0b1111;
    }
}
