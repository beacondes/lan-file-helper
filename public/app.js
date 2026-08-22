const $ = id => document.getElementById(id);

// 设备标识（本地生成并持久化）
let deviceId = localStorage.getItem('deviceId');
if (!deviceId) {
  deviceId = '设备-' + Math.random().toString(36).slice(2, 6).toUpperCase();
  localStorage.setItem('deviceId', deviceId);
}

const chat = $('chat');
let emptyTip = $('empty-tip');

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function fmtTime(ts) {
  const d = new Date(ts), now = new Date();
  const hm = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  if (d.toDateString() === now.toDateString()) return hm;
  return (d.getMonth() + 1) + '-' + d.getDate() + ' ' + hm;
}

function fmtSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
  return (bytes / 1073741824).toFixed(2) + ' GB';
}

const isImage = n => /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(n);
const isVideo = n => /\.(mp4|webm|mov|m4v)$/i.test(n);
const isAudio = n => /\.(mp3|wav|ogg|m4a|flac)$/i.test(n);

// 已渲染消息去重（乐观更新 + WS 广播都会触发，避免重复）
const renderedIds = new Set();

function renderMessage(m) {
  if (renderedIds.has(m.id)) return;
  renderedIds.add(m.id);
  if (emptyTip) { emptyTip.remove(); emptyTip = null; }
  const mine = m.device === deviceId;
  const el = document.createElement('div');
  el.className = 'msg-row ' + (mine ? 'mine' : 'theirs');

  let body;
  if (m.type === 'text') {
    body = '<div class="bubble text">' + escapeHtml(m.content) + '</div>';
  } else {
    const name = escapeHtml(m.name);
    if (isImage(m.name)) {
      body = '<div class="bubble file"><img class="file-img" src="' + m.url + '" alt="' + name + '" loading="lazy"><div class="file-name">' + name + '</div></div>';
    } else if (isVideo(m.name)) {
      body = '<div class="bubble file"><video class="file-img" src="' + m.url + '" controls preload="metadata"></video><div class="file-name">' + name + '</div></div>';
    } else if (isAudio(m.name)) {
      body = '<div class="bubble file"><audio src="' + m.url + '" controls></audio><div class="file-name">' + name + '</div></div>';
    } else {
      body = '<a class="bubble file" href="' + m.url + '" download="' + name + '"><div class="file-card"><span class="file-icon">📄</span><div class="file-meta"><div class="file-name">' + name + '</div><div class="file-size">' + fmtSize(m.size) + '</div></div></div></a>';
    }
  }

  el.innerHTML = '<div class="msg-meta">' + (mine ? '我' : escapeHtml(m.device)) + ' · ' + fmtTime(m.timestamp) + '</div>' + body;
  chat.appendChild(el);
  scrollToBottom();
}

function scrollToBottom() { chat.scrollTop = chat.scrollHeight; }

// —— 连接状态指示 ——
let wsConnected = false;
function updateStatus() {
  const dot = $('status-dot');
  if (dot) {
    dot.className = 'status-dot ' + (wsConnected ? 'on' : 'off');
    dot.title = wsConnected ? '已连接' : '未连接（自动重连中）';
  }
}

// —— WebSocket 实时同步 ——
function connectWS() {
  const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
  let ws;
  try { ws = new WebSocket(proto + location.host); } catch (e) { scheduleReconnect(); return; }
  ws.onopen = () => { wsConnected = true; updateStatus(); };
  ws.onmessage = e => {
    let data;
    try { data = JSON.parse(e.data); } catch (err) { return; }
    if (data.type === 'history') {
      renderedIds.clear();
      chat.innerHTML = '';
      emptyTip = null;
      data.messages.forEach(renderMessage);
    } else if (data.type === 'message') {
      renderMessage(data.message);
    }
  };
  ws.onclose = () => { wsConnected = false; updateStatus(); scheduleReconnect(); };
  ws.onerror = () => { try { ws.close(); } catch (e) {} };

  function scheduleReconnect() { setTimeout(connectWS, 2000); }
}
connectWS();

// —— 发送文本（乐观更新：拿到响应立即渲染，不依赖 WS）——
async function sendText() {
  const input = $('input');
  const content = input.value.trim();
  if (!content) return;
  try {
    const res = await fetch('/api/message', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, device: deviceId })
    });
    const data = await res.json();
    if (data.message) renderMessage(data.message);
  } catch (e) {
    alert('发送失败：无法连接服务器');
    return;
  }
  input.value = '';
  input.focus();
}
$('send-btn').onclick = sendText;
$('input').onkeydown = e => { if (e.key === 'Enter') sendText(); };

// —— 发送文件 ——
$('file-btn').onclick = () => $('file-input').click();
$('file-input').onchange = () => {
  for (const f of $('file-input').files) uploadFile(f);
  $('file-input').value = '';
};

async function uploadFile(file) {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('device', deviceId);
  try {
    const res = await fetch('/api/upload', { method: 'POST', body: fd });
    const data = await res.json();
    if (data.message) renderMessage(data.message);
  } catch (e) { alert('文件发送失败'); }
}

// —— 显示局域网地址 ——
fetch('/api/info').then(r => r.json()).then(d => {
  const ips = d.ips || [];
  $('lan-ip').textContent = ips.length ? '局域网：http://' + ips[0] + location.port : '本机';
}).catch(() => {});

// —— PWA ——
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
