# CENXT MRCP Server

CENXT MRCP Server 是一个基于Java实现的MRCPv2协议服务器，支持语音识别(ASR)、语音合成(TTS)和AI对话功能。

项目地址：https://github.com/cenxt/cenxt-mrcp

## 功能特性

- **MRCPv2协议支持**：完全兼容MRCPv2协议规范
- **多引擎支持**：
    - ASR引擎：阿里云、字节跳动豆包（支持自定义）
    - TTS引擎：阿里云、字节跳动豆包（支持自定义）
    - AI对话引擎：Coze平台（支持自定义）
- **大模型对话TTS支持**：支持将大模型流式输出通过tts流式播放
- **灵活配置**：支持多种引擎配置
- **灵活路由**：支持自定义路由，和根据被叫号前缀，路由到不同的ASR/TTS引擎
- **容器化部署**：提供Docker支持
- **虚拟机部署**：提供Linux安装包支持
- QQ交流群：1062716087
## 技术架构

- 基于Spring Boot 3.4.5
- Java 17

## 系统要求

- Java 17或更高版本
- 至少128MB可用内存

## 快速开始

### 1、Freeswitch编译安装支持MRCP

- 参考地址：https://freeswitch.org.cn/books/case-study/1.9-mod_unimrcp-install.html#mod_unimrcp-install
- 配置参考：https://freeswitch.org.cn/books/case-study/1.11-aliyun-mrcp.html#aliyun-mrcp

### 2、Freeswitch配置

#### （1）新增lua脚本 ```cenxt.lua```

- 脚本中包括3个自定义参数：caller、callee、uuid。
- caller被叫号码，用于传递给智能体进行用户身份识别
- callee被叫号码，用于ASR/TTS路由配置
- uuid会话ID，用于日志记录和关联大模型会话id
- 共支持4个参数，还有engine用于指定ASR/TTS模型名称，如：doubao-tts

```
session:answer();
ans = "电话接通"
local count=0
while session:ready() == true do
  local caller = session:getVariable('caller_id_name')
  local callee = session:getVariable('destination_number')
  local uuid = session:getVariable('uuid')
  local tts_voice = 'zhitian_emo'
  session:execute('set', 'tts_engine=unimrcp')
  session:execute('set', 'tts_voice=' ..tts_voice)
  session:execute("play_and_detect_speech", "say:{caller="..caller..",callee="..callee..",uuid="..uuid.."}"..ans.." detect:unimrcp {start-input-timers=false,caller="..caller..",callee="..callee..",uuid="..uuid.."} alimrcp")
  local xml = session:getVariable('detect_speech_result')
  if xml ~= nil and xml:match("<result>(.-)</result>") ~= nil then
    freeswitch.consoleLog("NOTICE", caller.."识别结果:"..xml .."\n")
    ans = xml:match("<result>(.-)</result>")
    count=0
  else
    freeswitch.consoleLog("NOTICE", caller.."识别失败:"..xml)
    ans = '没有说话'
    count=count+1
    if(count>=3) then
      freeswitch.consoleLog("NOTICE", "超过等待次数："..count)
      session:execute("speak","unimrcp|"..tts_voice.."|{caller="..caller..",callee="..callee..",uuid="..uuid.."}长时间没说话，要再见了")
      session:hangup()
      break
    end
  end
end
```

#### （2）新增拨号计划

```
<extension name="unimrcp">
   <condition field="destination_number" expression="^10">
       <action application="answer"/>
       <action application="lua" data="cenxt.lua"/>
   </condition>
</extension>
```

### 3、CENXT-MRCP集成其他AI、ASR、TTS功能

#### （1）引入依赖
```
    <repositories>
        <repository>
            <id>cenxt-maven</id>
            <name>cenxt-maven</name>
            <url>http://nexus.cenxt.cn/repository/cenxt-maven/</url>
        </repository>
    </repositories>
    
    
    <dependency>
        <groupId>cn.cenxt.cc</groupId>
        <artifactId>cenxt-mrcp-core</artifactId>
        <version>1.1.0</version>
    </dependency>
```
#### （2）参照示例，实现接口 ```AsrProcessHandler``` 、```TtsProcessHandler``` 、```AiProcessHandler``` `

#### （3）启动类加上注解 ```@EnableCenxtMrcp```


### 4、CENXT-MRCP配置

配置文件 ```application.yml```

#### （1）最小配置，实现ASR和TTS功能

```
spring:
  application:
    name: cenxt-mrcp

logging:
  config: classpath:log4j2.xml

cenxt:
  mrcp:
    sip:
      port: 7010
      transport: udp
    # 可选 对外ip 可以不设置
    external-ip:
    # 可选，可以不设置 自动获取指定前缀的ip作为对外ip
    external-ip-prefix:
    # MRCP服务端口
    port: 1544
    # rtp端口范围
    rtp:
      start-port: 30000
      end-port: 32000
  tts:
    # 豆包tts服务 开通地址 https://console.volcengine.com/speech/app
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v1/tts/ws_binary
      # 可选 语音名称 BV001_streaming BV104_streaming
      voice-name: BV104_streaming
      # 可选 语速
      speed-rate: 1.0
  asr:
    # 豆包asr服务 开通地址 https://console.volcengine.com/speech/app
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v2/asr
      # 可选,语音识别超时
      recognition-timeout: 10000
      # 可选,没有输入超时
      no-input-timeout: 5000
      # 可选,识别结束超时
      speech-complete-timeout: 800
      # 可选,识别中超时
      speech-incomplete-timeout: 10000


```

#### （2）多ASR/TTS引擎支持，同时配置动态路由策略

