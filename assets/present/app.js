/* Presentation admin — same logic as pb-marketer-tv's admin.js (pairing, library, single
   "Default" playlist, playback control), minus the admin lock, debug telemetry and app updater. */
"use strict";

const TOKEN_KEY = "tvos.present.token";
const state = {
  token: localStorage.getItem(TOKEN_KEY) || "",
  media: [],
  filter: "all",
  playlist: null,
  draft: null,
  dirty: false,
  tab: "library",
  selected: null,
  onTv: null,
  playing: true,
};
const $ = (id) => document.getElementById(id);

/* ------------------------------------------------------------------ i18n */

/* English text is the key. The page follows the TV's in-app language (GET /api/info "lang");
   before that answer arrives it uses the last language seen, then the browser's. */
const LANG_KEY = "tvos.present.lang";
const RTL_LANGS = ["he"];
// The server prepends I18N from Android resources.
let LANG = "en";

/** Translates an English string; {name} placeholders are filled from vars. */
function T(en, vars) {
  const s = (I18N[LANG] && I18N[LANG][en]) || en;
  return vars ? s.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m)) : s;
}

/** "he-IL" → "he"; null when the page has no dictionary for it (English needs none). */
function supportedLang(code) {
  const c = String(code || "").toLowerCase().split(/[-_]/)[0];
  return c === "en" || I18N[c] ? c : null;
}

function initialLang() {
  let saved = null;
  try { saved = localStorage.getItem(LANG_KEY); } catch (_) { /* storage blocked */ }
  return supportedLang(saved) || supportedLang(navigator.language) || "en";
}

const i18nOrig = new WeakMap();

/** Applies the language to <html> and to every element marked data-i18n / data-i18n-attr / data-i18n-html. */
function applyStaticText() {
  document.documentElement.lang = LANG;
  document.documentElement.dir = RTL_LANGS.includes(LANG) ? "rtl" : "ltr";
  document.querySelectorAll("[data-i18n], [data-i18n-attr], [data-i18n-html]").forEach((el) => {
    let orig = i18nOrig.get(el);
    if (!orig) {
      orig = { text: el.textContent.trim(), attrs: {} };
      (el.dataset.i18nAttr || "").split(/\s+/).filter(Boolean).forEach((a) => { orig.attrs[a] = el.getAttribute(a) || ""; });
      i18nOrig.set(el, orig);
    }
    if (el.dataset.i18nHtml) el.innerHTML = T(el.dataset.i18nHtml, { link: `<span class="link">${esc(T("choose files"))}</span>` });
    else if ("i18n" in el.dataset) el.textContent = T(orig.text);
    Object.entries(orig.attrs).forEach(([a, v]) => el.setAttribute(a, T(v)));
  });
  if (document.querySelector("title")) document.title = T("Presentation · tvOS Launcher");
}

/** Switches language and redraws whatever is on screen. */
function setLang(lang) {
  const next = supportedLang(lang) || "en";
  try { localStorage.setItem(LANG_KEY, next); } catch (_) { /* storage blocked */ }
  if (next === LANG && document.documentElement.lang === next) return;
  LANG = next;
  applyStaticText();
  if (state.token && state.playlist) { renderMedia(); renderSlides(); refreshPlayback(); }
}

document.addEventListener("DOMContentLoaded", () => {
  LANG = initialLang();
  applyStaticText();
  bind();
  loadInfo();
  if (state.token) { showApp(); refreshAll({ preserveDirty: false }); } else { showPair(); }
  setInterval(() => { if (state.token) refreshPlayback(); }, 3000);
  window.addEventListener("beforeunload", (e) => { if (state.dirty) { e.preventDefault(); e.returnValue = ""; } });
});

/* ------------------------------------------------------------------ wiring */

