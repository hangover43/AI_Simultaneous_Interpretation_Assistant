const API_BASE_URL = "http://127.0.0.1:8080";
const WS_BASE_URL = "ws://127.0.0.1:8080/ws/interpretation";

const state = {
  sessionId: null,
  socket: null,
  segments: new Map(),
  activeTabId: null,
  pendingStreamId: null,
  capturing: false,
  captureError: null,
  subtitleError: null
};

const topicInput = document.getElementById("topicInput");
const startButton = document.getElementById("startButton");
const stopButton = document.getElementById("stopButton");
const clearButton = document.getElementById("clearButton");
const connectionStatus = document.getElementById("connectionStatus");
const aiProviderStatus = document.getElementById("aiProviderStatus");
const historyList = document.getElementById("historyList");

renderHistory();
refreshProviderStatus();

startButton.addEventListener("click", startInterpretation);
stopButton.addEventListener("click", stopInterpretation);
clearButton.addEventListener("click", clearHistory);

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === "audio_chunk") {
    sendAudioChunk(message);
  }
});

async function startInterpretation() {
  setStatus("正在准备当前标签页音频...");
  startButton.disabled = true;
  await refreshProviderStatus();

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    state.activeTabId = tab?.id ?? null;
    state.pendingStreamId = null;
    state.captureError = null;
    state.subtitleError = null;

    if (!state.activeTabId) {
      throw new Error("没有可用的活动标签页");
    }

    try {
      state.pendingStreamId = await getTabAudioStreamId(state.activeTabId);
    } catch (error) {
      state.captureError = error.message;
    }

    await ensureSubtitleRuntime().catch((error) => {
      state.subtitleError = error.message;
    });

    const session = await createSession(topicInput.value.trim() || "通用视频");
    state.sessionId = session.sessionId;
    connectWebSocket(session.sessionId);
  } catch (error) {
    setStatus(`启动失败：${error.message}`);
    startButton.disabled = false;
  }
}

async function stopInterpretation() {
  await stopTabAudioCapture();

  if (state.socket?.readyState === WebSocket.OPEN) {
    state.socket.send(JSON.stringify({
      type: "audio_end",
      sessionId: state.sessionId
    }));
    state.socket.close();
  }

  if (state.sessionId) {
    await fetch(`${API_BASE_URL}/api/sessions/${state.sessionId}/stop`, { method: "POST" }).catch(() => {});
  }

  await sendSubtitleMessage({ type: "subtitle_clear" });
  state.socket = null;
  state.sessionId = null;
  state.pendingStreamId = null;
  state.capturing = false;
  setStatus("已停止");
  startButton.disabled = false;
  stopButton.disabled = true;
}

function clearHistory() {
  state.segments.clear();
  renderHistory();
}

async function createSession(topic) {
  const response = await fetch(`${API_BASE_URL}/api/sessions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      topic,
      sourceLanguage: "auto",
      targetLanguage: "zh-CN",
      revisionWindowSize: 8
    })
  });

  if (!response.ok) {
    throw new Error(`后端返回 ${response.status}`);
  }
  return response.json();
}

async function refreshProviderStatus() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/ai/provider`);
    if (!response.ok) {
      throw new Error(`后端返回 ${response.status}`);
    }
    const provider = await response.json();
    aiProviderStatus.textContent = `AI: ${provider.provider} / ${provider.model}`;
  } catch (error) {
    aiProviderStatus.textContent = "AI: 后端未连接";
  }
}

function connectWebSocket(sessionId) {
  const socket = new WebSocket(`${WS_BASE_URL}?sessionId=${encodeURIComponent(sessionId)}`);
  state.socket = socket;

  socket.addEventListener("open", async () => {
    setStatus("已连接后端，正在启动音频捕获...");
    stopButton.disabled = false;
    socket.send(JSON.stringify({
      type: "audio_start",
      sessionId,
      audio: {
        format: "webm-opus",
        sampleRate: 48000,
        channels: 1
      }
    }));

    const captureStarted = await startTabAudioCapture(sessionId);
    if (captureStarted) {
      state.capturing = true;
      const subtitleNote = state.subtitleError ? `；字幕注入失败：${state.subtitleError}` : "";
      setStatus(`正在实时翻译当前标签页音频${subtitleNote}`);
      return;
    }

    setStatus(`标签页音频捕获失败：${state.captureError || "未知错误"}，已切换 mock 演示`);
    socket.send(JSON.stringify({
      type: "mock_start",
      sessionId
    }));
  });

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    handleServerMessage(message);
  });

  socket.addEventListener("close", () => {
    setStatus("连接已关闭");
    startButton.disabled = false;
    stopButton.disabled = true;
  });

  socket.addEventListener("error", () => {
    setStatus("连接错误，请确认后端已启动");
    startButton.disabled = false;
    stopButton.disabled = true;
  });
}

