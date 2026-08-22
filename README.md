# 局域网文件助手 (lan-file-helper)

仿微信「文件助手」的局域网传输工具：Windows 电脑跑一个服务，安卓手机（或任何设备）连同一 WiFi，通过浏览器即可互传**文本和文件**。全程局域网直传，不经过公网。

## 功能

- 💬 文本消息实时收发（WebSocket 推送）
- 📎 文件传输：图片在线预览、音视频在线播放、其他文件点击下载
- 🔄 多设备实时同步，消息历史持久化（重启不丢）
- 📱 安卓端 PWA：浏览器打开即可「添加到主屏幕」，像 App 一样用
- 🖥 Windows 端无需安装：Node.js 一条命令启动

## 技术栈

- Node.js + Express（HTTP + 文件上传）
- ws（WebSocket 实时消息）
- multer（文件上传）
- 原生 HTML/CSS/JS 前端 + PWA

## 使用步骤

### 1. Windows 端启动服务

```bash
# 需要先装 Node.js（https://nodejs.org）
git clone https://github.com/beacondes/lan-file-helper.git
cd lan-file-helper
npm install
npm start
```

启动后会打印局域网地址，例如：

```
========== 局域网文件助手 ==========
  本机访问:   http://localhost:3000
  局域网访问: http://192.168.1.100:3000
====================================
```

> ⚠️ Windows 首次启动会弹「Windows 防火墙」提示，**勾选允许访问**（专网+公用都勾），否则安卓连不上。

### 2. 安卓端访问

1. 手机连**和电脑同一个 WiFi**
2. 浏览器打开电脑打印的局域网地址（如 `http://192.168.1.100:3000`）
3. （推荐）浏览器菜单 → **添加到主屏幕**，装成 App 图标

### 3. 开始传输

- 电脑浏览器打开 `http://localhost:3000`，或安卓打开局域网地址
- 底部输入文字点「发送」，或点 📎 发文件
- 消息和文件会**实时同步到所有已连接的设备**

## 自定义端口

默认 3000，可在启动时指定：

```bash
# Windows (PowerShell)
$env:PORT=8080; npm start
# 或 Linux/Mac
PORT=8080 npm start
```

## 注意事项

- 电脑和手机必须**在同一个局域网**（同一 WiFi 或同一路由器下）
- 文件保存在本机 `uploads/` 目录，消息记录在 `messages.json`
- 传输大文件时不要关闭页面或断网
- 服务是纯局域网工具，**不要暴露到公网**（无鉴权）
