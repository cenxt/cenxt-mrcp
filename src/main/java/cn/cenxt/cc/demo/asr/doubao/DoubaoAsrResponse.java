package cn.cenxt.cc.demo.asr.doubao;

import lombok.Data;

@Data
public class DoubaoAsrResponse {
    private String reqid = "unknow";
    private int code = 0;
    private String message = "";
    private int sequence = 0;
    private Result[] result;
    private Addition addition;

    @Data
    public static class Result {
        private String text;
        private int confidence;
        private String language;
        private Utterances[] utterances;
        private float global_confidence;
    }

    @Data
    public static class Utterances {
        private String text;
        private int start_time;
        private int end_time;
        private boolean definite;
        private String language;
//        private Words[] words;
    }

    @Data
    public static class Words {
        private String text;
        private int start_time;
        private int end_time;
        private int blank_duration;
    }

    @Data
    public static class Addition {
        private String duration;
    }
}