- 目前支持ASR引擎类型：aliyun、doubao
- 目前支持TTS引擎类型：aliyun、doubao
- **需传递自定义参数callee才能实现动态路由策略**
```
spring:
  application:
    name: cenxt-mrcp

logging:
  config: classpath:log4j2.xml

cenxt:
  mrcp:
    sip:
      port: 7010
      transport: udp
    # 可选 对外ip 可以不设置
    external-ip:
    # 可选，可以不设置 自动获取指定前缀的ip作为对外ip
    external-ip-prefix:
    # MRCP服务端口
    port: 1544
    # rtp端口范围
    rtp:
      start-port: 30000
      end-port: 32000
  tts:
    default-engine: doubao
    # 可选，路由规则。根据被叫号码匹配不同的tts服务
    route-rules:
      # 正则匹配
      - callee-regex: ^101\d+$
        # 可选 使用的引擎
        engine: aliyun
      - callee-regex: ^100\d+$
        # 可选 使用的引擎
        engine: doubao
    aliyun:
      app-key: <填写你申请的内容>
      access-key-id: <填写你申请的内容>
      access-key-secret: <填写你申请的内容>
      url: wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1
      # 可选 语音名称 zhimiao_emo zhitian_emo
      voice-name: zhitian_emo
      # 可选 语速
      speed-rate: 1.0
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v1/tts/ws_binary
      # 可选 语音名称 BV001_streaming BV104_streaming
      voice-name: BV104_streaming
      # 可选 语速
      speed-rate: 1.0
  asr:
    default-engine: aliyun
    # 可选，匹配规则
    route-rules:
      # 正则匹配
      - callee-regex: ^101\d+$
        # 可选 使用的引擎
        engine: aliyun
      - callee-regex: ^100\d+$
        # 可选 使用的引擎
        engine: doubao
    aliyun:
      app-key: <填写你申请的内容>
      access-key-id: <填写你申请的内容>
      access-key-secret: <填写你申请的内容>
      url: wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1
      # 可选,语音识别超时
      recognition-timeout: 10000
      # 可选,没有输入超时
      no-input-timeout: 3000
      # 可选,识别结束超时
      speech-complete-timeout: 800
      # 可选,识别中超时
      speech-incomplete-timeout: 10000
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v2/asr
      # 可选,语音识别超时
      recognition-timeout: 10000
      # 可选,没有输入超时
      no-input-timeout: 5000
      # 可选,识别结束超时
      speech-complete-timeout: 800
      # 可选,识别中超时
      speech-incomplete-timeout: 10000


```

#### （3）AI能力对接支持

- 目前支持AI引擎类型：coze

对接原理示意图：
```
┌───────────┐ 语音流 ┌───────────┐ 语音流 ┌───────────┐       
│           │───────>│           │───────>│           │       
│ FreeSWITCH│ recog  │cenxt-mrcp │        │  ASR服务  │       
│           │<───────│           │<───────│           │       
└───────────┘ ASR结果└───────────┘ ASR结果└───────────┘       
      │                                                       
      │ ASR结果                                               
      ▼                                                       
┌───────────┐ ASR结果┌───────────┐               ┌───────────┐
│           │───────>│           │               │           │
│ FreeSWITCH│ speak  │           │──ASR结果─────>│ AI智能体  │
│           │<───────│           │<──流式文本────│   LLM     │
└───────────┘ 语音流 │           │               └───────────┘
                     │cenxt-mrcp │     流式文本  ┌───────────┐
                     │           │──────────────>│           │
                     │           │               │  TTS服务  │
                     │           │<──────────────│           │
                     └───────────┘     语音流    └───────────┘
                                                              

```
```
spring:
  application:
    name: cenxt-mrcp

logging:
  config: classpath:log4j2.xml

cenxt:
  mrcp:
    sip:
      port: 7010
      transport: udp
    # 可选 对外ip 可以不设置
    external-ip:
    # 可选，可以不设置 自动获取指定前缀的ip作为对外ip
    external-ip-prefix:
    # MRCP服务端口
    port: 1544
    # rtp端口范围
    rtp:
      start-port: 30000
      end-port: 32000
  ai:
    default-engine: coze
    # 扣子智能体开发平台 开通地址 https://www.coze.cn/home
    coze:
      api-url: https://api.coze.cn
      api-key: <填写你申请的内容>
      bot-id: <填写你申请的内容>
  tts:
    default-engine: doubao
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v1/tts/ws_binary
      # 可选 语音名称 BV001_streaming BV104_streaming
      voice-name: BV104_streaming
      # 可选 语速
      speed-rate: 1.0
    cenxt-llm-tts:
      llm-tts-ai-engine: coze
      llm-tts-tts-engine: doubao
      llm-tts-sentence-separator: 。！？~
  asr:
    default-engine: doubao
    doubao:
      app-id: <填写你申请的内容>
      access-token: <填写你申请的内容>
      cluster: <填写你申请的内容>
      url: wss://openspeech.bytedance.com/api/v2/asr
      # 可选,语音识别超时
      recognition-timeout: 10000
      # 可选,没有输入超时
      no-input-timeout: 5000
      # 可选,识别结束超时
      speech-complete-timeout: 800
      # 可选,识别中超时
      speech-incomplete-timeout: 10000


```
### 5、常见问题
#### （1）多网卡适配
sip、mrcp、rtp监听的地址均为```0.0.0.0```，可以通过配置来调整sdp中的对外ip信息。配置具体ip或者前缀，二选一。
```
cenxt:
  mrcp:
    # 可选 对外ip 可以不设置
    external-ip:
    # 可选，可以不设置 自动获取指定前缀的ip作为对外ip
    external-ip-prefix: 192.168
```