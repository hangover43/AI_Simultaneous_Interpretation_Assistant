let mediaStream = null;
let mediaRecorder = null;
let audioElement = null;
let sequence = 0;
let activeSessionId = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "offscreen_start_capture") {
    startCapture(message.streamId, message.sessionId)
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, message: error.message }));
    return true;
  }

  if (message?.type === "offscreen_stop_capture") {
    stopCapture();
    sendResponse({ ok: true });
    return true;
  }

  return false;
});

async function startCapture(streamId, sessionId) {
  stopCapture();

  activeSessionId = sessionId;
  sequence = 0;
  mediaStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      mandatory: {
        chromeMediaSource: "tab",
        chromeMediaSourceId: streamId
      }
    },
    video: false
  });

  keepTabAudioAudible(mediaStream);

  const options = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
    ? { mimeType: "audio/webm;codecs=opus" }
    : undefined;

  mediaRecorder = new MediaRecorder(mediaStream, options);
  mediaRecorder.ondataavailable = handleAudioChunk;
  mediaRecorder.start(1000);
}

function stopCapture() {
  if (mediaRecorder && mediaRecorder.state !== "inactive") {
    mediaRecorder.stop();
  }
  mediaRecorder = null;

  if (mediaStream) {
    for (const track of mediaStream.getTracks()) {
      track.stop();
    }
  }
  mediaStream = null;

  if (audioElement) {
    audioElement.pause();
    audioElement.srcObject = null;
  }
  audioElement = null;
  activeSessionId = null;
}

async function handleAudioChunk(event) {
  if (!event.data || event.data.size === 0 || !activeSessionId) {
    return;
  }

  const payloadBase64 = await blobToBase64(event.data);
  await chrome.runtime.sendMessage({
    type: "audio_chunk",
    sessionId: activeSessionId,
    sequence: sequence++,
    timestampMs: Date.now(),
    payloadBase64
  }).catch(() => {});
}

function keepTabAudioAudible(stream) {
  audioElement = new Audio();
  audioElement.srcObject = stream;
  audioElement.muted = false;
  audioElement.play().catch(() => {});
}

async function blobToBase64(blob) {
  const buffer = await blob.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}
