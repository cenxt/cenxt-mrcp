FROM eclipse-temurin:17-jre-alpine
# 维护者信息
MAINTAINER cenxt "1217402001@qq.com"
RUN mkdir -p /appdata/cenxt-mrcp/
ADD dist/*.tar.gz /appdata/cenxt-mrcp/
ENV SPRING_PROFILES_ACTIVE prod
ENV TZ 'Asia/Shanghai'
VOLUME /appdata/cenxt-mrcp/config
#HEALTHCHECK --interval=60s --timeout=5s --retries=3 CMD curl -sS http://127.0.0.1:8888/cenxtv-service/channel/logs/score || exit 1
CMD sh /appdata/cenxt-mrcp/bin/cenxt-mrcp.sh start docker