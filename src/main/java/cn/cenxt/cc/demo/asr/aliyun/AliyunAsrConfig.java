package cn.cenxt.cc.demo.asr.aliyun;

import cn.cenxt.cc.mrcp.asr.AsrConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cenxt.asr.aliyun")
public class AliyunAsrConfig extends AsrConfig {

    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    private String url;
}
