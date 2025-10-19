# NeoLink

> **内网穿透客户端（NeoLink）** — 使用 Java 21 开发，支持除 HTTPS 以外的所有 TCP 类型内网穿透。
> 推荐的场景：RDP 内网穿透，MC 服务器内网穿透，HTTP FileServer 等等

![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](#许可证)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?logo=windows&logoColor=white)
![GUI](https://img.shields.io/badge/Interface-JavaFX-blueviolet?logo=javafx)
![Build](https://img.shields.io/badge/Build-GraalVM%20Native-lightblue?logo=graalvm)
![Status](https://img.shields.io/badge/Status-Stable-success?logo=github)
![Made with Love](https://img.shields.io/badge/Made%20with-%E2%9D%A4-red)
---

## 说明（概览）

NeoLink 是一个轻量级的内网穿透客户端，用于将本地 TCP 服务（例如 Minecraft 服务器）暴露给公网 NeoServer。项目同时提供命令行与 JavaFX GUI 两种运行模式，并支持通过 HTTP / SOCKS 代理访问本地或远端服务。客户端包含自动重连、心跳检测、日志记录与远程更新下载功能。

> **重点**：本软件仅支持 TCP 内网穿透（作者在 EULA 中声明明确限制）**不支持 HTTPS 隧道** <br>
> EXE 版本使用 Graalvm 构建原生镜像，理论上不需要 Java 环境运行
---

![图片](/image.jpg "Magic Gardens")

## ✨ 特性

- **Java 21 驱动**：充分利用现代 Java 特性，性能更优。
- **通用 TCP 支持**：几乎所有类型的 TCP 服务均可穿透。
- **双模式运行**：命令行（CLI）适合服务器部署，图形界面（GUI）适合新手。
- **自动重连**：连接断开后自动重试，保障服务高可用。
- **代理支持**：支持 HTTP / SOCKS5 代理连接 NeoProxyServer 或本地服务。
- **多语言**：自动识别系统语言（中/英），也可通过参数强制指定。
- **自动更新**：服务端可推送新版本，Windows 下自动下载并重启。
- **日志记录**：所有操作自动记录到 `logs/` 目录。
- **心跳保活**：维持长连接，防止 NAT 超时断开。

---

## 🚀 快速开始
*   Windows11 支持 EXE 启动（采用 Graalvm 构建，无需 Java 环境）
*   Release 界面提供了环境捆绑版（仅支持 Windows），是电脑小白使用 NeoLink 最方便的选择，避免折腾
*   其他平台仅支持 Java 21 以上的版本启动
## **获取客户端:** 从本项目的 "Releases" 页面下载最新的客户端

### 命令行模式（Terminal）
将构建后的 JAR（举例 `NeoLink-XXXX.jar`）放到工作目录并运行：

```bash
java -jar NeoLink-XXXX.jar

# 可选参数追加到后面
# --output-file=path/to/logfile.log   将日志写入指定文件
# --key=...                           访问密钥（必填）
# `--local-port=...                   本地要被穿透的端口（必填）
# --debug                             打印调试信息（异常栈）
# --no-color                          关闭 ANSI 颜色输出
# --en-us / --zh-ch                   指定语言
# `--nogui`                           禁用 JavaFX GUI 启动
# `--gui`                             使用 JavaFX GUI 启动 （默认启用）
```
或者
```bash
# 使用 GUI
NeoLink-XXXX.exe
```

### 📁配置文件（`config.cfg`）

第一次运行时程序会在当前工作目录创建 `config.cfg`（如果不存在）。默认内容如下（也可直接在仓库中保存此文件）：

```
#把你要连接的NeoServer的域名或者公网ip放到这里来
#Put the domain name or public network ip of the NeoServer you want to connect to here
REMOTE_DOMAIN_NAME=127.0.0.1

#如果你不知道以下的设置意味着什么，请你不要改变它
#If you don't know what the following setting means, please don't change it
LOCAL_DOMAIN_NAME=localhost
HOST_HOOK_PORT=801
HOST_CONNECT_PORT=802

#设置用来连接本地服务器的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456   如果不需要去请留空
#Set the proxy server IP address and port to connect to the on-premises server,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
PROXY_IP_TO_LOCAL_SERVER=

#设置用来连接 NeoProxyServer 的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456
#Set the proxy server IP address and port to connect to the NeoProxyServer,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
PROXY_IP_TO_NEO_SERVER=

#设置发送心跳包的间隔，单位为毫秒
#Set the interval for sending heartbeat packets, in milliseconds
HEARTBEAT_PACKET_DELAY=1000

#是否启用自动重连当服务端暂时离线的时候
#Whether to enable automatic reconnection when the server is temporarily offline
ENABLE_AUTO_RECONNECT=true

#如果ENABLE_AUTO_RECONNECT设置为true，则将间隔多少秒后重连，单位为秒，且必须为大于0的整数
#If ENABLE_AUTO_RECONNECT is set to true, the number of seconds after which reconnection will be made in seconds and must be an integer greater than 0
RECONNECTION_INTERVAL=30

#数据包数组的长度
#The length of the packet array
BUFFER_LEN=4096
```

#### 代理字段格式说明
- 支持 2 种代理类型前缀：`socks` 或 `http`（不区分大小写）。示例：
  - `socks->127.0.0.1:7890`（无认证）
  - `http->10.10.10.1:8080@user;pass`（带认证）
- `PROXY_IP_TO_LOCAL_SERVER`：当访问本地服务（localDomainName/localPort）时，先走这个代理（可为空）
- `PROXY_IP_TO_NEO_SERVER`：当访问 NeoServer 时，走此代理（可为空）

---

## 📜日志

- 默认输出目录：`./logs/`（程序会在当前工作目录创建并写入文件）
- 可使用 `--output-file` 指定日志文件路径
- GUI 模式会使用内部队列将日志显示在 WebView 中

---

## 📞EULA & 联系方式

程序会在首次运行写出 `eula.txt`，内容包含使用限制与作者联系方式（QQ 群 / QQ）。请阅读并遵守 EULA 要求。  
联系方式（出现在 EULA）：QQ群 `304509047`，作者 QQ `1591117599`。

---

## ❓常见问题（FAQ）

Q: 为什么连接不上 NeoProxyServer？  
A:
1. 检查 `config.cfg` 中 `REMOTE_DOMAIN_NAME` 与 `HOST_HOOK_PORT` 和 `HOST_CONNECT_PORT` 是否正确。  
2. 确认服务器防火墙/云服务安全组已放通对应端口。  
3. 若使用代理，检查 `PROXY_IP_TO_NEO_SERVER` 配置是否正确并可达。  
4. 使用 `--debug` 获取更多异常栈信息。

Q: 本地端口无法连接（`Fail to connect to localhost`）？  
A: 确认本地服务（如 Minecraft）已经在 `LOCAL_DOMAIN_NAME:localPort` 上监听，并且程序有权限访问该端口。

Q: 如何关闭自动重连？  
A: 在 `config.cfg` 中将 `ENABLE_AUTO_RECONNECT=false`。

Q: GUI 启动但无法显示日志或乱码？  
A: GUI 使用 WebView 渲染日志，程序已经做了中文编码/ANSI 转换的处理；如仍异常，请检查 JavaFX 版本与系统环境编码设置。

---

## 🔐许可证

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源发布。


## 🛠️故障排查 & 调试建议

- 启用 `--debug` 获取更多堆栈信息（会写入日志文件）。
- 查看 `logs/` 目录中的最近日志文件以定位问题。
- 若出现“延迟大于 200ms”的提示，请考虑更换更稳定的网络或 NeoServer 节点。

---
