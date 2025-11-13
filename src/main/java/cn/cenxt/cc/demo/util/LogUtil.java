package cn.cenxt.cc.demo.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public class LogUtil {

    /**
     * 设置uuid
     *
     * @param uuid uuid
     */
    public static void setUuid(String uuid) {
        if (StringUtils.hasText(uuid)) {
            MDC.put("uuid", uuid);
        }
    }
}
