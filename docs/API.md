# API 文档

后端默认地址：

```text
HTTP: http://127.0.0.1:8080
WebSocket: ws://127.0.0.1:8080/ws/interpretation
```

## REST API

### 健康检查

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

### 创建会话

```http
POST /api/sessions
```

请求：

```json
{
  "topic": "AI 技术演讲",
  "sourceLanguage": "auto",
  "targetLanguage": "zh-CN",
  "revisionWindowSize": 8
}
```

响应：

```json
{
  "sessionId": "sess_20260607_000001",
  "topic": "AI 技术演讲",
  "sourceLanguage": "auto",
  "targetLanguage": "zh-CN",
  "revisionWindowSize": 8,
  "createdAt": "2026-06-07T20:00:00"
}
```

### 查询会话

```http
GET /api/sessions/{sessionId}
```

### 停止会话

```http
POST /api/sessions/{sessionId}/stop
```

### 查询 AI Provider

```http
GET /api/ai/provider
```

响应：

```json
{
  "provider": "ollama",
  "model": "qwen2.5:3b",
  "baseUrl": "http://127.0.0.1:11434"
}
```

### 翻译测试

```http
POST /api/ai/translate-test
```

请求：

```json
{
  "topic": "AI 技术演讲",
  "text": "The model reduces inference latency."
}
```

### 修正测试

```http
POST /api/ai/revise-test
```

请求：

```json
{
  "topic": "AI 技术演讲",
  "sourceText": "The model reduces inference latency.",
  "translation": "模型减少了推理延迟。"
}
```

## WebSocket

连接：

```text
ws://127.0.0.1:8080/ws/interpretation?sessionId={sessionId}
```

### 客户端消息

开始音频流：

```json
{
  "type": "audio_start",
  "sessionId": "sess_20260607_000001",
  "audio": {
    "format": "webm-opus",
    "sampleRate": 48000,
    "channels": 1
  }
}
```

发送音频分片：

```json
{
  "type": "audio_chunk",
  "sessionId": "sess_20260607_000001",
  "sequence": 0,
  "timestampMs": 1780837200000,
  "payloadBase64": "..."
}
```

停止音频流：

```json
{
  "type": "audio_end",
  "sessionId": "sess_20260607_000001"
}
```

启动 mock 演示：

```json
{
  "type": "mock_start",
  "sessionId": "sess_20260607_000001"
}
```

### 服务端消息

连接成功：

```json
{
  "type": "connected",
  "sessionId": "sess_20260607_000001"
}
```

音频流就绪：

```json
{
  "type": "audio_ready",
  "sessionId": "sess_20260607_000001"
}
```

音频分片确认：

```json
{
  "type": "audio_ack",
  "sessionId": "sess_20260607_000001",
  "sequence": 0
}
```

最终片段：

```json
{
  "type": "segment_final",
  "sessionId": "sess_20260607_000001",
  "segment": {
    "id": "seg_asr_000",
    "sourceText": "The model reduces inference latency.",
    "translation": "模型减少了推理延迟。",
    "revised": false
  }
}
```

历史修正：

```json
{
  "type": "segment_revision",
  "sessionId": "sess_20260607_000001",
  "segment": {
    "id": "seg_asr_000",
    "sourceText": "The model reduces inference latency.",
    "translation": "该模型降低了推理延迟。",
    "revised": true
  }
}
```

错误：

```json
{
  "type": "error",
  "sessionId": "sess_20260607_000001",
  "code": "SESSION_NOT_FOUND",
  "message": "Session not found."
}
```
