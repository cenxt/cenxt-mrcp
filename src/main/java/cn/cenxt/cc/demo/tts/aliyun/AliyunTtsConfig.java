package cn.cenxt.cc.demo.tts.aliyun;

import cn.cenxt.cc.mrcp.tts.TtsConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cenxt.tts.aliyun")
@Data
public class AliyunTtsConfig extends TtsConfig {

    // 阿里云TTS相关配置
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    private String url;
}
