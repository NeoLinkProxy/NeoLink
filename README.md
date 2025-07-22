# NeoLink - 一款简单易用的高性能Java TCP 内网穿透客户端

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21+-green.svg)](https://www.java.com)

## 🚀 产品介绍

NeoLink 是一款基于 Java 语言开发的轻量级、高性能的内网穿透工具。它致力于帮助开发者和用户轻松地将部署在内网环境的服务暴露到公网上，从而实现远程访问、API调试、本地搭建Web服务器对外演示等功能。
本项目为 NeoLink 的客户端，需要与 NeoProxyServer 服务端配合使用，实现稳定、可靠的 TCP 转发。
这个项目开发的初衷是帮助 Minecraft 玩家更好的联机互动，然而在开发的实际过程中我扩展了使用面，也可以进行 HTTP 转发

## ✨ 产品优势

*   **简单易用:** 无需复杂的配置，只需简单的几步即可启动客户端，建立穿透连接。
*   **轻量高效:** 客户端资源占用低，性能卓越，对系统负担小。
*   **开源免费:** 项目完全开源，您可以自由地进行二次开发和定制。
*   **多隧道并发:** 支持同时建立多条穿透隧道，将内网的多个不同服务同时暴露出去。
*   **高安全性:** 使用 AES256 加密你的通信，保障通信安全。
*   **跨平台运行:** 得益于Java的跨平台特性，客户端可以运行在Windows、Linux、macOS等多种操作系统上。

## 启动方式
*   Windows11 支持 EXE 启动（采用 Graalvm 构建，无需 Java 环境）
*   Release 界面提供了环境捆绑版（仅支持 Windows），是电脑小白使用 NeoLink 最方便的选择，避免折腾
*   其他平台仅支持 Java 21 以上的版本启动

## **获取客户端:** 从本项目的 "Releases" 页面下载最新的客户端


## 🤝 贡献代码

欢迎任何形式的贡献！如果您有任何好的想法或者发现了Bug，请随时提交 `Issue` 或 `Pull Request`。

## 📄 开源许可

本项目采用 [MIT](LICENSE) 开源许可。