function handleServerMessage(message) {
  if (message.type === "connected" || message.type === "audio_ready" || message.type === "audio_ack") {
    return;
  }

  if (message.type === "live_translation") {
    sendSubtitleMessage({
      type: "subtitle_show",
      translation: message.translation
    });
    return;
  }

  if (message.type === "segment_final") {
    upsertSegment(message.segment);
    sendSubtitleMessage({
      type: "subtitle_show",
      translation: message.segment.translation
    });
    return;
  }

  if (message.type === "segment_revision") {
    upsertSegment({ ...message.segment, revised: true });
    return;
  }

  if (message.type === "error") {
    setStatus(`错误：${message.message}`);
  }
}

function sendAudioChunk(message) {
  if (!state.socket || state.socket.readyState !== WebSocket.OPEN || message.sessionId !== state.sessionId) {
    return;
  }

  state.socket.send(JSON.stringify({
    type: "audio_chunk",
    sessionId: message.sessionId,
    sequence: message.sequence,
    timestampMs: message.timestampMs,
    payloadBase64: message.payloadBase64
  }));
}

async function startTabAudioCapture(sessionId) {
  if (!state.pendingStreamId) {
    return false;
  }

  const response = await chrome.runtime.sendMessage({
    type: "start_tab_capture",
    streamId: state.pendingStreamId,
    sessionId
  }).catch((error) => ({ ok: false, message: error.message }));

  state.captureError = response?.message || state.captureError;
  return Boolean(response?.ok);
}

function getTabAudioStreamId(tabId) {
  return new Promise((resolve, reject) => {
    chrome.tabCapture.getMediaStreamId({ targetTabId: tabId }, (streamId) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      if (!streamId) {
        reject(new Error("未能创建当前标签页音频流"));
        return;
      }
      resolve(streamId);
    });
  });
}

async function ensureSubtitleRuntime() {
  if (!state.activeTabId) {
    throw new Error("没有可用的活动标签页");
  }

  await chrome.scripting.insertCSS({
    target: { tabId: state.activeTabId },
    files: ["src/content.css"]
  }).catch(() => {});

  await chrome.scripting.executeScript({
    target: { tabId: state.activeTabId },
    files: ["src/content.js"]
  });
}

async function stopTabAudioCapture() {
  await chrome.runtime.sendMessage({ type: "stop_tab_capture" }).catch(() => {});
}

function upsertSegment(segment) {
  state.segments.set(segment.id, {
    id: segment.id,
    sourceText: segment.sourceText,
    translation: segment.translation,
    revised: Boolean(segment.revised)
  });
  renderHistory();
}

function renderHistory() {
  historyList.replaceChildren();

  const segments = Array.from(state.segments.values());
  if (segments.length === 0) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "暂无历史翻译";
    historyList.appendChild(empty);
    return;
  }

  for (const segment of segments) {
    const item = document.createElement("article");
    item.className = segment.revised ? "history-item revised" : "history-item";

    item.appendChild(label("原文"));
    item.appendChild(text(segment.sourceText));
    item.appendChild(label("译文"));
    item.appendChild(text(segment.translation));
    historyList.appendChild(item);
  }
}

function label(value) {
  const element = document.createElement("p");
  element.className = "history-label";
  element.textContent = value;
  return element;
}

function text(value) {
  const element = document.createElement("p");
  element.className = "history-text";
  element.textContent = value;
  return element;
}

async function sendSubtitleMessage(message) {
  if (!state.activeTabId) {
    return;
  }
  await chrome.tabs.sendMessage(state.activeTabId, message).catch(() => {});
}

function setStatus(value) {
  connectionStatus.textContent = value;
}
