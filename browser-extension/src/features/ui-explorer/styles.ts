export const UI_EXPLORER_STYLES = `
  :host {
    all: initial;
    position: fixed;
    inset: 0;
    z-index: 2147483647;
    pointer-events: none;
    color: #172b4d;
    font-family: Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    font-size: 14px;
    line-height: 1.45;
  }

  *, *::before, *::after { box-sizing: border-box; }

  .tdw-selection-shield {
    position: fixed;
    inset: 0;
    display: none;
    pointer-events: auto;
    cursor: crosshair;
    background: transparent;
    touch-action: none;
  }

  .tdw-selection-shield[data-active="true"] { display: block; }

  .tdw-highlight {
    position: fixed;
    display: none;
    pointer-events: none;
    border: 2px solid #0c66e4;
    border-radius: 7px;
    background: rgba(222, 235, 255, 0.15);
    box-shadow:
      0 0 0 2px rgba(7, 71, 166, 0.9),
      0 0 0 7px rgba(12, 102, 228, 0.18),
      0 0 28px 10px rgba(12, 102, 228, 0.34),
      inset 0 0 20px rgba(222, 235, 255, 0.2);
    animation: tdw-pulse 1.45s ease-in-out infinite;
  }

  .tdw-highlight[data-visible="true"] { display: block; }

  .tdw-highlight::before,
  .tdw-highlight::after {
    content: "";
    position: absolute;
    inset: -8px;
    border: 2px solid transparent;
    border-top-color: #69a7ff;
    border-bottom-color: #69a7ff;
    border-radius: 10px;
  }

  .tdw-highlight::after {
    inset: -5px -10px;
    border-top-color: transparent;
    border-bottom-color: transparent;
    border-left-color: #0747a6;
    border-right-color: #0747a6;
  }

  .tdw-highlight-label {
    position: absolute;
    left: -2px;
    bottom: calc(100% + 10px);
    max-width: min(360px, 70vw);
    padding: 7px 10px;
    overflow: hidden;
    color: white;
    font-size: 12px;
    font-weight: 750;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
    border: 1px solid rgba(255, 255, 255, 0.28);
    border-radius: 999px;
    background: linear-gradient(120deg, #071d49, #0747a6 55%, #0c66e4);
    box-shadow: 0 8px 24px rgba(7, 71, 166, 0.32);
  }

  .tdw-modal-layer {
    position: fixed;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 24px;
    pointer-events: auto;
    background: rgba(7, 20, 45, 0.56);
    backdrop-filter: blur(7px) saturate(0.9);
    animation: tdw-fade-in 150ms ease-out;
  }

  .tdw-modal {
    width: min(720px, calc(100vw - 32px));
    max-height: min(860px, calc(100vh - 32px));
    overflow: auto;
    border: 1px solid rgba(179, 212, 255, 0.9);
    border-radius: 20px;
    background: #ffffff;
    box-shadow: 0 28px 90px rgba(9, 30, 66, 0.42), 0 0 0 1px rgba(12, 102, 228, 0.18);
    animation: tdw-modal-in 190ms cubic-bezier(0.2, 0.9, 0.2, 1);
  }

  .tdw-modal-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
    padding: 22px 24px 18px;
    color: white;
    border-radius: 19px 19px 0 0;
    background:
      radial-gradient(circle at 84% 0%, rgba(105, 167, 255, 0.75), transparent 32%),
      linear-gradient(125deg, #071d49, #0747a6 58%, #0c66e4);
  }

  .tdw-modal-eyebrow {
    display: block;
    margin-bottom: 5px;
    color: #b3d4ff;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .tdw-modal-title { margin: 0; font-size: 22px; line-height: 1.2; }

  .tdw-modal-subtitle { margin: 7px 0 0; color: #deebff; }

  .tdw-icon-button {
    display: grid;
    flex: 0 0 auto;
    width: 34px;
    height: 34px;
    place-items: center;
    padding: 0;
    color: white;
    font: inherit;
    font-size: 22px;
    border: 1px solid rgba(255, 255, 255, 0.38);
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.1);
    cursor: pointer;
  }

  .tdw-icon-button:disabled { opacity: 0.55; cursor: wait; }

  .tdw-modal-body { display: grid; gap: 18px; padding: 22px 24px 24px; }

  .tdw-demo-banner,
  .tdw-selection-summary,
  .tdw-result {
    padding: 13px 15px;
    border-radius: 12px;
  }

  .tdw-demo-banner {
    color: #0747a6;
    border: 1px solid #b3d4ff;
    background: #deebff;
    font-weight: 650;
  }

  .tdw-selection-summary {
    border: 1px solid #dfe1e6;
    background: #f7f8f9;
  }

  .tdw-selection-summary strong { display: block; margin-bottom: 4px; color: #071d49; }

  .tdw-field { display: grid; gap: 7px; }

  .tdw-field > span { font-weight: 750; color: #172b4d; }

  .tdw-field textarea,
  .tdw-field select {
    width: 100%;
    color: #172b4d;
    font: inherit;
    border: 1px solid #b6c2cf;
    border-radius: 10px;
    background: white;
    outline: none;
  }

  .tdw-field textarea { min-height: 110px; resize: vertical; padding: 12px 13px; }
  .tdw-field select { height: 42px; padding: 0 12px; }

  .tdw-field textarea:focus,
  .tdw-field select:focus {
    border-color: #0c66e4;
    box-shadow: 0 0 0 3px rgba(12, 102, 228, 0.2);
  }

  .tdw-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

  .tdw-preview {
    border: 1px solid #dfe1e6;
    border-radius: 12px;
    background: #f7f8f9;
  }

  .tdw-preview summary {
    padding: 12px 14px;
    color: #0747a6;
    font-weight: 750;
    cursor: pointer;
  }

  .tdw-preview pre {
    max-height: 230px;
    margin: 0;
    padding: 14px;
    overflow: auto;
    color: #172b4d;
    font: 11px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace;
    border-top: 1px solid #dfe1e6;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .tdw-actions { display: flex; justify-content: flex-end; gap: 10px; }

  .tdw-button {
    min-height: 42px;
    padding: 0 17px;
    font: inherit;
    font-weight: 760;
    border-radius: 10px;
    cursor: pointer;
  }

  .tdw-button:disabled { opacity: 0.65; cursor: wait; }
  .tdw-button-secondary { color: #0747a6; border: 1px solid #b3d4ff; background: white; }
  .tdw-button-primary {
    color: white;
    border: 1px solid #0c66e4;
    background: linear-gradient(120deg, #0747a6, #0c66e4);
    box-shadow: 0 8px 22px rgba(12, 102, 228, 0.24);
  }

  .tdw-result { color: #164b35; border: 1px solid #7ee2b8; background: #dcfff1; }
  .tdw-result[data-kind="error"] { color: #ae2a19; border-color: #ff9c8f; background: #ffebe6; }
  .tdw-result strong { display: block; margin-bottom: 3px; }
  .tdw-hidden { display: none !important; }

  @keyframes tdw-pulse {
    0%, 100% { filter: brightness(1); transform: scale(1); }
    50% { filter: brightness(1.14); transform: scale(1.006); }
  }

  @keyframes tdw-fade-in { from { opacity: 0; } }
  @keyframes tdw-modal-in { from { opacity: 0; transform: translateY(10px) scale(0.98); } }

  @media (max-width: 620px) {
    .tdw-modal-layer { padding: 10px; }
    .tdw-grid { grid-template-columns: 1fr; }
    .tdw-modal-header, .tdw-modal-body { padding-left: 18px; padding-right: 18px; }
  }

  @media (prefers-reduced-motion: reduce) {
    .tdw-highlight, .tdw-modal-layer, .tdw-modal { animation: none; }
  }
`;
