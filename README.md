# NeoLink

> **内网穿透客户端（NeoLink）** — 使用 Java 21 开发，支持 TCP 和 UDP 协议的内网穿透。
> 自 4.7.0 版本开始，同步支持 TCP UDP
> 推荐的场景：RDP 内网穿透，MC 服务器内网穿透，HTTP FileServer，以及需要获取真实 IP 的 Web 服务。

![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](#许可证)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?logo=windows&logoColor=white)
![GUI](https://img.shields.io/badge/Interface-JavaFX-blueviolet?logo=javafx)
![Build](https://img.shields.io/badge/Build-GraalVM%20Native-lightblue?logo=graalvm)
![Status](https://img.shields.io/badge/Status-Stable-success?logo=github)
![Made with Love](https://img.shields.io/badge/Made%20with-%E2%9D%A4-red)
---

## 说明（概览）

NeoLink 是一个轻量级的内网穿透客户端，用于将本地 TCP/UDP 服务（例如 Minecraft 服务器）暴露给公网 NeoProxyServer。项目同时提供命令行与 JavaFX GUI 两种运行模式，并支持通过 HTTP/SOCKS 代理访问本地或远端服务。客户端包含自动重连、心跳检测、日志记录与远程更新下载功能。

**新功能**：
1. **多节点切换**：支持通过 `node.json` 配置多个服务端节点，并通过 `--node` 参数一键切换。
2. **Proxy Protocol v2**：支持 PPv2 协议，可将外部访客的真实 IP 透传给后端 Web 服务（如 Nginx）。

> **重点**：请仔细阅读 eula.txt 中声明的限制<br>
>EXE 版本使用 Graalvm 构建原生镜像，理论上不需要 Java 环境运行
---

![图片](/image.png "Magic Gardens")

## ✨ 特性

- **Java 21 驱动**：充分利用现代 Java 特性，性能更优。
- **通用 TCP/UDP 支持**：几乎所有类型的服务均可穿透。
- **多节点管理**：通过 JSON 配置文件管理多个服务器节点，启动时灵活切换。
- **真实 IP 透传**：支持 Proxy Protocol v2 协议，配合 Nginx 等后端可获取用户真实 IP。
- **双模式运行**：命令行（CLI）适合服务器部署，图形界面（GUI）适合新手。
- **自动重连**：连接断开后自动重试，保障服务高可用。
- **代理支持**：支持 HTTP/SOCKS5 代理连接 NeoProxyServer 或本地服务。
- **多语言**：自动识别系统语言（中/英），也可通过参数强制指定。
- **自动更新**：服务端可推送新版本，Windows 下自动下载并重启。
- **日志记录**：所有操作自动记录到 `logs/` 目录。
- **心跳保活**：维持长连接，防止 NAT 超时断开。
- **参数化启动**：支持通过命令行参数直接指定密钥和端口，实现一键启动。

---

## 🚀 快速开始
*   Windows11 支持 EXE 启动（采用 Graalvm 构建，无需 Java 环境）
*   Release 界面提供了环境捆绑版（仅支持 Windows），是电脑小白使用 NeoLink 最方便的选择，避免折腾
*   其他平台仅支持 Java 21 以上的版本启动
*   支持通过命令行参数直接指定密钥和本地端口，实现一键启动

### **获取客户端:** 从本项目的 "Releases" 页面下载最新的客户端

### 命令行模式（Terminal）
将构建后的 JAR（举例 `NeoLink-XXXX.jar`）放到工作目录并运行（强制制定中文）：
```bash
java -jar NeoLink-XXXX.jar --nogui --zh-cn

# 可选参数追加到后面
# --output-file=path/to/logfile.log  将日志写入指定文件
# --key=...                          访问密钥
# --local-port=...                   本地要被穿透的端口
# --node=NodeName                    指定要连接的节点名称（需配置 node.json，如名称含空格请加引号）
# --enable-pp                        启用 Proxy Protocol v2（透传真实 IP，仅限 Web 等支持的后端）
# --debug                            打印调试信息（异常栈）
# --no-color                         关闭 ANSI 颜色输出
# --en-us / --zh-cn                  指定语言
# --nogui                            禁用 JavaFX GUI 启动
# --gui                              使用 JavaFX GUI 启动 （默认启用）
# --disable-tcp                      禁用 TCP 连接
# --disable-udp                      禁用 UDP 连接
```

### 一键启动（GUI模式）
```bash
# 使用 GUI 并直接指定密钥和端口，实现一键启动
java -jar NeoLink-XXXX.jar --zh-cn --key=你的访问密钥 --local-port=本地端口号

# 或者使用 EXE 文件
NeoLink-XXXX.exe --zh-cn --key=你的访问密钥 --local-port=本地端口号
```

### 使用指定节点启动（新功能）
如果你配置了 `node.json`，可以快速连接到指定的服务器节点：
```bash
# 连接到 node.json 中名为 "HK-Server" 的节点
java -jar NeoLink-XXXX.jar --nogui --key=密钥 --local-port=80 --node=HK-Server

# 如果节点名称包含空格，请使用双引号
java -jar NeoLink-XXXX.jar --nogui --key=密钥 --local-port=80 --node="HK Server 1"
```

### 🖥️构建项目
```bash
git clone https://github.com/CeroxeAnivie/PlethoraAPI
mvn install

git clone https://github.com/NeoLinkProxy/NeoLink.git
cd NeoProxyServer
mvn clean package
```

---

## 📁 配置文件

### 1. 通用配置 (`config.cfg`)
第一次运行时程序会在当前工作目录创建 `config.cfg`（如果不存在）。
**注意**：如果你使用了 `--node` 参数，`node.json` 中的配置将覆盖 `config.cfg` 中的 `REMOTE_DOMAIN_NAME` 及端口设置。

```properties
#把你要连接的 NeoServer 的域名或者公网 ip 放到这里来
REMOTE_DOMAIN_NAME=localhost

#设置是否启用自动更新
ENABLE_AUTO_UPDATE=true

#是否向后端服务透传真实 IP (Proxy Protocol v2)
#注意：仅当你的后端服务(如Nginx)配置了 accept_proxy 才可以开启，否则会导致连接失败。
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

### 2. 多节点配置 (`node.json`)
在程序同级目录下创建 `node.json` 文件，可以配置多个服务器节点。启动时使用 `--node=名称` 即可加载对应的地址和端口。

**文件格式示例：**
```json
[
  {
    "name": "Localhost",
    "address": "localhost",
    "hookPort": 44801,
    "connectPort": 44802
  },
  {
    "name": "VIP-Node",
    "address": "vip.example.com",
    "hookPort": 55001,
    "connectPort": 55002
  }
]
```
*如果未找到指定的节点名称，程序将回退使用 `config.cfg` 中的默认配置。*

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
1. 检查配置（`config.cfg` 或 `node.json`）中的地址与端口是否正确。
2. 确认服务器防火墙/云服务安全组已放通对应端口。
3. 若使用代理，检查 `PROXY_IP_TO_NEO_SERVER` 配置是否正确并可达。

Q: 如何使用 HTTPS 协议？<br>
A: 通常有两种方案：
1. **后端 TLS 终止**：在你的后端服务（如 Nginx、Caddy）上部署 **NeoProxyServer 服务器域名** 的 SSL 证书。NeoLink 负责将加密的 TCP 流量透传给后端，由后端进行解密。
2. **CDN TLS 终止**：在 CDN（如 Cloudflare）上开启 HTTPS，设置回源地址为 NeoProxyServer 的域名和端口，并采用 HTTP 回源。

Q: 如何获取访问者的真实 IP？（Proxy Protocol）
A:
1. 客户端开启透传功能（`--enable-pp` 或配置文件）。
2. 后端服务（如 Nginx）配置接收 Proxy Protocol：
   ```nginx
   server {
       listen 80 proxy_protocol; 
       location / {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header X-Real-IP $proxy_protocol_addr;
       }
   }
   ```
   **注意**：SSH/RDP/MC 等服务通常不支持此协议，开启会导致连接失败。

Q: `--node` 参数不起作用？
A: 请确保当前目录下存在 `node.json` 文件，且 JSON 格式正确（包含 `name`, `address`, `hookPort`, `connectPort` 字段），并确保参数中的节点名称与 JSON 中的 `name` 完全一致。

Q: 本地端口无法连接（`Fail to connect to localhost`）？  
A: 确认本地服务（如 Minecraft）已经在 `LOCAL_DOMAIN_NAME:localPort` 上监听，并且程序有权限访问该端口。

Q: RDP 总是断开连接怎么回事？<br>
A: 如果在网络不佳的情况下启用了 UDP ，RDP 协议会识别并且应用。但是 UDP 容易丢包，在这种情况下禁用 UDP 使用纯 TCP 的RDP即可完美解决（使用 `--disable-udp`）。

---

## 🔐许可证

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源发布。


## 🛠️故障排查 & 调试建议

- 启用 `--debug` 获取更多堆栈信息（会写入日志文件）。
- 若指定了 `--node` 但连接地址未变，请检查日志中是否有 "Failed to load node config" 的提示。
- 使用 `--disable-tcp` 或 `--disable-udp` 参数可以分别禁用 TCP 或 UDP 连接，以排查特定协议的问题。

---

## ⚠️附件 《最终用户许可协议》 EULA 内容

- 始终以最新的为准，旧的内容自动失效。作者对其任意内容具有最终解释权。<br>
  [eula.txt](eula.txt)
---