chrome.runtime.onInstalled.addListener(() => {
  if (chrome.sidePanel?.setPanelBehavior) {
    chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true });
  }
});

chrome.action.onClicked.addListener(async (tab) => {
  if (chrome.sidePanel?.open && tab?.windowId) {
    await chrome.sidePanel.open({ windowId: tab.windowId });
  }
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "start_tab_capture") {
    startTabCapture(message.streamId, message.sessionId)
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, message: error.message }));
    return true;
  }

  if (message?.type === "stop_tab_capture") {
    stopTabCapture()
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, message: error.message }));
    return true;
  }

  return false;
});

async function startTabCapture(streamId, sessionId) {
  if (!streamId) {
    throw new Error("Missing tab audio stream id.");
  }

  await ensureOffscreenDocument();

  const response = await chrome.runtime.sendMessage({
    type: "offscreen_start_capture",
    streamId,
    sessionId
  });
  if (!response?.ok) {
    throw new Error(response?.message || "Offscreen tab audio capture failed.");
  }
}

async function stopTabCapture() {
  await chrome.runtime.sendMessage({ type: "offscreen_stop_capture" }).catch(() => {});
}

async function ensureOffscreenDocument() {
  if (!chrome.offscreen) {
    throw new Error("Offscreen API is unavailable in this browser.");
  }

  const offscreenUrl = chrome.runtime.getURL("src/offscreen.html");
  const existingContexts = await chrome.runtime.getContexts({
    contextTypes: ["OFFSCREEN_DOCUMENT"],
    documentUrls: [offscreenUrl]
  });

  if (existingContexts.length > 0) {
    return;
  }

  await chrome.offscreen.createDocument({
    url: "src/offscreen.html",
    reasons: ["USER_MEDIA"],
    justification: "Capture the current tab audio and stream it to the local interpretation backend."
  });
}
