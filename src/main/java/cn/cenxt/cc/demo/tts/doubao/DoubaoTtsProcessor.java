package cn.cenxt.cc.demo.tts.doubao;

import cn.cenxt.cc.demo.util.LogUtil;
import cn.cenxt.cc.mrcp.common.StopHandle;
import cn.cenxt.cc.mrcp.tts.TtsConfig;
import cn.cenxt.cc.mrcp.tts.TtsProcessHandler;
import cn.cenxt.cc.mrcp.tts.TtsReceiveListener;
import cn.cenxt.cc.mrcp.tts.TtsRequest;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sound.sampled.AudioFormat;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class DoubaoTtsProcessor implements TtsProcessHandler {

    @Autowired
    private DoubaoTtsConfig config;

    @Override
    public String getName() {
        return "doubao";
    }

    @Override
    public TtsConfig getConfig() {
        return config;
    }

    /**
     * 执行TTS合成
     *
     * @param request  TTS请求参数
     * @param listener TTS数据接收监听
     */
    @Override
    public StopHandle start(TtsRequest request, TtsReceiveListener listener) {
        log.info("doubao tts request:{}", request);
        AudioFormat format = new AudioFormat(8000, 16, 1, true, false);

        String uid = request.getCallee();
        if (!StringUtils.hasText(uid)) {
            uid = "uid";
        }
        String voiceName = request.getVoiceName();
        if (!StringUtils.hasText(request.getVoiceName())) {
            voiceName = "BV406_streaming";
        }
        double speedRate = request.getSpeedRate();
        if (speedRate <= 0) {
            speedRate = 1.0;
        }
        DoubaoTtsRequest doubaoTtsRequest = DoubaoTtsRequest.builder()
                .app(DoubaoTtsRequest.App.builder()
                        .appid(config.getAppId())
                        .cluster(config.getCluster())
                        .build())
                .user(DoubaoTtsRequest.User.builder()
                        .uid(uid)
                        .build())
                .audio(DoubaoTtsRequest.Audio.builder()
                        .encoding("pcm")
                        .voiceType(voiceName)
                        .rate(8000)
                        .speedRatio(speedRate)
                        .build())
                .request(DoubaoTtsRequest.Request.builder()
                        .reqID(UUID.randomUUID().toString())
                        .operation("query")
                        .text(request.getText())
                        .build())
                .build();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer; " + config.getAccessToken());
        headers.put("Content-Type", "text/plain; charset=utf-8");
        WebSocketClient ttsWebsocketClient = new WebSocketClient(URI.create(config.getUrl())
                , headers) {
            private boolean firstRecvBinary = true;

            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                LogUtil.setUuid(request.getUuid());
                log.info("doubao tts open");
            }

            @Override
            public void onMessage(ByteBuffer bytes) {

                int protocolVersion = (bytes.get(0) & 0xff) >> 4;
                int headerSize = bytes.get(0) & 0x0f;
                int messageType = (bytes.get(1) & 0xff) >> 4;
                int messageTypeSpecificFlags = bytes.get(1) & 0x0f;
                int serializationMethod = (bytes.get(2) & 0xff) >> 4;
                int messageCompression = bytes.get(2) & 0x0f;
                int reserved = bytes.get(3) & 0xff;
                bytes.position(headerSize * 4);
                byte[] fourByte = new byte[4];
                if (messageType == 11) {
                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        log.info("doubao tts first latency");
                    }
                    if (messageTypeSpecificFlags == 0) {
                        // Ack without audio data
                    } else {
                        bytes.get(fourByte, 0, 4);
                        int sequenceNumber = new BigInteger(fourByte).intValue();
                        bytes.get(fourByte, 0, 4);
                        int payloadSize = new BigInteger(fourByte).intValue();
                        byte[] payload = new byte[payloadSize];
                        bytes.get(payload, 0, payloadSize);
                        listener.send(payload, format);
                        if (sequenceNumber < 0) {
                            // received the last segment
                            this.close(CloseFrame.NORMAL, "received all audio data.");
                        }
                    }
                } else if (messageType == 15) {
                    // Error message from server
                    bytes.get(fourByte, 0, 4);
                    int code = new BigInteger(fourByte).intValue();
                    bytes.get(fourByte, 0, 4);
                    int messageSize = new BigInteger(fourByte).intValue();
                    byte[] messageBytes = new byte[messageSize];
                    bytes.get(messageBytes, 0, messageSize);
                    String message = new String(messageBytes, StandardCharsets.UTF_8);
                    log.error("doubao tts error:{}", message);
                    this.close(CloseFrame.NORMAL, message);
                } else {
                    log.warn("Received unknown response message type: {}", messageType);
                }
            }

            @Override
            public void onMessage(String s) {
                log.info("doubao tts onMessage:{}", s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log.info("doubao tts close");
                listener.complete();
            }

            @Override
            public void onError(Exception e) {
                log.error("doubao tts error", e);
                close(CloseFrame.NORMAL, e.toString());
                listener.complete();
            }
        };

        String json = JSON.toJSONString(doubaoTtsRequest);
        log.info("doubao tts send: {}", json);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = {0x11, 0x10, 0x10, 0x00};
        ByteBuffer requestByte = ByteBuffer.allocate(8 + jsonBytes.length);
        requestByte.put(header).putInt(jsonBytes.length).put(jsonBytes);
        try {
            ttsWebsocketClient.connectBlocking();
            ttsWebsocketClient.send(requestByte.array());
            log.info("doubao tts send success");
        } catch (Exception e) {
            log.error("doubao tts error", e);
        }

        return new StopHandle() {
            /**
             * 停止TTS处理
             */
            @Override
            protected void onStop() {
                log.info("doubao tts stop");
                ttsWebsocketClient.close();
                listener.complete();
            }
        };
    }
}