function bind() {
  // Pairing: six boxes, auto-advance, paste-friendly, auto-submit.
  const boxes = [...document.querySelectorAll("#pinBoxes input")];
  boxes.forEach((box, i) => {
    box.addEventListener("input", () => {
      box.value = box.value.replace(/\D/g, "").slice(-1);
      if (box.value && boxes[i + 1]) boxes[i + 1].focus();
      const pin = boxes.map((b) => b.value).join("");
      $("pairBtn").disabled = pin.length !== 6;
      if (pin.length === 6) $("pairForm").requestSubmit();
    });
    box.addEventListener("keydown", (e) => {
      if (e.key === "Backspace" && !box.value && boxes[i - 1]) boxes[i - 1].focus();
    });
    box.addEventListener("paste", (e) => {
      const digits = (e.clipboardData.getData("text") || "").replace(/\D/g, "").slice(0, 6);
      if (!digits) return;
      e.preventDefault();
      digits.split("").forEach((d, k) => { if (boxes[k]) boxes[k].value = d; });
      boxes[Math.min(digits.length, 5)].focus();
      $("pairBtn").disabled = digits.length !== 6;
      if (digits.length === 6) $("pairForm").requestSubmit();
    });
  });
  $("pairForm").addEventListener("submit", pair);
  $("unpairBtn").addEventListener("click", () => { if (confirmLeave()) forgetPairing(); });

  document.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => setTab(b.dataset.tab)));
  document.querySelectorAll("[data-filter]").forEach((b) => b.addEventListener("click", () => setFilter(b.dataset.filter)));
  document.querySelectorAll("[data-cmd]").forEach((b) => b.addEventListener("click", () => control(b.dataset.cmd)));

  // Upload: click or drag & drop, multiple files.
  const drop = $("dropZone");
  $("fileInput").addEventListener("change", (e) => { uploadFiles([...e.target.files]); e.target.value = ""; });
  drop.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); $("fileInput").click(); } });
  ["dragenter", "dragover"].forEach((t) => drop.addEventListener(t, (e) => { e.preventDefault(); drop.classList.add("over"); }));
  ["dragleave", "drop"].forEach((t) => drop.addEventListener(t, () => drop.classList.remove("over")));
  drop.addEventListener("drop", (e) => { e.preventDefault(); uploadFiles([...e.dataTransfer.files]); });

  // Add Slide menu (and the empty state's buttons).
  $("addBtn").addEventListener("click", (e) => { e.stopPropagation(); toggleMenu(); });
  document.addEventListener("click", (e) => { if (!e.target.closest(".menu-wrap")) toggleMenu(false); });
  document.querySelectorAll("[data-add]").forEach((b) => b.addEventListener("click", () => {
    toggleMenu(false);
    if (b.dataset.add === "library") openPicker(); else openWebpageSheet();
  }));
  $("saveBtn").addEventListener("click", savePlaylist);
  $("revertBtn").addEventListener("click", () => { state.draft = clone(state.playlist); setDirty(false); renderSlides(); });
  $("sheetClose").addEventListener("click", closeSheet);
  $("sheet").addEventListener("click", (e) => { if (e.target.id === "sheet") closeSheet(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") { closeSheet(); closeConfirm(false); toggleMenu(false); } });
}

/* ------------------------------------------------------------------ pairing */

async function loadInfo() {
  try {
    const info = await api("/api/info", { auth: false });
    if (info.lang) setLang(info.lang);  // follow the TV's in-app language
    if (info.device) $("deviceName").textContent = `${info.device}${info.versionName ? " · tvOS Launcher " + info.versionName : ""}`;
  } catch (_) { /* shown when calls fail */ }
}

async function pair(e) {
  e.preventDefault();
  const boxes = [...document.querySelectorAll("#pinBoxes input")];
  const pin = boxes.map((b) => b.value).join("");
  if (pin.length !== 6) return;
  $("pairError").textContent = "";
  $("pairBtn").disabled = true;
  try {
    const res = await api("/api/pair", { auth: false, method: "POST", body: { pin } });
    state.token = res.token;
    localStorage.setItem(TOKEN_KEY, state.token);
    showApp();
    await refreshAll({ preserveDirty: false });
    toast(T("Paired with the TV"));
  } catch (err) {
    $("pairError").textContent = err.message;
    const g = $("pinBoxes");
    g.classList.remove("shake"); void g.offsetWidth; g.classList.add("shake");
    boxes.forEach((b) => (b.value = ""));
    boxes[0].focus();
  }
}

function forgetPairing() {
  state.token = "";
  localStorage.removeItem(TOKEN_KEY);
  setDirty(false);
  showPair();
}

function showPair() {
  $("pairView").classList.remove("hidden");
  $("appView").classList.add("hidden");
  setTimeout(() => document.querySelector("#pinBoxes input").focus(), 50);
}

function showApp() {
  $("pairView").classList.add("hidden");
  $("appView").classList.remove("hidden");
  setTab(state.tab);
}

/* ------------------------------------------------------------------ data */

async function refreshAll({ preserveDirty }) {
  try {
    await refreshMedia();
    await refreshPlaylist({ preserveDirty });
    await refreshPlayback();
  } catch (err) { handleError(err); }
}

async function refreshMedia() {
  const res = await api("/api/media");
  state.media = res.media || [];
  pruneDraftMissingMedia();
  renderMedia();
  renderSlides();
}

async function refreshPlaylist({ preserveDirty }) {
  const res = await api("/api/playlist");
  state.playlist = res.playlist || emptyPlaylist();
  if (preserveDirty && state.dirty && state.draft) { renderSaveBar(); return; }
  state.draft = clone(state.playlist);
  setDirty(false);
  renderSlides();
}

async function refreshPlayback() {
  try {
    const { playback: p = {} } = await api("/api/playback/status");
    const dot = $("nowDot");
    dot.className = "status-dot " + ({ playing: "playing", paused: "paused", error: "error" }[p.state] || "");
    state.playing = p.state !== "paused";
    const live = (state.playlist?.slides || []).slice().sort((a, b) => a.order - b.order).filter((x) => x.enabled !== false);
    const onTv = p.available && p.slideCount > 0 && live[p.slideIndex] ? live[p.slideIndex].id : null;
    if (onTv !== state.onTv) { state.onTv = onTv; if (state.tab === "playlist") { renderNav(); renderPreview(); } }
    $("playPause").dataset.cmd = state.playing ? "pause" : "play";
    $("playPause").title = T(state.playing ? "Pause" : "Play");
    $("playPause").setAttribute("aria-label", $("playPause").title);
    $("ppIcon").setAttribute("d", state.playing ? "M8 5v14M16 5v14" : "M7 5l12 7-12 7z");
    if (!p.available) {
      $("nowTitle").textContent = T("Not on screen");
      $("nowSub").textContent = T("Open Presentation on the TV, or turn on “Start on boot” in Settings.");
    } else if (p.state === "empty") {
      $("nowTitle").textContent = T("Nothing playing");
      $("nowSub").textContent = T("Add slides in Playlist, then Save to TV.");
    } else {
      const label = T({ playing: "Playing", paused: "Paused", error: "Skipping", starting: "Starting" }[p.state] || p.state);
      $("nowTitle").textContent = p.slideCount > 0 ? T("{label} · Slide {n} of {count}", { label, n: p.slideIndex + 1, count: p.slideCount }) : label;
      $("nowSub").textContent = [p.mediaName || p.url, p.error].filter(Boolean).join(" — ");
    }
  } catch (err) { handleError(err, true); }
}

/* ------------------------------------------------------------------ library */

function setTab(tab) {
  state.tab = tab;
  document.querySelectorAll("[data-tab]").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === tab)));
  $("libraryView").classList.toggle("hidden", tab !== "library");
  $("playlistView").classList.toggle("hidden", tab !== "playlist");
  document.querySelector(".content").classList.toggle("wide", tab === "playlist");
  document.body.classList.toggle("wide", tab === "playlist");
}

