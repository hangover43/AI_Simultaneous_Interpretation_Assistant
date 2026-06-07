# 本地模型方案

当前版本优先完成“文本翻译 + 前文修正 + 真实网页音频转写”的可用闭环，全部在本机运行。

## 默认配置

- 翻译模型：`qwen2.5:3b`
- 模型运行时：Ollama
- ASR：whisper.cpp
- ASR 模型：`ggml-base.bin`
- 音频转换：ffmpeg

`qwen2.5:3b` 被选为当前默认模型，是因为它在本机测试中响应速度明显优于 `translategemma:12b`，更适合实时字幕场景。

## 启动后端

```powershell
cmd.exe /c scripts\launch-backend-ollama.cmd
```

该脚本会设置：

```text
AI_PROVIDER=ollama
AI_OLLAMA_BASE_URL=http://127.0.0.1:11434
AI_OLLAMA_MODEL=qwen2.5:3b
```

检查后端当前模型：

```powershell
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/ai/provider
```

## 处理链路

```text
浏览器 tabCapture
  -> MediaRecorder 生成 webm/opus 分片
  -> WebSocket 发送到后端
  -> ffmpeg 转为 16k 单声道 wav
  -> whisper.cpp 转写原文
  -> Ollama 翻译为中文
  -> 网页字幕 + 右侧历史面板
```

## 模型文件

`tools/whisper.cpp/` 存放本地下载的 whisper.cpp 可执行文件和模型。该目录被 `.gitignore` 忽略，不提交到仓库。

当前期望路径：

```text
tools/whisper.cpp/Release/whisper-cli.exe
tools/whisper.cpp/models/ggml-base.bin
```

如果换电脑运行，需要重新准备 whisper.cpp 和 `ggml-base.bin`，或修改 `backend/src/main/resources/application.yml` 里的路径。

## 可选优化

- 降低分片时长：把 `extension/src/offscreen.js` 的 5000ms 改为 3000ms，可降低字幕延迟，但 ASR 调用频率会增加。
- 更快 ASR：可迁移到 faster-whisper 或 sherpa-onnx，降低实时转写延迟。
- 更强翻译：可尝试 `qwen3:4b` / `qwen3:8b`，但要重新评估响应速度。
- 更强修正：保留最近 N 条 segment，把术语和上下文传给修正 prompt，只高亮历史记录，不回放已经过去的弹幕。
