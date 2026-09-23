<p align="center"><img src="docs/assets/fnmusic-icon.svg" alt="FnMusic 图标" width="96" height="96"></p>

# FnMusic

**简体中文** · [English](README-en.md)

[![主线构建](https://github.com/seasonyuu/FnMusic/actions/workflows/release.yml/badge.svg?branch=main&event=push)](https://github.com/seasonyuu/FnMusic/actions/workflows/release.yml?query=branch%3Amain)
[![最新版本](https://img.shields.io/github/v/release/seasonyuu/FnMusic?display_name=tag)](https://github.com/seasonyuu/FnMusic/releases/latest)
[![代码许可](https://img.shields.io/github/license/seasonyuu/FnMusic?label=code%20license)](LICENSE-SCOPE.md)

把飞牛音乐库带到 Android，再加上 AirPlay 输出和外部在线歌词搜索。连接自己的 NAS，按喜欢的方式听歌。

FnMusic 是非官方第三方客户端，适用于 Android 8.0 及以上设备。项目仍在开发中，功能和界面可能调整。

## 预览

<p align="center">
  <img src="docs/assets/screenshots/home.png" alt="首页：最近添加、专辑与歌单" width="30%">
  <img src="docs/assets/screenshots/library.png" alt="音乐库：歌曲、歌手、专辑、歌单等分类" width="30%">
  <img src="docs/assets/screenshots/player.png" alt="播放器：封面、播放控制与音频输出" width="30%">
</p>

<p align="center">首页 · 音乐库 · 播放器</p>

## FnMusic 的扩展体验

### AirPlay 输出

在播放器中选择同一局域网内的 AirPlay 接收设备，将 NAS 中的音乐送到其他音频设备。手机仍可控制暂停、切歌、进度和接收端音量，也能切回本机播放。当前已在 macOS 接收端完成播放验证；其他接收设备的兼容性仍待验证。详见 [AirPlay 说明](docs/airplay.md)。

### 外部在线歌词

播放时可自动从网易云音乐、QQ 音乐、酷狗音乐和 AMLL TTML DB 匹配歌词，优先呈现逐词歌词；没有合适结果时回退到音乐库自带歌词。也可以在播放页手动搜索、预览并固定歌词，为单曲调整时间偏移；歌词来源、优先顺序和缓存均可在设置中管理。搜索会向所选来源发送歌曲名和歌手名，不会发送 NAS 凭据。详见[歌词功能](docs/features.md#lyrics)。

## 日常听歌

- **连接自己的音乐库**：通过飞牛账号登录，支持 FN Connect 与自定义 NAS 地址。
- **找到想听的音乐**：从首页进入最近添加、专辑和歌单，也可以按歌曲、歌手、专辑浏览或直接搜索。
- **整理个人收藏**：收藏歌曲，查看最近播放，创建和编辑歌单。
- **持续播放**：支持后台播放、通知和锁屏媒体控制，以及本机音频输出。
- **按喜好调整**：切换明暗主题、调节播放音质与缓存；在手机和大屏设备上使用适配的布局。

## 为什么做 FnMusic？

音乐已经存放在自己的 NAS 上，也希望在手机上方便地把它送到 AirPlay 设备，并为喜欢的歌找到更合适的歌词。FnMusic 围绕这些听歌场景持续完善，同时保留浏览、收藏和歌单管理等日常入口。

## 获取与使用

从[最新 Release](https://github.com/seasonyuu/FnMusic/releases/latest) 下载 `app-release.apk`，发布页也提供 SHA-256 校验文件；或[从源码构建 APK](docs/development.md#build-from-source)。安装后，使用已有的 fnOS 音乐服务账号连接自己的 NAS；Android 最低版本为 8.0。

FnMusic 根据当前飞牛音乐 Web 客户端的行为实现连接，**并非飞牛官方应用，也没有使用官方公开 API**。不同 fnOS 版本的兼容性可能有所差异。

## 项目文档

- [功能与设置](docs/features.md)
- [构建、模块和验证](docs/development.md)
- [协议记录](api.md)
- [测试指南](docs/testing.md)

## 开源协议与声明

FnMusic 原创代码和文档采用 [Apache License 2.0](LICENSE)：允许使用、修改和分发，包括商业用途；须遵守保留许可与版权声明、标注修改等条件，协议也包含专利授权。完整适用范围见[授权范围说明](LICENSE-SCOPE.md)。

飞牛商标、网页素材、字体、音乐封面及含有这些内容的截图**不属于上述授权范围**，第三方组件保留各自的许可证。项目与飞牛官方无关联；素材来源见[开发文档](docs/development.md#assets-and-trademarks)。
