package cn.cenxt.cc.demo.ai.coze;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cenxt.ai.coze")
@Data
public class CozeConfig {

    // coze智能体相关配置
    private String apiUrl;
    private String apiKey;
    private String botId;
}
