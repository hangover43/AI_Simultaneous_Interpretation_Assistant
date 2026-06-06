# API 文档

## 1. 说明

本文档定义浏览器插件与 Java Spring Boot 本地后端之间的接口协议。第一版以后端本地服务为主，插件通过 WebSocket 发送音频流、接收实时译文和修正结果；少量控制类能力通过 REST API 提供。

默认服务地址：

```text
http://127.0.0.1:8080
ws://127.0.0.1:8080/ws/interpretation
```

## 2. 通信模型

```text
浏览器插件
  -> REST: 创建会话、停止会话、topic 术语表预热
  -> WebSocket: 音频流、实时事件、字幕结果、修正结果
Java 后端
  -> WebSocket: ASR 结果、实时翻译、历史修正、错误事件
```

## 3. REST API

### 3.1 健康检查

```http
GET /api/health
```

响应：

```json
{
  "status": "ok",
  "version": "0.1.0"
}
```

### 3.2 创建同传会话

```http
POST /api/sessions
```

请求：

```json
{
  "topic": "AI 技术分享",
  "sourceLanguage": "auto",
  "targetLanguage": "zh-CN",
  "revisionWindowSize": 8
}
```

字段说明：

- `topic`：用户选择或输入的主题。
- `sourceLanguage`：源语言，第一版默认 `auto`。
- `targetLanguage`：目标语言，第一版固定为 `zh-CN`。
- `revisionWindowSize`：上下文修正窗口，建议 5-10。

响应：

```json
{
  "sessionId": "sess_20260606_000001",
  "topic": "AI 技术分享",
  "targetLanguage": "zh-CN",
  "glossary": [
    {
      "source": "inference latency",
      "target": "推理延迟"
    },
    {
      "source": "model distillation",
      "target": "模型蒸馏"
    }
  ]
}
```

### 3.3 获取会话信息

```http
GET /api/sessions/{sessionId}
```

响应：

```json
{
  "sessionId": "sess_20260606_000001",
  "topic": "AI 技术分享",
  "sourceLanguage": "auto",
  "targetLanguage": "zh-CN",
  "status": "running"
}
```

### 3.4 停止会话

```http
POST /api/sessions/{sessionId}/stop
```

响应：

```json
{
  "sessionId": "sess_20260606_000001",
  "status": "stopped"
}
```

### 3.5 清空会话历史

```http
DELETE /api/sessions/{sessionId}/segments
```

响应：

```json
{
  "sessionId": "sess_20260606_000001",
  "cleared": true
}
```

## 4. WebSocket

连接地址：

```text
ws://127.0.0.1:8080/ws/interpretation?sessionId=sess_20260606_000001
```

### 4.1 客户端消息

#### 4.1.1 开始音频流

```json
{
  "type": "audio_start",
  "sessionId": "sess_20260606_000001",
  "audio": {
    "format": "webm-opus",
    "sampleRate": 48000,
    "channels": 1
  }
}
```

服务端收到后会初始化音频流状态，并返回 `audio_ready`。

#### 4.1.2 发送音频片段

音频片段建议使用二进制 WebSocket frame 发送。若第一版实现更简单，也可使用 base64 文本消息。

文本消息格式：

```json
{
  "type": "audio_chunk",
  "sessionId": "sess_20260606_000001",
  "sequence": 42,
  "timestampMs": 12800,
  "payloadBase64": "..."
}
```

字段说明：

- `sequence`：递增序号，用于后端排序和排错。
- `timestampMs`：插件侧采集时间戳。
- `payloadBase64`：音频数据，第一版可用 base64，后续建议切换为二进制。

#### 4.1.3 结束音频流

```json
{
  "type": "audio_end",
  "sessionId": "sess_20260606_000001"
}
```

#### 4.1.4 更新 topic

```json
{
  "type": "topic_update",
  "sessionId": "sess_20260606_000001",
  "topic": "医学会议"
}
```

后端收到后应重新生成或补充术语表。

### 4.2 服务端消息

#### 4.2.1 音频流就绪