function setFilter(f) {
  state.filter = f;
  document.querySelectorAll("[data-filter]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.filter === f)));
  renderMedia();
}

function renderMedia() {
  const grid = $("mediaGrid");
  grid.innerHTML = "";
  $("mediaCount").textContent = state.media.length ? state.media.length : "";
  const items = state.media.filter((m) => state.filter === "all" || kind(m) === state.filter);
  if (!items.length) {
    grid.innerHTML = `<div class="empty">${esc(T({ all: "No files yet. Drop images or videos above.", image: "No images yet.", video: "No videos yet." }[state.filter]))}</div>`;
    return;
  }
  items.forEach((m) => {
    const t = kind(m);
    const el = document.createElement("div");
    el.className = "tile";
    el.innerHTML = `
      <div class="thumb">${thumb(m)}<span class="kind">${T(t === "video" ? "Video" : t === "image" ? "Image" : "File")}</span></div>
      <div class="tile-body">
        <div class="tile-name" title="${esc(m.displayName)}">${esc(m.displayName)}</div>
        <div class="tile-meta">${formatBytes(m.sizeBytes)} · ${formatDate(m.createdAt)}</div>
        <div class="tile-actions">
          ${t ? `<button class="btn tinted small" data-a="add">${T("Add to Playlist")}</button>` : ""}
          <button class="icon-btn danger" data-a="del" title="${T("Delete")}" aria-label="${esc(T("Delete {name}", { name: m.displayName }))}"><svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/></svg></button>
        </div>
      </div>`;
    el.querySelector('[data-a="add"]')?.addEventListener("click", () => { addMediaSlide(m); setTab("playlist"); });
    el.querySelector('[data-a="del"]').addEventListener("click", async () => {
      const ok = await confirmSheet(T("Delete “{name}”?", { name: m.displayName }), T("It will be removed from the TV, along with any slides that use it."));
      if (!ok) return;
      try {
        await api(`/api/media/${m.id}`, { method: "DELETE" });
        removeMediaFromDraft(m.id);
        await refreshAll({ preserveDirty: true });
        toast(T("Deleted"));
      } catch (err) { handleError(err); }
    });
    grid.appendChild(el);
  });
}

async function uploadFiles(files) {
  const list = files.filter((f) => /^(image|video)\//.test(mimeFor(f)));
  if (list.length < files.length) toast(T("Only images and videos can be uploaded"), "error");
  let ok = 0;
  for (const file of list) {
    const row = document.createElement("div");
    row.className = "upload";
    row.innerHTML = `<div class="upload-row"><span class="upload-name">${esc(file.name)}</span><span class="secondary" data-s>${T("Waiting…")}</span></div><div class="bar"><i></i></div>`;
    $("uploads").prepend(row);
    try {
      await uploadOne(file, (pct) => { row.querySelector("i").style.width = pct + "%"; row.querySelector("[data-s]").textContent = T("{pct}% of {size}", { pct, size: formatBytes(file.size) }); });
      row.classList.add("done");
      row.querySelector("i").style.width = "100%";
      row.querySelector("[data-s]").textContent = T("Uploaded");
      ok++;
      await refreshMedia();
      setTimeout(() => row.remove(), 2500);
    } catch (err) {
      row.classList.add("failed");
      row.querySelector("[data-s]").textContent = err.message || T("Upload failed");
    }
  }
  // Report what actually happened, not how many files were picked.
  const failed = list.length - ok;
  if (ok && !failed) toast(ok === 1 ? T("{name} uploaded", { name: list[0].name }) : T("{n} files uploaded", { n: ok }));
  else if (ok && failed) toast(T("{ok} uploaded, {failed} failed", { ok, failed }), "error");
  else if (failed) toast(failed === 1 ? T("Upload failed") : T("{n} uploads failed", { n: failed }), "error");
}

function uploadOne(file, onProgress) {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append("file", file, file.name);
    form.append("mimeType", mimeFor(file));
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/media");
    xhr.setRequestHeader("Authorization", `Bearer ${state.token}`);
    xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
    xhr.setRequestHeader("X-File-Type", mimeFor(file));
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)); };
    xhr.onload = () => {
      const data = safeJson(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) return resolve(data);
      if (xhr.status === 401) forgetPairing();
      reject(new Error(data.error || (data.errors || []).join("; ") || T("Upload failed ({status})", { status: xhr.status })));
    };
    xhr.onerror = () => reject(new Error(T("Upload failed. Check the TV is on and on the same network.")));
    xhr.send(form);
  });
}

/* ------------------------------------------------------------------ playlist */

const ICON = {
  web: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"/></svg>`,
  video: `<svg viewBox="0 0 24 24"><path d="M8 5.5v13l10-6.5z" fill="currentColor"/></svg>`,
  hidden: `<svg viewBox="0 0 24 24"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/><path d="M4 4l16 16"/></svg>`,
  up: `<svg viewBox="0 0 24 24"><path d="M12 19V5M6 11l6-6 6 6"/></svg>`,
  down: `<svg viewBox="0 0 24 24"><path d="M12 5v14M6 13l6 6 6-6"/></svg>`,
  trash: `<svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/></svg>`,
};

