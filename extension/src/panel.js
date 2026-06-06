const API_BASE_URL = "http://127.0.0.1:8080";
const WS_BASE_URL = "ws://127.0.0.1:8080/ws/interpretation";

const state = {
  sessionId: null,
  socket: null,
  segments: new Map(),
  activeTabId: null
};

const topicInput = document.getElementById("topicInput");
const startButton = document.getElementById("startButton");
const stopButton = document.getElementById("stopButton");
const clearButton = document.getElementById("clearButton");
const connectionStatus = document.getElementById("connectionStatus");
const historyList = document.getElementById("historyList");

renderHistory();

startButton.addEventListener("click", startInterpretation);
stopButton.addEventListener("click", stopInterpretation);
clearButton.addEventListener("click", clearHistory);

async function startInterpretation() {
  setStatus("正在创建会话...");
  startButton.disabled = true;

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    state.activeTabId = tab?.id ?? null;

    const session = await createSession(topicInput.value.trim() || "通用会议");
    state.sessionId = session.sessionId;
    connectWebSocket(session.sessionId);
  } catch (error) {
    setStatus(`启动失败：${error.message}`);
    startButton.disabled = false;
  }
}

async function stopInterpretation() {
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

function connectWebSocket(sessionId) {
  const socket = new WebSocket(`${WS_BASE_URL}?sessionId=${encodeURIComponent(sessionId)}`);
  state.socket = socket;

  socket.addEventListener("open", () => {
    setStatus("已连接，mock 同传运行中");
    stopButton.disabled = false;
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
  if (message.type === "connected") {
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
    empty.textContent = "暂无历史记录";
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
