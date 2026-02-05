# NeoLink

**Java 21 驱动的高性能内网穿透客户端**

支持 TCP / UDP 协议 • Proxy Protocol v2 • 多节点切换

[![GitHub Release](https://img.shields.io/github/v/release/NeoLinkProxy/NeoLink?style=flat-square&color=blue)](https://github.com/NeoLinkProxy/NeoLink/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/NeoLinkProxy/NeoLink/total?style=flat-square&color=success)](https://github.com/NeoLinkProxy/NeoLink/releases)
[![GitHub Stars](https://img.shields.io/github/stars/NeoLinkProxy/NeoLink?style=flat-square&logo=github)](https://github.com/NeoLinkProxy/NeoLink)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](https://opensource.org/licenses/MIT)

![Java](https://img.shields.io/badge/Java-21%2B-orange?style=flat-square&logo=openjdk&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?style=flat-square&logo=windows&logoColor=white)
![Build](https://img.shields.io/badge/Build-Gradle-blue?style=flat-square&logo=gradle)
![GUI](https://img.shields.io/badge/Interface-Compose%20Multiplatform-blueviolet?style=flat-square&logo=kotlin)

---

> **简介**
>
> NeoLink 是一个轻量级的内网穿透客户端，用于将本地 TCP/UDP 服务（例如 Minecraft 服务器、RDP 远程桌面）暴露给公网 NeoProxyServer。
>
> **推荐场景**：RDP 远程桌面、Minecraft 服务器、HTTP 文件服务、需要获取真实 IP 的 Web 服务。

![演示图片](/image.png "Magic Gardens")

---

## 📖 说明 (概览)

NeoLink 项目同时提供 **命令行 (CLI)** 与 **Compose Multiplatform 图形界面 (GUI)** 两种运行模式，并支持通过 HTTP/SOCKS 代理访问本地或远端服务。客户端包含自动重连、心跳检测、日志记录与远程更新下载功能。

自 4.7.0 版本开始，同步支持 TCP 和 UDP。本项目已迁移至 **Compose Multiplatform** 框架，提供更现代、响应更快的声明式 UI 体验。

### ✨ 核心亮点

- 🚀 **现代 UI 架构**：基于 **Compose Multiplatform** 构建，完美适配跨平台深色模式与高分屏。
- ☕ **Java 21 + Kotlin 驱动**：利用现代强类型语言特性，性能更优，转发更稳定。
- ↔️ **通用 TCP/UDP**：几乎所有类型的服务均可穿透。
- 🆔 **真实 IP 透传**：支持 **Proxy Protocol v2**，配合 Nginx 等后端可获取用户真实 IP。
- 🗄️ **多节点管理**：通过 `node.json` 配置多个服务端节点，启动时灵活切换。
- 🔄 **稳定可靠**：自动重连、心跳保活，防止 NAT 超时断开。
- ⬆️ **自动更新**：支持自动检测并下载新版本，简化维护流程。
- 🔌 **代理支持**：支持 HTTP/SOCKS5 代理连接 NeoProxyServer 或本地服务。
- 🌐 **多语言**：自动识别系统语言（中/英），也可通过参数强制指定。

---

## 🚀 快速开始

*   🪟 **多平台支持**：支持 Windows、Linux 及 macOS。
*   🎁 **Release 界面**：提供了各平台的打包版本，无需配置复杂的 Java 环境即可使用。
*   ⚡ **一键启动**：支持通过命令行参数直接指定密钥和本地端口。

### **获取客户端:** 
🎁 从本项目的 "Releases" 页面下载最新的客户端

### 🖥️ **命令行模式 (Terminal)**
将构建后的 JAR 放到工作目录并运行（强制指定中文）：
```bash
# 示例命令
java -jar NeoLink-XXXX.jar --nogui --zh-cn

# 可选参数追加到后面
# --output-file=path/to/logfile.log  将日志写入指定文件
# --key=...                          访问密钥
# --local-port=...                   本地要被穿透的端口
# --node=NodeName                    指定要连接的节点名称（需配置 node.json）
# --enable-pp                        启用 Proxy Protocol v2
# --debug                            打印调试信息
# --en-us / --zh-cn                  指定语言
# --nogui                            禁用 GUI 启动
# --gui                              使用 GUI 启动 (默认)
# --disable-tcp                      禁用 TCP 连接
# --disable-udp                      禁用 UDP 连接
```

### ✨ **一键启动 (GUI 模式)**
```bash
# 使用 GUI 并直接指定密钥和端口，实现一键启动
java -jar NeoLink-XXXX.jar --zh-cn --key=你的访问密钥 --local-port=本地端口号
```

### 🛠️ **构建项目**
由于项目已迁移至 Gradle，请使用以下命令构建：
```bash
git clone https://github.com/NeoLinkProxy/NeoLink.git
cd NeoLink

# 开发模式直接运行
./gradlew run

# 打包为当前平台的安装包/执行文件
./gradlew packageDistributionForCurrentOS
```

---

## 📁 配置文件

### 1. 📝 **通用配置** (`config.cfg`)
第一次运行时程序会在当前工作目录创建 `config.cfg`（如果不存在）。

```properties
#把你要连接的 NeoServer 的域名或者公网 ip 放到这里来
REMOTE_DOMAIN_NAME=localhost

#设置是否启用自动更新
ENABLE_AUTO_UPDATE=true

#是否向后端服务透传真实 IP (Proxy Protocol v2)
ENABLE_PROXY_PROTOCOL=false

LOCAL_DOMAIN_NAME=localhost
HOST_HOOK_PORT=44801
HOST_CONNECT_PORT=44802

#代理设置 (示例: socks->127.0.0.1:7890)
PROXY_IP_TO_LOCAL_SERVER=
PROXY_IP_TO_NEO_SERVER=

HEARTBEAT_PACKET_DELAY=1000
ENABLE_AUTO_RECONNECT=true
RECONNECTION_INTERVAL=30
BUFFER_LEN=4096
```

### 2. 🗄️ **多节点配置** (`node.json`)
在程序同级目录下创建 `node.json` 文件，可以配置多个服务器节点。

**文件格式示例：**
```json
[
  {
    "name": "Localhost",
    "address": "localhost",
    "HOST_HOOK_PORT": 44801,
    "HOST_CONNECT_PORT": 44802
  },
  {
    "name": "VIP-Node",
    "address": "vip.example.com",
    "HOST_HOOK_PORT": 55001,
    "HOST_CONNECT_PORT": 55002
  }
]
```

---

## 📈 日志

- 📁 默认输出目录：`./logs/`
- 📄 可使用 `--output-file` 指定日志文件路径
- 🖥️ GUI 模式使用高度优化的日志列表显示，支持颜色解析与复制。

---

## 📞 EULA & 联系方式

程序会在首次运行写出 `eula.txt`，内容包含使用限制与作者联系方式。  
联系方式：QQ群 `304509047` 💬，作者 QQ `1591117599` 📧。

---

## 🤔 常见问题 (FAQ)

Q: 为什么连接不上 NeoProxyServer？  
A: 请检查防火墙端口是否开放，以及 `node.json` 中的端口是否与服务端对应。

Q: 如何获取访问者的真实 IP？  
A: 客户端开启透传功能（`--enable-pp`），并确保后端（如 Nginx）配置了 `proxy_protocol` 监听。

Q: RDP 总是断开连接？  
A: UDP 在弱网下容易丢包，建议使用 `--disable-udp` 强制 RDP 走纯 TCP 模式。

---

## 🔐 许可证

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源发布。

---

## ⚠️ 附件 《最终用户许可协议》 EULA 内容

- 始终以最新的为准，旧的内容自动失效。作者对其任意内容具有最终解释权。<br>
  [eula.txt](eula.txt) 📄