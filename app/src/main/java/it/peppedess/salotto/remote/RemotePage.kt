package it.peppedess.salotto.remote

/** Pagina del telecomando: si apre nel browser di un altro telefono, niente da installare. */
internal object RemotePage {

    fun html(token: String): String = """
<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#0f1115">
<title>Telecomando Salotto</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }
  html, body { margin: 0; height: 100%; background: #0f1115; color: #e8eaed; overscroll-behavior: none;
    font-family: "Google Sans", Roboto, system-ui, sans-serif; }
  main { max-width: 440px; margin: 0 auto; padding: 18px 18px 28px; display: flex; flex-direction: column; gap: 18px; }
  h1 { font-size: 20px; font-weight: 500; margin: 2px 0 0; }
  .status { font-size: 13px; color: #9aa0a6; min-height: 18px; }
  .row { display: flex; gap: 12px; justify-content: space-between; }
  button { font: inherit; color: #e8eaed; background: #262a31; border: 0; border-radius: 999px;
    height: 54px; flex: 1; font-size: 15px; display: flex; align-items: center; justify-content: center; gap: 8px; }
  button:active { background: #3c4048; transform: scale(.97); }
  .tabs { display: flex; background: #1f2228; border-radius: 999px; padding: 4px; gap: 4px; }
  .tabs button { height: 40px; background: transparent; font-size: 14px; }
  .tabs button.on { background: #8ab4f8; color: #0b1d3a; font-weight: 500; }
  .panel { display: none; flex-direction: column; gap: 14px; }
  .panel.on { display: flex; }
  .pad { position: relative; width: 280px; height: 280px; margin: 4px auto; border-radius: 50%; background: #1f2228; }
  .pad button { position: absolute; width: 84px; height: 84px; background: transparent; border-radius: 50%; font-size: 26px; }
  .pad button:active { background: #3c4048; }
  .pad .up { top: 6px; left: 98px; } .pad .down { bottom: 6px; left: 98px; }
  .pad .left { left: 6px; top: 98px; } .pad .right { right: 6px; top: 98px; }
  .pad .ok { width: 112px; height: 112px; left: 84px; top: 84px; background: #3c4048; font-size: 17px; font-weight: 500; }
  .pad .ok:active { background: #5f6368; }
  .touch { height: 300px; border-radius: 24px; background: linear-gradient(160deg, #22262d, #1a1d22);
    touch-action: none; display: flex; align-items: flex-end; justify-content: center; padding: 14px;
    font-size: 12px; color: #80868b; text-align: center; line-height: 1.5; }
  .touch.active { background: linear-gradient(160deg, #2a2f37, #1f2329); }
  .hint { font-size: 13px; color: #9aa0a6; line-height: 1.45; }
  .field { display: flex; gap: 10px; }
  input { flex: 1; min-width: 0; height: 52px; border-radius: 999px; border: 0; padding: 0 20px; font: inherit; font-size: 16px;
    background: #262a31; color: #e8eaed; outline: none; user-select: text; }
  .primary { background: #8ab4f8; color: #0b1d3a; font-weight: 500; }
  .field button { flex: 0 0 auto; padding: 0 20px; }
  svg { width: 22px; height: 22px; fill: currentColor; }
</style>
</head>
<body>
<main>
  <div>
    <h1>Telecomando Salotto</h1>
    <div class="status" id="status">Collegato</div>
  </div>

  <div class="row">
    <button data-k="back"><svg viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z"/></svg>Indietro</button>
    <button data-k="home"><svg viewBox="0 0 24 24"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>Home</button>
  </div>

  <div class="tabs">
    <button data-tab="keys" class="on">Tasti</button>
    <button data-tab="touch">Touchpad</button>
    <button data-tab="kb">Tastiera</button>
  </div>

  <section class="panel on" id="p-keys">
    <div class="pad">
      <button class="up" data-k="up">&#9650;</button>
      <button class="left" data-k="left">&#9664;</button>
      <button class="ok" data-k="ok">OK</button>
      <button class="right" data-k="right">&#9654;</button>
      <button class="down" data-k="down">&#9660;</button>
    </div>
  </section>

  <section class="panel" id="p-touch">
    <div class="touch" id="tp">Muovi un dito per spostare il puntatore · tocca per cliccare · tieni premuto per la pressione lunga · due dita per scorrere · tocco con due dita = Indietro</div>
    <div class="hint" id="tphint"></div>
  </section>

  <section class="panel" id="p-kb">
    <div class="hint">Quello che scrivi appare nel campo di testo selezionato dell'app aperta (in Salotto, nella ricerca).</div>
    <div class="field">
      <input id="kb" type="text" placeholder="Scrivi qui" autocomplete="off" autocapitalize="off" enterkeyhint="go">
      <button class="primary" id="enter">Invio</button>
    </div>
    <div class="row">
      <button id="clear">Svuota</button>
    </div>
  </section>

  <div class="row">
    <button data-k="voldown"><svg viewBox="0 0 24 24"><path d="M18.5 12A4.5 4.5 0 0 0 16 7.97v8.05A4.47 4.47 0 0 0 18.5 12zM5 9v6h4l5 5V4L9 9z"/></svg>&minus;</button>
    <button data-k="mute"><svg viewBox="0 0 24 24"><path d="M16.5 12A4.5 4.5 0 0 0 14 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zM19 12c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.8 8.8 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.99 8.99 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9zM12 4 9.91 6.09 12 8.18z"/></svg></button>
    <button data-k="volup"><svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>+</button>
  </div>

  <div class="row">
    <button data-k="play"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7zM6 5H4v14h2z"/></svg>Play / Pausa</button>
    <button data-k="menu"><svg viewBox="0 0 24 24"><path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/></svg>Opzioni</button>
  </div>

  <form class="field" id="search">
    <input id="q" type="text" placeholder="Cerca su Salotto" autocomplete="off" enterkeyhint="search">
    <button type="submit" class="primary">Cerca</button>
  </form>
</main>
<script>
  const T = "$token";
  const status = document.getElementById("status");
  function ok() { status.textContent = "Collegato"; }
  function fail() { status.textContent = "Salotto non risponde: controlla di essere sulla stessa rete Wi-Fi."; }
  function post(path, body) {
    return fetch(path + (path.includes("?") ? "&" : "?") + "t=" + T, {
      method: "POST",
      headers: body === undefined ? {} : { "Content-Type": "text/plain;charset=utf-8" },
      body: body
    });
  }
  function send(k) {
    if (navigator.vibrate) navigator.vibrate(12);
    return post("/k?k=" + k).then(r => { if (r.status === 409) return false; r.ok ? ok() : fail(); return r.ok; }).catch(() => { fail(); return false; });
  }

  // Schede
  document.querySelectorAll("[data-tab]").forEach(b => b.addEventListener("click", () => {
    document.querySelectorAll("[data-tab]").forEach(x => x.classList.toggle("on", x === b));
    document.querySelectorAll(".panel").forEach(p => p.classList.toggle("on", p.id === "p-" + b.dataset.tab));
    if (b.dataset.tab === "kb") setTimeout(() => document.getElementById("kb").focus(), 50);
  }));

  // Tasti con ripetizione tenendo premuto
  let repeat = null;
  document.querySelectorAll("button[data-k]").forEach(b => {
    const k = b.dataset.k;
    const repeatable = ["up", "down", "left", "right", "volup", "voldown"].includes(k);
    b.addEventListener("pointerdown", e => {
      e.preventDefault();
      send(k);
      if (repeatable) { clearInterval(repeat); repeat = setTimeout(() => { repeat = setInterval(() => send(k), 140); }, 450); }
    });
    ["pointerup", "pointerleave", "pointercancel"].forEach(ev => b.addEventListener(ev, () => { clearTimeout(repeat); clearInterval(repeat); }));
  });

  // Touchpad
  const tp = document.getElementById("tp");
  const tphint = document.getElementById("tphint");
  const pts = new Map();
  let acc = { x: 0, y: 0 }, sacc = { x: 0, y: 0 };
  let moved = 0, downAt = 0, maxPts = 0, start = null, last = null;
  let pointer = true, busy = false, sbusy = false;
  function noPointer() {
    if (!pointer) return;
    pointer = false;
    tphint.textContent = "Per il puntatore attiva il Servizio TV in Salotto (Impostazioni → Esperienza TV). Intanto: scorri per muoverti, tocca per OK.";
  }
  tp.addEventListener("pointerdown", e => {
    e.preventDefault();
    tp.setPointerCapture(e.pointerId);
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pts.size === 1) { downAt = Date.now(); moved = 0; maxPts = 1; start = { x: e.clientX, y: e.clientY }; }
    maxPts = Math.max(maxPts, pts.size);
    tp.classList.add("active");
  });
  tp.addEventListener("pointermove", e => {
    const p = pts.get(e.pointerId);
    if (!p) return;
    const dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    moved += Math.abs(dx) + Math.abs(dy);
    last = { x: e.clientX, y: e.clientY };
    if (pts.size >= 2) { sacc.x += dx / pts.size; sacc.y += dy / pts.size; }
    else if (maxPts === 1) { acc.x += dx; acc.y += dy; }
  });
  function up(e) {
    if (!pts.has(e.pointerId)) return;
    pts.delete(e.pointerId);
    if (pts.size > 0) return;
    tp.classList.remove("active");
    const dt = Date.now() - downAt;
    if (moved < 12 && dt < 300) {
      if (maxPts >= 2) send("back");
      else if (pointer) send("tap").then(r => { if (r === false) { noPointer(); send("ok"); } });
      else send("ok");
    } else if (moved < 12 && dt >= 550 && maxPts === 1) {
      if (pointer) send("longpress"); else send("menu");
    } else if (!pointer && maxPts === 1 && last && start) {
      const dx = last.x - start.x, dy = last.y - start.y;
      if (Math.max(Math.abs(dx), Math.abs(dy)) > 30) {
        send(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up"));
      }
    }
    acc = { x: 0, y: 0 };
  }
  ["pointerup", "pointercancel"].forEach(ev => tp.addEventListener(ev, up));
  setInterval(() => {
    if (pointer && !busy && (Math.abs(acc.x) >= 1 || Math.abs(acc.y) >= 1)) {
      const dx = Math.round(acc.x), dy = Math.round(acc.y);
      acc = { x: 0, y: 0 };
      busy = true;
      post("/m?dx=" + dx + "&dy=" + dy).then(r => { if (r.status === 409) noPointer(); else ok(); }).catch(fail).finally(() => busy = false);
    }
  }, 30);
  setInterval(() => {
    if (pointer && !sbusy && (Math.abs(sacc.x) >= 4 || Math.abs(sacc.y) >= 4)) {
      const dx = Math.round(sacc.x), dy = Math.round(sacc.y);
      sacc = { x: 0, y: 0 };
      sbusy = true;
      post("/s?dx=" + dx + "&dy=" + dy).then(r => { if (r.status === 409) noPointer(); }).catch(fail).finally(() => sbusy = false);
    }
  }, 220);

  // Tastiera: il campo dell'app segue quello che scrivi
  const kb = document.getElementById("kb");
  let kbTimer = null;
  function pushText() {
    post("/type", kb.value).then(r => {
      if (r.status === 409) status.textContent = "Seleziona un campo di testo nell'app (o attiva il Servizio TV in Salotto).";
      else if (r.ok) ok(); else fail();
    }).catch(fail);
  }
  kb.addEventListener("input", () => { clearTimeout(kbTimer); kbTimer = setTimeout(pushText, 160); });
  kb.addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); clearTimeout(kbTimer); pushText(); setTimeout(() => send("enter"), 150); } });
  document.getElementById("enter").addEventListener("click", () => { clearTimeout(kbTimer); pushText(); setTimeout(() => send("enter"), 150); });
  document.getElementById("clear").addEventListener("click", () => { kb.value = ""; pushText(); kb.focus(); });

  // Ricerca in Salotto
  document.getElementById("search").addEventListener("submit", e => {
    e.preventDefault();
    const q = document.getElementById("q").value.trim();
    if (!q) return;
    post("/text", q).then(r => { if (r.ok) { ok(); document.getElementById("q").value = ""; } else fail(); }).catch(fail);
  });
</script>
</body>
</html>
""".trimIndent()

    val denied = """
<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Salotto</title><style>body{background:#0f1115;color:#e8eaed;font-family:system-ui,sans-serif;padding:32px;line-height:1.5}</style></head>
<body><h2>Codice non valido</h2><p>Apri il telecomando inquadrando il QR code mostrato da Salotto (Impostazioni rapide → Telecomando dal telefono).</p></body></html>
""".trimIndent()
}