/** Re-renders the whole editor: slide navigator, preview and inspector for the selected slide. */
function renderSlides() {
  ensureDraft();
  const slides = state.draft.slides;
  $("slideCount").textContent = slides.length ? slides.length : "";
  $("loopTime").textContent = loopLabel(slides);
  $("editor").classList.toggle("hidden", !slides.length);
  $("slidesEmpty").classList.toggle("hidden", slides.length > 0);
  if (!slides.some((s) => s.id === state.selected)) state.selected = slides[0]?.id || null;
  if (slides.length) { renderNav(); renderPreview(); renderInspector(); }
  renderSaveBar();
}

function selectedIndex() { return state.draft.slides.findIndex((s) => s.id === state.selected); }
function mediaOf(s) { return s.mediaId ? state.media.find((x) => x.id === s.mediaId) : null; }
function titleOf(s) { return s.type === "webpage" ? s.url || T("Webpage") : mediaOf(s)?.displayName || T("Missing file"); }

function select(id, focus) {
  state.selected = id;
  renderNav(); renderPreview(); renderInspector();
  if (focus) document.querySelector(`.nav-item[data-id="${CSS.escape(id)}"]`)?.focus();
  revealSelected();
}

/** Scrolls only the slide navigator (never the page) so the selected slide is visible. */
function revealSelected() {
  const el = document.querySelector(".nav-item.sel"), nav = el?.closest(".navigator");
  if (!el || !nav) return;
  const r = el.getBoundingClientRect(), n = nav.getBoundingClientRect(), pad = 8;
  if (r.top < n.top) nav.scrollTop -= n.top - r.top + pad;
  else if (r.bottom > n.bottom) nav.scrollTop += r.bottom - n.bottom + pad;
  if (r.left < n.left) nav.scrollLeft -= n.left - r.left + pad;
  else if (r.right > n.right) nav.scrollLeft += r.right - n.right + pad;
}

function renderNav() {
  const list = $("slides");
  if (!state.draft) return;
  list.innerHTML = "";
  state.draft.slides.forEach((s, i) => {
    const m = mediaOf(s), enabled = s.enabled !== false, sel = s.id === state.selected;
    const li = document.createElement("li");
    li.className = `nav-item${sel ? " sel" : ""}${enabled ? "" : " off"}`;
    li.dataset.id = s.id;
    li.tabIndex = sel ? 0 : -1;
    li.setAttribute("role", "option");
    li.setAttribute("aria-selected", String(sel));
    li.setAttribute("aria-label", T("Slide {n}: {title}", { n: i + 1, title: titleOf(s) }));
    li.innerHTML = `
      <span class="nav-num">${i + 1}</span>
      <div class="nav-thumb${s.type === "webpage" ? " web" : ""}">
        ${s.type === "webpage" ? ICON.web : m ? thumb(m) : ""}
        ${state.onTv === s.id && !state.dirty ? `<span class="chip tl live">${T("On TV")}</span>` : ""}
        ${enabled ? "" : `<span class="chip tr">${ICON.hidden}</span>`}
        <span class="chip br">${s.type === "video" ? ICON.video + T("Video") : (s.type === "webpage" && s.webpageAutoScroll !== false ? T("Scroll") : formatDuration(s.durationSec || defaultDuration(s.type)))}</span>
      </div>`;
    li.addEventListener("click", () => select(s.id));
    li.addEventListener("keydown", (e) => navKey(e, i));
    enableDrag(li, i);
    list.appendChild(li);
  });
}

function navKey(e, i) {
  // In right-to-left layouts the horizontal slide strip runs right to left, so ← means "next".
  const rtl = document.documentElement.dir === "rtl";
  const back = rtl ? "ArrowRight" : "ArrowLeft", fwd = rtl ? "ArrowLeft" : "ArrowRight";
  const s = state.draft.slides, prev = ["ArrowUp", back].includes(e.key), next = ["ArrowDown", fwd].includes(e.key);
  if (!prev && !next) return;
  e.preventDefault();
  if (e.altKey) { move(i, prev ? -1 : 1); document.querySelector(".nav-item.sel")?.focus(); return; }
  const t = s[i + (prev ? -1 : 1)];
  if (t) select(t.id, true);
}

function renderPreview() {
  const i = selectedIndex(), s = state.draft?.slides[i];
  const box = $("preview");
  if (!s) { box.innerHTML = ""; return; }
  const m = mediaOf(s), enabled = s.enabled !== false;
  const fit = { contain: "contain", cover: "cover", fill: "fill" }[s.fitMode] || "contain";
  let body = "";
  if (s.type === "webpage") {
    body = `<div class="web-card">${ICON.web}<div class="web-url">${esc(s.url || T("No address yet"))}</div>
      <div class="web-note">${T(s.webpageAutoScroll !== false ? "Shown on the TV as a 1920-px-wide page, scrolling down" : "Shown on the TV as a 1920-px-wide page")}</div></div>`;
  } else if (m && kind(m) === "image") {
    body = `<img src="${mediaUrl(m)}" alt="" style="object-fit:${fit}">`;
  } else if (m && kind(m) === "video") {
    body = `<video src="${mediaUrl(m)}#t=0.5" style="object-fit:${fit}" controls muted playsinline preload="metadata"></video>`;
  } else {
    body = `<div class="web-card" style="background:#1c1c1e"><div class="web-url">${T("Missing file")}</div><div class="web-note">${T("Choose another file for this slide.")}</div></div>`;
  }
  box.className = `preview${enabled ? "" : " off"}`;
  box.innerHTML = body + `<span class="chip tl">${T("{n} of {count}", { n: i + 1, count: state.draft.slides.length })}</span>`
    + (state.onTv === s.id && !state.dirty ? `<span class="chip tr live">${T("On TV now")}</span>` : enabled ? "" : `<span class="chip tr">${ICON.hidden}${T("Hidden")}</span>`);
  $("capTitle").textContent = titleOf(s);
  $("capSub").textContent = summary(s);
}

