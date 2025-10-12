# NeoLink

> **内网穿透客户端（NeoLink）** — 使用 Java 21 开发，支持除 HTTPS 以外的所有 TCP 类型内网穿透。
> 推荐的场景：RDP 内网穿透，MC 服务器内网穿透，HTTP FileServer 等等

![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](#许可证)
---

## 说明（概览）

NeoLink 是一个轻量级的内网穿透客户端，用于将本地 TCP 服务（例如 Minecraft 服务器）暴露给公网 NeoServer。项目同时提供命令行与 JavaFX GUI 两种运行模式，并支持通过 HTTP / SOCKS 代理访问本地或远端服务。客户端包含自动重连、心跳检测、日志记录与远程更新下载功能。

> **重点**：本软件仅支持 TCP 内网穿透（作者在 EULA 中声明明确限制）**不支持 HTTPS 隧道** <br>
> EXE 版本使用 Graalvm 构建原生镜像，理论上不需要 Java 环境运行
---

## 要求

- **Java 21**（EXE 版本无需 Java 环境，要求 Win10 1909+）  
- 可选：JavaFX（如果要使用 GUI） — 请确保运行环境包含 JavaFX 或使用带 JavaFX 的打包方式。  
- 网络：能够访问目标 NeoServer（默认端口见配置）。

---
## 启动方式
*   Windows11 支持 EXE 启动（采用 Graalvm 构建，无需 Java 环境）
*   Release 界面提供了环境捆绑版（仅支持 Windows），是电脑小白使用 NeoLink 最方便的选择，避免折腾
*   其他平台仅支持 Java 21 以上的版本启动
---

## **获取客户端:** 从本项目的 "Releases" 页面下载最新的客户端


### 2. 配置文件（`config.cfg`）

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

## 运行

### 命令行模式（Terminal）
将构建后的 JAR（举例 `NeoLink.jar`）放到工作目录并运行：

```bash
# 最常用：指定访问密钥（必需）和本地端口（必需）
java -jar NeoLink.jar --key=YOUR_ACCESS_KEY --local-port=25565

# 可选参数
# --output-file=path/to/logfile.log   将日志写入指定文件
# --debug                             打印调试信息（异常栈）
# --no-color                          关闭 ANSI 颜色输出
# --en-us / --zh-ch                   指定语言
```

运行后程序会：
1. 读取或创建 `config.cfg`
2. 连接远程 NeoServer（配置中 REMOTE_DOMAIN_NAME + HOST_HOOK_PORT）
3. 发送客户端信息并等待服务器响应
4. 验证成功后维持心跳并监听来自服务器的命令（如 `sendSocket` 建立隧道）

如果启用自动重连（`ENABLE_AUTO_RECONNECT=true`），客户端在断线后会按 `RECONNECTION_INTERVAL` 秒重试。

---

### GUI 模式（JavaFX）
JavaFX 环境已集成到程序中

```bash
# 使用 GUI
java -jar NeoLink-XXXX.jar --gui
```
```bash
# 使用 GUI
java -jar NeoLink-XXXX.jar --gui
```

GUI 支持：
- 填写远程地址 / 本地端口 / 访问密钥
- 启动 / 停止服务
- 实时查看日志（内置 WebView 渲染）

---

## 命令行参数（提要）

- `--Key=...`           : 访问密钥（必填）
- `--local-port=...`    : 本地要被穿透的端口（必填）
- `--output-file=...`   : 指定日志输出文件路径
- `--gui`               : 使用 JavaFX GUI 启动
- `--debug`             : 启用调试模式（打印异常细节）
- `--no-color`          : 关闭控制台颜色
- `--en-us` / `--zh-ch` : 强制语言

---

## 日志

- 默认输出目录：`./logs/`（程序会在当前工作目录创建并写入文件）
- 可使用 `--output-file` 指定日志文件路径
- GUI 模式会使用内部队列将日志显示在 WebView 中

---

## EULA & 联系方式

程序会在首次运行写出 `eula.txt`，内容包含使用限制与作者联系方式（QQ 群 / QQ）。请阅读并遵守 EULA 要求。  
联系方式（出现在 EULA）：QQ群 `304509047`，作者 QQ `1591117599`。

---

## 常见问题（FAQ）

Q: 为什么连接不上 NeoServer？  
A:
1. 检查 `config.cfg` 中 `REMOTE_DOMAIN_NAME` 与 `HOST_HOOK_PORT` 是否正确。  
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

## 贡献指南

欢迎通过 Pull Request / Issue 的方式贡献代码或提报 bug。贡献前请注意：
- 代码风格与项目保持一致
- 新增功能请附带简单的使用示例
- 若提交补丁或重构，请在 PR 中说明兼容性影响

---

## 许可证

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源发布。


## 故障排查 & 调试建议

- 启用 `--debug` 获取更多堆栈信息（会写入日志文件）。
- 查看 `logs/` 目录中的最近日志文件以定位问题。
- 若出现“延迟大于 200ms”的提示，请考虑更换更稳定的网络或 NeoServer 节点。

---

## 致谢

感谢使用 NeoLink。若你在使用过程遇到问题或有改进建议，欢迎在仓库中提交 Issue 或通过 EULA 中提供的联系方式联系作者 `Ceroxe`。
