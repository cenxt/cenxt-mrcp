package cn.cenxt.cc.demo.tts.aliyun;

import cn.cenxt.cc.mrcp.common.StopHandle;
import cn.cenxt.cc.mrcp.tts.TtsConfig;
import cn.cenxt.cc.mrcp.tts.TtsProcessHandler;
import cn.cenxt.cc.mrcp.tts.TtsReceiveListener;
import cn.cenxt.cc.mrcp.tts.TtsRequest;
import cn.cenxt.cc.demo.util.LogUtil;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 功能描述：阿里云TTS处理器
 *
 * @author cenxt
 * @email cpt725@qq.com
 * @date 2025/8/12 23:20
 */
@Slf4j
@Component
public class AliyunTtsProcessor implements TtsProcessHandler {

    @Autowired
    private AliyunTtsConfig config;

    private NlsClient client;

    private long expireTime;


    public void init() {
        System.setProperty("nls.ws.connect.timeout", "3000");
        AccessToken accessToken = new AccessToken(config.getAccessKeyId(), config.getAccessKeySecret());
        try {
            accessToken.apply();
            log.info("get token: " + accessToken.getToken() + ", expire time: " + accessToken.getExpireTime());
            expireTime = accessToken.getExpireTime();
            if (config.getUrl() == null || config.getUrl().isEmpty()) {
                client = new NlsClient(accessToken.getToken());
            } else {
                client = new NlsClient(config.getUrl(), accessToken.getToken());
            }
        } catch (IOException e) {
            log.error("get token failed", e);
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
    public TtsConfig getConfig() {
        return config;
    }

    /**
     * 执行TTS合成
     *
     * @param request  TTS请求参数
     * @param listener TTS数据接收监听器
     */
    @Override
    public StopHandle start(TtsRequest request, TtsReceiveListener listener) {
        log.info("aliyun tts request:{}", request);
        SpeechSynthesizer synthesizer = null;
        AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
        try {
            //创建实例，建立连接。
            synthesizer = new SpeechSynthesizer(getClient(), new SpeechSynthesizerListener() {

                private boolean firstRecvBinary = true;

                @Override
                public void onComplete(SpeechSynthesizerResponse speechSynthesizerResponse) {
                    log.info("aliyun tts onComplete. status_text: {} ,taskId:{}", speechSynthesizerResponse.getStatusText(), speechSynthesizerResponse.getTaskId());
                    listener.complete();
                }

                @Override
                public void onFail(SpeechSynthesizerResponse response) {
                    log.error("aliyun tts  onFail task_id: {}, status: {}, status_text: {}"
                            , response.getTaskId(), response.getStatus(), response.getStatusText());
                    listener.complete();
                }

                @Override
                public void onMessage(ByteBuffer byteBuffer) {
                    log.debug("aliyun tts onMessage");
                    try {
                        if (firstRecvBinary) {
                            LogUtil.setUuid(request.getUuid());
                            //计算首包语音流的延迟，收到第一包语音流时，即可以进行语音播放，以提升响应速度（特别是实时交互场景下）。
                            firstRecvBinary = false;
                            log.info("aliyun tts first latency");
                        }

                        byte[] data;
                        if (byteBuffer.hasArray()) {
                            // 如果ByteBuffer有底层数组，则直接使用
                            data = byteBuffer.array();
                        } else {
                            // 如果是直接内存缓冲区，则需要复制数据
                            data = new byte[byteBuffer.remaining()];
                            byteBuffer.get(data);
                        }
                        listener.send(data, format);
                    } catch (Exception e) {
                        log.error("aliyun tts onMessage process error", e);
                    }
                }
            });
            synthesizer.setAppKey(config.getAppKey());
            //设置返回音频的编码格式
            synthesizer.setFormat(OutputFormatEnum.PCM);
            //设置返回音频的采样率
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
            String voiceName = request.getVoiceName();
            if (!StringUtils.hasText(request.getVoiceName())) {
                voiceName = "zhitian_emo";
            }
            //发音人
            synthesizer.setVoice(voiceName);
            //语调，范围是-500~500，可选，默认是0。
            double speedRate = request.getSpeedRate();
            if (speedRate <= 0) {
                speedRate = 1.0;
            }
            synthesizer.setPitchRate((int) (100 * speedRate));
            //语速，范围是-500~500，默认是0。
            synthesizer.setSpeechRate(0);
            //设置用于语音合成的文本
            String string = request.getText().replaceAll("[\\r\\n]", "");
            synthesizer.setText(string);
            synthesizer.start();
            log.info("aliyun tts start");
        } catch (Exception e) {
            log.error("aliyun tts start error", e);
        }

        SpeechSynthesizer finalSynthesizer = synthesizer;
        return new StopHandle() {
            /**
             * 停止TTS处理
             */
            @Override
            protected void onStop() {
                if (null != finalSynthesizer) {
                    try {
                        finalSynthesizer.close();
                    } catch (Exception e) {
                        log.warn("aliyun tts close error", e);
                    }
                }
                listener.complete();
            }
        };
    }
}
