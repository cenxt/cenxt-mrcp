package cn.cenxt.cc.demo.asr.doubao;


import cn.cenxt.cc.mrcp.asr.AsrConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cenxt.asr.doubao")
public class DoubaoAsrConfig extends AsrConfig {

    // 豆包TTS相关配置
    private String appId;
    private String accessToken;
    private String cluster;
    private String url;
}