function renderInspector() {
  const i = selectedIndex(), s = state.draft?.slides[i];
  const box = $("inspector");
  if (!s) { box.innerHTML = ""; return; }
  const fit = s.fitMode || "contain", count = state.draft.slides.length;
  const g = [];
  if (s.type === "webpage") {
    g.push(`<div class="group-label">${T("Webpage")}</div><div class="group">
      <div class="cell stack"><input data-f="url" type="text" inputmode="url" autocapitalize="off" spellcheck="false" value="${esc(s.url || "")}" placeholder="https://example.com" aria-label="${T("Webpage address")}"></div>
      <div class="cell"><span>${T("Auto-scroll")}</span><label class="switch"><input type="checkbox" data-f="webpageAutoScroll" ${s.webpageAutoScroll !== false ? "checked" : ""} aria-label="${T("Auto-scroll")}"><span></span></label></div>
      ${s.webpageAutoScroll !== false ? `
      <div class="cell stack"><span>${T("Scroll speed")}</span><div class="row"><input data-f="webpageScrollSpeedPxPerSec" type="range" min="5" max="400" value="${Math.min(400, s.webpageScrollSpeedPxPerSec || 40)}" aria-label="${T("Scroll speed")}"><span class="val" data-v="speed">${T("{n} px/s", { n: s.webpageScrollSpeedPxPerSec || 40 })}</span></div></div>
      <div class="cell stack"><span>${T("Pause on each screen")}</span><div class="row"><input data-f="webpageScrollPauseSec" type="range" min="0" max="60" value="${Math.min(60, s.webpageScrollPauseSec ?? 5)}" aria-label="${T("Pause on each screen")}"><span class="val" data-v="wait">${T("{n} s", { n: s.webpageScrollPauseSec ?? 5 })}</span></div></div>` : ""}
    </div>`);
  } else {
    const opts = state.media.filter((m) => kind(m) === s.type)
      .map((m) => `<option value="${m.id}" ${m.id === s.mediaId ? "selected" : ""}>${esc(m.displayName)}</option>`).join("");
    g.push(`<div class="group-label">${T(s.type === "video" ? "Video" : "Image")}</div><div class="group">
      <div class="cell"><span>${T("File")}</span><select data-f="mediaId" aria-label="${T("File")}" dir="auto">${opts}</select></div></div>`);
  }
  const timing = s.type === "video"
    ? `<div class="cell"><span>${T("Time on screen")}</span><span class="note">${T("Plays to the end")}</span></div>`
    : s.type === "webpage" && s.webpageAutoScroll !== false
      ? `<div class="cell"><span>${T("Time on screen")}</span><span class="note">${T("Until it reaches the bottom")}</span></div>`
      : `<div class="cell"><span>${T("Time on screen")}</span><span class="stepper"><button type="button" data-step="-5" aria-label="${T("5 seconds less")}">−</button><input data-f="durationSec" type="number" min="1" max="86400" value="${s.durationSec || defaultDuration(s.type)}" aria-label="${T("Seconds")}"><span class="unit">${T("s")}</span><button type="button" data-step="5" aria-label="${T("5 seconds more")}">+</button></span></div>`;
  g.push(`<div class="group-label">${T("Playback")}</div><div class="group">
    ${timing}
    <div class="cell"><span>${T("Fit")}</span><div class="segmented small" role="group" aria-label="${T("Fit")}">
      ${["contain", "cover", "fill"].map((f) => `<button type="button" data-fit="${f}" aria-pressed="${f === fit}" title="${T({ contain: "Show all of it", cover: "Fill the screen, cropping edges", fill: "Stretch to the screen" }[f])}">${fitLabel(f)}</button>`).join("")}</div></div>
    ${s.type === "video" ? `<div class="cell"><span>${T("Sound")}</span><label class="switch"><input type="checkbox" data-f="muted" data-invert ${s.muted === false ? "checked" : ""} aria-label="${T("Play sound")}"><span></span></label></div>` : ""}
    <div class="cell"><span>${T("Show this slide")}</span><label class="switch"><input type="checkbox" data-f="enabled" ${s.enabled !== false ? "checked" : ""} aria-label="${T("Show this slide")}"><span></span></label></div>
  </div>`);
  g.push(`<div class="insp-actions">
    <button type="button" class="btn small" data-a="up" ${i === 0 ? "disabled" : ""}>${ICON.up}${T("Earlier")}</button>
    <button type="button" class="btn small" data-a="down" ${i === count - 1 ? "disabled" : ""}>${ICON.down}${T("Later")}</button>
    <button type="button" class="btn small remove" data-a="remove">${ICON.trash}${T("Remove Slide")}</button></div>`);
  box.innerHTML = g.join("");

  box.querySelectorAll("[data-f]").forEach((input) => {
    const live = input.type === "range" || input.type === "number" || input.dataset.f === "url";
    input.addEventListener(live ? "input" : "change", () => updateField(i, input));
  });
  box.querySelectorAll("[data-fit]").forEach((b) => b.addEventListener("click", () => {
    s.fitMode = b.dataset.fit; markDirty();
    box.querySelectorAll("[data-fit]").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    renderPreview();
  }));
  box.querySelectorAll("[data-step]").forEach((b) => b.addEventListener("click", () => {
    const input = box.querySelector('[data-f="durationSec"]');
    input.value = Math.max(1, Number(input.value || 0) + Number(b.dataset.step));
    updateField(i, input);
  }));
  box.querySelector('[data-a="up"]').addEventListener("click", () => move(i, -1));
  box.querySelector('[data-a="down"]').addEventListener("click", () => move(i, 1));
  box.querySelector('[data-a="remove"]').addEventListener("click", () => removeSlide(i));
}