```json
{
  "type": "audio_ready",
  "sessionId": "sess_20260606_000001"
}
```

插件行为：

- 可继续发送 `audio_chunk`。
- 第一版 UI 可以不展示该事件。

#### 4.2.2 ASR 临时结果

```json
{
  "type": "asr_partial",
  "sessionId": "sess_20260606_000001",
  "segmentId": "seg_001",
  "sourceLanguage": "en",
  "sourceText": "The model reduces inference..."
}
```

用途：

- 可用于调试。
- 第一版 UI 可以不展示。

#### 4.2.3 新增历史记录

```json
{
  "type": "segment_final",
  "sessionId": "sess_20260606_000001",
  "segment": {
    "id": "seg_001",
    "sourceText": "The model reduces inference latency.",
    "translation": "该模型降低了推理延迟。",
    "revised": false
  }
}
```

插件行为：

- 在右侧历史记录追加该条记录。
- 在视频弹幕层展示 `translation`。

#### 4.2.4 实时弹幕翻译

```json
{
  "type": "live_translation",
  "sessionId": "sess_20260606_000001",
  "segmentId": "seg_001",
  "translation": "该模型降低了推理延迟。"
}
```

插件行为：

- 更新视频区域当前弹幕。
- 不要求写入历史记录，历史记录以 `segment_final` 为准。

#### 4.2.5 修正历史记录

```json
{
  "type": "segment_revision",
  "sessionId": "sess_20260606_000001",
  "segment": {
    "id": "seg_001",
    "sourceText": "The model reduces inference latency.",
    "translation": "该模型降低了推理延迟。",
    "revised": true
  }
}
```

插件行为：

- 找到右侧历史记录中的同 ID 记录。
- 更新原文和译文。
- 将该记录设置为高亮。
- 高亮应保持，不自动消失。
- 不展示修正原因和状态文字。

#### 4.2.6 术语表更新

```json
{
  "type": "glossary_update",
  "sessionId": "sess_20260606_000001",
  "glossary": [
    {
      "source": "retrieval augmented generation",
      "target": "检索增强生成"
    }
  ]
}
```

插件行为：

- 第一版可以不展示。
- 可用于后续调试或扩展术语表面板。

#### 4.2.7 错误事件

```json
{
  "type": "error",
  "sessionId": "sess_20260606_000001",
  "code": "ASR_FAILED",
  "message": "Audio recognition failed."
}
```

## 5. 数据结构

### 5.1 Segment

```json
{
  "id": "seg_001",
  "sourceText": "The model reduces inference latency.",
  "translation": "该模型降低了推理延迟。",
  "revised": false
}
```

### 5.2 GlossaryTerm

```json
{
  "source": "model distillation",
  "target": "模型蒸馏"
}
```

### 5.3 Session

```json
{
  "sessionId": "sess_20260606_000001",
  "topic": "AI 技术分享",
  "sourceLanguage": "auto",
  "targetLanguage": "zh-CN",
  "status": "running"
}
```

## 6. 错误码

| Code | 含义 |
| --- | --- |
| `SESSION_NOT_FOUND` | 会话不存在 |
| `AUDIO_FORMAT_UNSUPPORTED` | 音频格式不支持 |
| `AUDIO_STREAM_CLOSED` | 音频流已关闭 |
| `ASR_FAILED` | 语音识别失败 |
| `TRANSLATION_FAILED` | 翻译失败 |
| `REFINER_FAILED` | 上下文修正失败 |
| `MODEL_PROVIDER_UNAVAILABLE` | AI 服务不可用 |
| `INTERNAL_ERROR` | 后端内部错误 |

## 7. 第一版实现建议

- WebSocket 文本消息先满足开发和演示，后续再优化为二进制音频帧。
- 当前 mock 后端会根据 `audio_chunk` 数量模拟生成字幕和修正事件。
- `segment_final` 是历史记录的唯一追加入口。
- `segment_revision` 是历史记录的唯一修正入口。
- 弹幕层只消费 `live_translation` 和 `segment_final`。
- 右侧历史记录只关心 `sourceText`、`translation`、`revised`。
