(() => {
  const OVERLAY_ID = "ai-interpretation-overlay";
  const VISIBLE_CLASS = "ai-interpretation-visible";

  function ensureOverlay() {
    let overlay = document.getElementById(OVERLAY_ID);
    if (overlay) {
      return overlay;
    }

    overlay = document.createElement("div");
    overlay.id = OVERLAY_ID;

    const subtitle = document.createElement("div");
    subtitle.className = "ai-interpretation-subtitle";
    overlay.appendChild(subtitle);
    document.documentElement.appendChild(overlay);
    return overlay;
  }

  function showSubtitle(text) {
    const overlay = ensureOverlay();
    const subtitle = overlay.querySelector(".ai-interpretation-subtitle");
    subtitle.textContent = text;
    overlay.classList.add(VISIBLE_CLASS);
  }

  function clearSubtitle() {
    const overlay = document.getElementById(OVERLAY_ID);
    if (!overlay) {
      return;
    }
    overlay.classList.remove(VISIBLE_CLASS);
    const subtitle = overlay.querySelector(".ai-interpretation-subtitle");
    subtitle.textContent = "";
  }

  chrome.runtime.onMessage.addListener((message) => {
    if (message?.type === "subtitle_show") {
      showSubtitle(message.translation || "");
    }
    if (message?.type === "subtitle_clear") {
      clearSubtitle();
    }
  });
})();