function updateField(i, input) {
  ensureDraft();
  const s = state.draft.slides[i];
  if (!s) return;
  const f = input.dataset.f;
  if (input.type === "checkbox") s[f] = "invert" in input.dataset ? !input.checked : input.checked;
  else if (["durationSec", "webpageScrollSpeedPxPerSec", "webpageScrollPauseSec"].includes(f)) s[f] = normalizeNumber(f, input.value);
  else s[f] = input.value;
  if (f === "webpageScrollSpeedPxPerSec") $("inspector").querySelector('[data-v="speed"]').textContent = T("{n} px/s", { n: s[f] });
  if (f === "webpageScrollPauseSec") $("inspector").querySelector('[data-v="wait"]').textContent = T("{n} s", { n: s[f] });
  $("loopTime").textContent = loopLabel(state.draft.slides);
  markDirty();
  if (f === "durationSec") {
    const chip = document.querySelector(".nav-item.sel .chip.br");
    if (chip) chip.textContent = formatDuration(s[f]);
  } else renderNav();
  if (["mediaId", "enabled", "url", "webpageAutoScroll"].includes(f)) renderPreview(); else { $("capSub").textContent = summary(s); }
  if (f === "webpageAutoScroll") renderInspector();
}

function addMediaSlide(m) {
  const type = kind(m);
  if (!type) { toast(T("Only images and videos can be slides"), "error"); return; }
  addSlide({ type, mediaId: m.id, durationSec: type === "video" ? 0 : defaultDuration(type), fitMode: "contain", muted: true, loop: false, enabled: true });
}

function addSlide(slide) {
  ensureDraft();
  const created = { id: randomId(), order: state.draft.slides.length, ...slide };
  state.draft.slides.push(created);
  state.selected = created.id;
  markDirty();
  renderSlides();
  toast(T("Slide added — Save to TV when ready"));
}

function move(i, dir) {
  const t = i + dir, s = state.draft.slides;
  if (t < 0 || t >= s.length) return;
  [s[i], s[t]] = [s[t], s[i]];
  normalizeOrders(); markDirty(); renderSlides();
}

function moveTo(from, to) {
  const s = state.draft.slides;
  if (from === to || from < 0 || to < 0) return;
  const [item] = s.splice(from, 1);
  s.splice(to > from ? to - 1 : to, 0, item);
  normalizeOrders(); markDirty(); renderSlides();
}

function removeSlide(i) {
  const s = state.draft.slides;
  const next = s[i + 1] || s[i - 1];
  s.splice(i, 1);
  state.selected = next?.id || null;
  normalizeOrders(); markDirty(); renderSlides();
}

/** Drag a thumbnail above or below another; the drop line shows where it lands. */
function enableDrag(li, i) {
  li.draggable = true;
  const clear = () => document.querySelectorAll(".drop-before, .drop-after").forEach((x) => x.classList.remove("drop-before", "drop-after"));
  const after = (e) => {
    const r = li.getBoundingClientRect();
    if (!getComputedStyle($("slides")).gridAutoFlow.startsWith("column")) return e.clientY > r.top + r.height / 2;
    // Horizontal strip: "after" is to the right in LTR and to the left in RTL.
    const rightHalf = e.clientX > r.left + r.width / 2;
    return document.documentElement.dir === "rtl" ? !rightHalf : rightHalf;
  };
  li.addEventListener("dragstart", (e) => { li.classList.add("dragging"); e.dataTransfer.setData("text/plain", String(i)); e.dataTransfer.effectAllowed = "move"; });
  li.addEventListener("dragend", () => { li.classList.remove("dragging"); clear(); });
  li.addEventListener("dragover", (e) => { e.preventDefault(); clear(); li.classList.add(after(e) ? "drop-after" : "drop-before"); });
  li.addEventListener("dragleave", () => li.classList.remove("drop-before", "drop-after"));
  li.addEventListener("drop", (e) => {
    e.preventDefault();
    const to = after(e) ? i + 1 : i;
    clear();
    moveTo(Number(e.dataTransfer.getData("text/plain")), to);
  });
}

function toggleMenu(open) {
  const menu = $("addMenu"), show = open ?? menu.classList.contains("hidden");
  menu.classList.toggle("hidden", !show);
  $("addBtn").setAttribute("aria-expanded", String(show));
  if (show) menu.querySelector("button").focus();
}

