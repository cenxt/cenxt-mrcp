package cn.cenxt.cc.demo.asr.aliyun;

import cn.cenxt.cc.mrcp.asr.*;
import cn.cenxt.cc.demo.util.LogUtil;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 功能描述：阿里云ASR处理器
 *
 * @author cenxt
 * @email cpt725@qq.com
 * @date 2025/8/13 00:05
 */

@Slf4j
@Component
public class AliyunAsrProcessor implements AsrProcessHandler {


    @Autowired
    private AliyunAsrConfig config;

    private NlsClient client;
    private long expireTime;

    public void init() {
        System.setProperty("nls.ws.connect.timeout", "3000");
        AccessToken accessToken = new AccessToken(config.getAccessKeyId(), config.getAccessKeySecret());
        try {
            accessToken.apply();
            log.info("aliyun asr  get token: {}, expire time: {}", accessToken.getToken(), accessToken.getExpireTime());
            expireTime = accessToken.getExpireTime();
            if (config.getUrl() == null || config.getUrl().isEmpty()) {
                client = new NlsClient(accessToken.getToken());
            } else {
                client = new NlsClient(config.getUrl(), accessToken.getToken());
            }
        } catch (IOException e) {
            log.error("aliyun asr get token failed", e);
        }
    }

    private synchronized NlsClient getClient() {
        if (client == null || System.currentTimeMillis() + 60000 > expireTime) {
            synchronized (this) {
                if (client == null) {
                    init();
                }
            }
        }
        if (client == null) {
            throw new RuntimeException("client init failed");
        }
        return client;
    }
    @Override
    public String getName() {
        return "aliyun";
    }

    @Override
    public AsrConfig getConfig() {
        return config;
    }

    /**
     * 执行ASR识别
     *
     * @param request  ASR请求参数
     * @param listener ASR结果接收监听
     */
    @Override
    public AsrHandle start(AsrRequest request, AsrResultListener listener) {
        log.info("aliyun asr request:{}", request);
        config.setParams(request);
        AtomicBoolean isStop = new AtomicBoolean(false);
        SpeechTranscriber transcriber = null;
        try {
            //创建实例、建立连接。
            transcriber = new SpeechTranscriber(getClient(), new SpeechTranscriberListener() {
                @Override
                public void onTranscriberStart(SpeechTranscriberResponse speechTranscriberResponse) {
                    LogUtil.setUuid(request.getUuid());
                    log.info("aliyun asr onTranscriberStart task_id: " + speechTranscriberResponse.getTaskId());
                }

                @Override
                public void onSentenceBegin(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("aliyun asr onSentenceBegin task_id: " + speechTranscriberResponse.getTaskId() + ", name: " + speechTranscriberResponse.getName() + ", status: " + speechTranscriberResponse.getStatus());
                }

                @Override
                public void onSentenceEnd(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("aliyun asr onSentenceEnd" +
                            " taskId=" + speechTranscriberResponse.getTaskId() +
                            " sentenceBeginTime=" + speechTranscriberResponse.getSentenceBeginTime() +
                            " sentenceTime=" + speechTranscriberResponse.getTransSentenceTime() +
                            " sentenceIndex=" + speechTranscriberResponse.getTransSentenceIndex() +
                            " sentenceText=" + speechTranscriberResponse.getTransSentenceText()
                    );

                    listener.complete(speechTranscriberResponse.getTransSentenceText());

                }

                @Override
                public void onTranscriptionResultChange(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("aliyun asr onTranscriptionResultChange " +
                            " taskId=" + speechTranscriberResponse.getTaskId() +
                            " sentenceBeginTime=" + speechTranscriberResponse.getSentenceBeginTime() +
                            " sentenceTime=" + speechTranscriberResponse.getTransSentenceTime() +
                            " sentenceIndex=" + speechTranscriberResponse.getTransSentenceIndex() +
                            " sentenceText=" + speechTranscriberResponse.getTransSentenceText()
                    );
                    listener.input();
                    listener.change(speechTranscriberResponse.getTransSentenceText());

                }

                @Override
                public void onTranscriptionComplete(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.debug("aliyun asr onTranscriptionComplete" +
                            " taskId=" + speechTranscriberResponse.getTaskId() +
                            " status=" + speechTranscriberResponse.getStatus() +
                            " statusText=" + speechTranscriberResponse.getStatusText() +
                            " sentenceTime=" + speechTranscriberResponse.getTransSentenceTime() +
                            " sentenceIndex=" + speechTranscriberResponse.getTransSentenceIndex() +
                            " sentenceText=" + speechTranscriberResponse.getTransSentenceText()
                    );
                }

                @Override
                public void onFail(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.error("aliyun asr onFail taskId={} status={} statusText={}", speechTranscriberResponse.getTaskId(), speechTranscriberResponse.getStatus(), speechTranscriberResponse.getStatusText());
                    listener.complete(null);
                }
            });

            transcriber.setAppKey(config.getAppKey());
            //输入音频编码方式。
            transcriber.setFormat(InputFormatEnum.PCM);
            //输入音频采样率。
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
            //是否返回中间识别结果。
            transcriber.setEnableIntermediateResult(true);
            //是否生成并返回标点符号。
            transcriber.setEnablePunctuation(true);
            //是否将返回结果规整化，比如将一百返回为100。
            transcriber.setEnableITN(false);

            long speechCompleteTimeout = request.getSpeechCompleteTimeout();
            if (speechCompleteTimeout <= 0) {
                speechCompleteTimeout = 1000;
            }
            //设置vad断句参数。默认值：800ms，有效值：200ms～6000ms。
            transcriber.addCustomedParam("max_sentence_silence", speechCompleteTimeout);
            log.info("start transcriber");
            transcriber.start();
        } catch (Exception e) {
            log.error("RECOGNIZE error", e);
            if (transcriber != null) {
                try {
                    transcriber.stop();
                } catch (Exception ex) {
                    log.warn("transcriber.stop error", ex);
                }
            }
        }

        SpeechTranscriber finalTranscriber = transcriber;
        return new AsrHandle() {
            /**
             * 停止处理
             */
            @Override
            protected void onStop() {
                log.info("aliyun asr stop");
                if (finalTranscriber != null) {
                    try {
                        finalTranscriber.close();
                    } catch (Exception ex) {
                        log.warn("finalTranscriber.stop error", ex);
                    }
                    listener.complete(null);
                }
            }

            /**
             * 接收数据
             *
             * @param bytes 数据
             */
            @Override
            protected void onReceive(byte[] bytes) {
                log.debug("aliyun asr send payload:{}", bytes.length);
                if (finalTranscriber != null) {
                    finalTranscriber.send(bytes, bytes.length);
                }
            }
        };
    }
}
