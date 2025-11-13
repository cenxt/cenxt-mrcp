package cn.cenxt.cc.demo.tts.doubao;

import cn.cenxt.cc.mrcp.tts.TtsConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cenxt.tts.doubao")
@Data
public class DoubaoTtsConfig extends TtsConfig {

    // 豆包TTS相关配置
    private String appId;
    private String accessToken;
    private String cluster;
    private String url;
}