function openWebpageSheet() {
  $("sheetTitle").textContent = T("Add Webpage");
  $("sheetBody").innerHTML = `<form id="webForm" class="web-form">
    <div class="group"><div class="cell stack"><input id="webUrl" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="example.com/dashboard" aria-label="${T("Webpage address")}" required></div></div>
    <p class="secondary small">${T("The page opens full screen on the TV and scrolls down slowly. You can change scrolling in the slide’s settings.")}</p>
    <button class="btn primary wide" type="submit">${T("Add Slide")}</button></form>`;
  $("sheet").classList.remove("hidden");
  setTimeout(() => $("webUrl").focus(), 50);
  $("webForm").addEventListener("submit", (e) => {
    e.preventDefault();
    let url = $("webUrl").value.trim();
    if (url && !/^[a-z]+:\/\//i.test(url)) url = "https://" + url;
    if (!/^https?:\/\//.test(url)) { toast(T("Webpage address must start with http:// or https://"), "error"); return; }
    addSlide({
      type: "webpage", url, durationSec: defaultDuration("webpage"), fitMode: "contain", muted: true, loop: false,
      webpageAutoScroll: true, webpageScrollSpeedPxPerSec: 40, webpageScrollPauseSec: 5, enabled: true,
    });
    closeSheet();
  });
}

function openPicker() {
  const items = state.media.filter((m) => kind(m));
  $("sheetTitle").textContent = T("Add from Library");
  $("sheetBody").innerHTML = items.length
    ? `<div class="pick">${items.map((m) => `<button type="button" data-id="${m.id}"><div class="thumb">${thumb(m)}<span class="kind">${T(kind(m) === "video" ? "Video" : "Image")}</span></div><div class="tile-body"><div class="tile-name">${esc(m.displayName)}</div><div class="tile-meta">${formatBytes(m.sizeBytes)}</div></div></button>`).join("")}</div>`
    : `<div class="empty">${T("Your Library is empty. Upload images or videos first.")}</div>`;
  $("sheetBody").querySelectorAll("[data-id]").forEach((b) => b.addEventListener("click", () => {
    addMediaSlide(state.media.find((m) => m.id === b.dataset.id));
    b.style.boxShadow = "0 0 0 2px var(--green)";
  }));
  $("sheet").classList.remove("hidden");
}

function closeSheet() { $("sheet").classList.add("hidden"); }

async function savePlaylist() {
  if (!state.dirty) return;
  ensureDraft();
  normalizeOrders();
  // Addresses typed without a scheme ("example.com") get https:// added.
  state.draft.slides.forEach((s) => { if (s.type === "webpage" && s.url && !/^[a-z]+:\/\//i.test(s.url.trim())) s.url = "https://" + s.url.trim(); });
  const errors = validateDraft();
  if (errors.length) { $("saveError").textContent = errors.join(" "); return; }
  $("saveBtn").disabled = true;
  $("saveBtn").textContent = T("Saving…");
  try {
    const res = await api("/api/playlist", { method: "PUT", body: { name: "Default", slides: state.draft.slides } });
    state.playlist = res.playlist;
    state.draft = clone(res.playlist);
    setDirty(false);
    renderSlides();
    await refreshPlayback();
    toast(T("Saved — now playing on the TV"));
  } catch (err) {
    $("saveError").textContent = err.message;
  } finally {
    $("saveBtn").disabled = false;
    $("saveBtn").textContent = T("Save to TV");
  }
}

async function control(cmd) {
  try {
    await api("/api/playback/control", { method: "POST", body: { command: cmd } });
    if (cmd === "showConnectionInfo") toast(T("Showing the address and code on the TV"));
    setTimeout(refreshPlayback, 300);
  } catch (err) { handleError(err); }
}

/* ------------------------------------------------------------------ draft helpers (as admin.js) */

function validateDraft() {
  const errors = [];
  state.draft.slides.forEach((s, i) => {
    const v = { n: i + 1 };
    if (s.type !== "video" && s.durationSec < 1) errors.push(T("Slide {n}: time on screen must be at least 1 second.", v));
    if (s.type === "webpage" && !/^https?:\/\//.test(s.url || "")) errors.push(T("Slide {n}: webpage address must start with http:// or https://.", v));
    if (s.type === "webpage") {
      const speed = Number(s.webpageScrollSpeedPxPerSec || 40), wait = Number(s.webpageScrollPauseSec ?? 5);
      if (speed < 5 || speed > 1000) errors.push(T("Slide {n}: scroll speed must be between 5 and 1000.", v));
      if (wait < 0 || wait > 300) errors.push(T("Slide {n}: pause must be between 0 and 300 seconds.", v));
    }
    if (s.type === "image" || s.type === "video") {
      if (!s.mediaId) errors.push(T("Slide {n}: choose a file.", v));
      else if (!state.media.some((m) => m.id === s.mediaId)) errors.push(T("Slide {n}: uses a deleted file.", v));
    }
  });
  return errors;
}

function removeMediaFromDraft(id) {
  if (!state.draft) return;
  const before = state.draft.slides.length;
  state.draft.slides = state.draft.slides.filter((s) => s.mediaId !== id);
  if (state.draft.slides.length !== before) { normalizeOrders(); markDirty(); renderSlides(); }
}

function pruneDraftMissingMedia() {
  if (!state.draft) return;
  const ids = new Set(state.media.map((m) => m.id));
  const before = state.draft.slides.length;
  state.draft.slides = state.draft.slides.filter((s) => (s.type !== "image" && s.type !== "video") || (s.mediaId && ids.has(s.mediaId)));
  if (state.draft.slides.length !== before) { normalizeOrders(); state.dirty = true; }
}

function ensureDraft() { if (!state.draft) state.draft = state.playlist ? clone(state.playlist) : emptyPlaylist(); }
function emptyPlaylist() { return { id: "default", name: "Default", slides: [], active: true }; }
function clone(p) { return JSON.parse(JSON.stringify(p || emptyPlaylist())); }
function normalizeOrders() { state.draft.slides.forEach((s, i) => (s.order = i)); }
function markDirty() { setDirty(true); }
function setDirty(d) { state.dirty = d; $("saveError").textContent = ""; renderSaveBar(); }
function renderSaveBar() { $("saveBar").classList.toggle("show", state.dirty && !!state.token); }
function confirmLeave() { return !state.dirty || window.confirm(T("You have unsaved playlist changes. Leave anyway?")); }

function fitLabel(f) { return T({ contain: "Contain", cover: "Cover", fill: "Fill" }[f] || "Contain"); }

function summary(s) {
  const t = s.type === "video" ? T("Video · plays to the end") : s.type === "image" ? T("Image · {d}", { d: formatDuration(s.durationSec || 10) })
    : s.webpageAutoScroll !== false ? T("Webpage · auto-scroll") : T("Webpage · {d}", { d: formatDuration(s.durationSec || 20) });
  return `${t} · ${fitLabel(s.fitMode || "contain")}${s.enabled === false ? " · " + T("Hidden") : ""}`;
}

function loopLabel(slides) {
  const on = slides.filter((s) => s.enabled !== false);
  if (!on.length) return T("Empty");
  const fixed = on.filter((s) => s.type === "image" || (s.type === "webpage" && s.webpageAutoScroll === false))
    .reduce((n, s) => n + Number(s.durationSec || defaultDuration(s.type)), 0);
  const variable = on.length - on.filter((s) => s.type === "image" || (s.type === "webpage" && s.webpageAutoScroll === false)).length;
  const base = T("Loop {d}", { d: formatDuration(fixed) });
  return variable ? T("{base} + {n} video/scroll", { base, n: variable }) : base;
}

/* ------------------------------------------------------------------ utils */

function kind(m) {
  if (!m) return "";
  if ((m.mimeType || "").startsWith("image/")) return "image";
  if ((m.mimeType || "").startsWith("video/")) return "video";
  const ext = String(m.displayName || m.fileName || "").split(".").pop().toLowerCase();
  if (["jpg", "jpeg", "png", "gif", "webp"].includes(ext)) return "image";
  if (["mp4", "m4v", "webm", "mkv", "mov", "3gp", "3gpp", "avi"].includes(ext)) return "video";
  return "";
}

function mediaUrl(m) { return `/api/media/${encodeURIComponent(m.id)}/file?token=${encodeURIComponent(state.token)}`; }

function thumb(m) {
  const src = mediaUrl(m);
  if (kind(m) === "image") return `<img loading="lazy" src="${src}" alt="">`;
  if (kind(m) === "video") return `<video preload="metadata" muted playsinline src="${src}#t=0.5"></video>`;
  return "";
}

function mimeFor(file) {
  if (file.type) return file.type;
  const ext = String(file.name).split(".").pop().toLowerCase();
  return ({ jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif", webp: "image/webp", mp4: "video/mp4", m4v: "video/mp4",
    webm: "video/webm", mkv: "video/x-matroska", mov: "video/quicktime", "3gp": "video/3gpp", "3gpp": "video/3gpp", avi: "video/x-msvideo" })[ext]
    || "application/octet-stream";
}

function defaultDuration(type) { return type === "video" ? 0 : type === "webpage" ? 20 : 10; }

function normalizeNumber(f, v) {
  const n = Number(v);
  if (f === "durationSec") return Math.max(1, Number.isFinite(n) ? Math.round(n) : 1);
  if (f === "webpageScrollSpeedPxPerSec") return clamp(Number.isFinite(n) ? n : 40, 5, 1000);
  if (f === "webpageScrollPauseSec") return clamp(Number.isFinite(n) ? n : 5, 0, 300);
  return n;
}
function clamp(v, a, b) { return Math.min(b, Math.max(a, v)); }

async function api(path, opts = {}) {
  const headers = { "Content-Type": "application/json" };
  if (opts.auth !== false && state.token) headers.Authorization = `Bearer ${state.token}`;
  const res = await fetch(path, { method: opts.method || "GET", headers, body: opts.body ? JSON.stringify(opts.body) : undefined });
  const text = await res.text();
  const data = text ? safeJson(text) : {};
  if (!res.ok) {
    const err = new Error(data.error || (data.errors || []).join("; ") || T("Request failed ({status})", { status: res.status }));
    err.status = res.status;
    throw err;
  }
  return data;
}

function handleError(err, quiet) {
  if (err.status === 401 || err.status === 403) { forgetPairing(); $("pairError").textContent = T("Pair this browser again with the code on the TV."); return; }
  if (!quiet) toast(err.message || T("Something went wrong"), "error");
}

let toastTimer;
function toast(msg, type) {
  const t = $("toast");
  t.textContent = msg;
  t.className = `toast show${type === "error" ? " error" : ""}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.className = "toast"), 2600);
}

let confirmResolve;
function confirmSheet(title, text) {
  $("confirmTitle").textContent = title;
  $("confirmText").textContent = text;
  $("confirm").classList.remove("hidden");
  return new Promise((resolve) => {
    confirmResolve = resolve;
    $("confirmOk").onclick = () => closeConfirm(true);
    $("confirmCancel").onclick = () => closeConfirm(false);
  });
}
function closeConfirm(v) {
  if ($("confirm").classList.contains("hidden")) return;
  $("confirm").classList.add("hidden");
  if (confirmResolve) confirmResolve(v);
  confirmResolve = null;
}

function safeJson(t) { try { return t ? JSON.parse(t) : {}; } catch (_) { return {}; } }
function randomId() { return (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`); }
function esc(v) { return String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]); }
function formatBytes(b) { if (!b) return "0 B"; const u = ["B", "KB", "MB", "GB"]; let i = 0, n = b; while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; } return `${n.toFixed(n < 10 && i ? 1 : 0)} ${u[i]}`; }
function formatDate(ms) { try { return new Date(ms).toLocaleDateString(LANG === "en" ? undefined : LANG, { day: "numeric", month: "short" }); } catch (_) { return ""; } }
function formatDuration(sec) { sec = Number(sec) || 0; if (sec < 60) return T("{s}s", { s: sec }); const m = Math.floor(sec / 60), s = sec % 60; return s ? T("{m}m {s}s", { m, s }) : T("{m}m", { m }); }
