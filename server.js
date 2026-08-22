const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const PORT = process.env.PORT || 3000;
// 打包成 exe（pkg）后，数据/静态资源放在 exe 同目录；普通 node 运行则用项目目录
const isPkg = typeof process.pkg !== 'undefined';
const baseDir = isPkg ? path.dirname(process.execPath) : __dirname;
const PUBLIC_DIR = path.join(baseDir, 'public');
// 数据目录：优先用 DATA_DIR 环境变量（Windows 服务安装时指向用户「下载」目录），否则用程序目录
const DATA_DIR = process.env.DATA_DIR || baseDir;
const UPLOAD_DIR = path.join(DATA_DIR, 'uploads');
const DATA_FILE = path.join(DATA_DIR, 'messages.json');

if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// —— 消息持久化（重启不丢）——
let messages = [];
try {
  const saved = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  if (Array.isArray(saved)) messages = saved;
} catch (e) {}

function saveMessages() {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(messages));
  } catch (e) {}
}

// —— 静态资源 ——
app.use(express.static(PUBLIC_DIR));
app.use('/uploads', express.static(UPLOAD_DIR));

// —— 文件上传（最大 2GB）——
const upload = multer({ dest: UPLOAD_DIR, limits: { fileSize: 2 * 1024 * 1024 * 1024 } });

app.post('/api/upload', upload.single('file'), (req, res) => {
  const file = req.file;
  if (!file) return res.status(400).json({ error: '未收到文件' });
  // 文件名转码（multer 用 latin1 解析，中文需转回 utf8）+ 去路径 + 重名加序号
  const rawName = Buffer.from(file.originalname, 'latin1').toString('utf8');
  const base = path.basename(rawName);
  const ext = path.extname(base);
  const stem = path.basename(base, ext);
  let finalName = base;
  let i = 1;
  while (fs.existsSync(path.join(UPLOAD_DIR, finalName))) {
    finalName = stem + '(' + i + ')' + ext;
    i++;
  }
  fs.renameSync(file.path, path.join(UPLOAD_DIR, finalName));
  const msg = {
    id: crypto.randomUUID(),
    type: 'file',
    name: base,
    size: file.size,
    url: '/uploads/' + finalName,
    device: req.body.device || '未知设备',
    timestamp: Date.now()
  };
  messages.push(msg);
  saveMessages();
  broadcast({ type: 'message', message: msg });
  res.json({ ok: true, message: msg });
});

// —— 文本消息 ——
app.post('/api/message', express.json(), (req, res) => {
  const content = (req.body.content || '').trim();
  if (!content) return res.status(400).json({ error: '内容为空' });
  const msg = {
    id: crypto.randomUUID(),
    type: 'text',
    content,
    device: req.body.device || '未知设备',
    timestamp: Date.now()
  };
  messages.push(msg);
  saveMessages();
  broadcast({ type: 'message', message: msg });
  res.json({ ok: true, message: msg });
});

// —— 消息历史 ——
app.get('/api/messages', (req, res) => res.json({ messages }));

// —— 局域网信息 ——
function getLanIPs() {
  const ips = [];
  const ifs = os.networkInterfaces();
  for (const name in ifs) {
    for (const iface of ifs[name]) {
      if (iface.family === 'IPv4' && !iface.internal) ips.push(iface.address);
    }
  }
  return ips;
}

app.get('/api/info', (req, res) => res.json({ ips: getLanIPs() }));

// —— WebSocket 广播 ——
function broadcast(data) {
  const str = JSON.stringify(data);
  wss.clients.forEach(c => { if (c.readyState === WebSocket.OPEN) c.send(str); });
}

wss.on('connection', ws => {
  ws.send(JSON.stringify({ type: 'history', messages }));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('========== 局域网文件助手 ==========');
  console.log('  本机访问:   http://localhost:' + PORT);
  getLanIPs().forEach(ip => console.log('  局域网访问: http://' + ip + ':' + PORT));
  console.log('====================================');
});
