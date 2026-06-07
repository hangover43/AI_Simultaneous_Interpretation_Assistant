# 本地模型接入说明

## 1. 推荐方案

第一版本地模型建议使用 Ollama 运行 Qwen 系列模型，优先接入文本翻译和上下文修正。

你本机已经有可用模型，当前推荐优先使用：

```text
translategemma:12b
```

选择理由：

- 已经安装在本机，无需额外下载。
- 翻译测试输出干净，适合先接入“文本翻译 + 修正”链路。
- 相比 reasoning 模型，更适合作为字幕翻译模型。

可选模型：

```text
qwen3:8b   # 如果后续想下载更通用的多语言模型
qwen3:4b   # 机器配置较弱时使用
qwen3:14b  # 显存/内存更充足时使用
```

## 2. 需要安装

先安装 Ollama。

如果后续需要下载推荐通用模型，可执行：

```powershell
ollama pull qwen3:8b
```

确认 Ollama 服务可用：

```powershell
Invoke-RestMethod -Uri http://127.0.0.1:11434/api/tags
```

## 3. 后端启用 Ollama Provider

默认配置仍然是 mock：

```yaml
ai:
  provider: mock
```

如需启用本地模型，启动后端时设置：

```powershell
$env:AI_PROVIDER="ollama"
$env:AI_OLLAMA_BASE_URL="http://127.0.0.1:11434"
$env:AI_OLLAMA_MODEL="translategemma:12b"
cd backend
& 'J:\Environment\apache-maven-3.9.16\bin\mvn.cmd' spring-boot:run
```

如果本机 `mvn` 已在 PATH 中，也可以使用：

```powershell
mvn spring-boot:run
```

## 4. 当前接入范围

当前 Ollama provider 已接入：

- 文本翻译
- 上下文修正
- 术语表约束

暂未接入：

- 真实 ASR
- 音频直接转文本

当前音频链路仍然通过 mock 源文本模拟 ASR 输出，然后调用本地模型进行翻译和修正。

## 5. 后续计划

下一步可以继续接入本地 ASR，例如：

- Whisper.cpp
- faster-whisper
- sherpa-onnx

本项目当前优先级是先打通文本翻译和修正，因此 ASR 暂后。
