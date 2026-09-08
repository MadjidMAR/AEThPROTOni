<p align="center">
  <img src="https://img.shields.io/badge/AetherST-Tunnel-007AFF?style=for-the-badge&logo=shield&logoColor=white" alt="AetherST Logo" width="200">
</p>

<h1 align="center">AetherST Tunnel</h1>

<p align="center">
  <strong>Advanced, High-Performance Censorship Circumvention Client for Android & Windows</strong>
</p>

<p align="center">
  <a href="https://github.com/immaghzbad/AetherST/releases">
    <img src="https://img.shields.io/github/v/release/immaghzbad/AetherST?style=for-the-badge&color=007AFF" alt="Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Proprietary-orange?style=for-the-badge" alt="License">
  </a>
  <a href="https://github.com/immaghzbad/AetherST/stargazers">
    <img src="https://img.shields.io/github/stars/immaghzbad/AetherST?style=for-the-badge&color=FFD700" alt="Stars">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform Android">
  <img src="https://img.shields.io/badge/Platform-Windows-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Platform Windows">
</p>

---

## 📖 Overview

**AetherST Tunnel** is a production-grade VPN and Proxy client for Android and Windows, meticulously engineered to provide secure and stable connectivity in highly restricted network environments. By combining the power of the **Aether Core** with proven tunnel engines, AetherST offers a robust solution against Deep Packet Inspection (DPI) and protocol-based blocking across multiple platforms.

## 📱 Versions & Platforms

- **Android Client:** `v1.6.9` (Latest Stable)
- **Windows Client:** `v1.1.1` (First Public Release)

## ✨ Features

- 🛡️ **Stealth Connectivity:** Specifically optimized to bypass protocol fingerprinting and DPI.
- 🚀 **Advanced Transports:** Comprehensive support for **MASQUE**, **WireGuard**, **Gool (WG-in-WG)**, and **Cloudflare Zero Trust**.
- 🔗 **Psiphon Chain:** Optional second layer routing traffic via Psiphon for a non-Iran exit IP, chainable over MASQUE, WireGuard, or Gool with Auto, Fallback, and Always modes.
- 📡 **Intelligent Scanning:** Real-time gateway discovery with data-plane validation before connection.
- ⚡ **Native Performance:** Powered by a high-throughput core for low latency and high bandwidth.
- 🖥️ **Multi-Platform UI:** Clean, iOS-inspired dashboard built with **Compose Multiplatform** for a seamless experience on both mobile and desktop.
- 🛠️ **Developer-Ready:** Built-in diagnostics, real-time logging, and flexible protocol presets.

## 🛠️ Supported Protocols

AetherST Tunnel leverages cutting-edge protocols to ensure connectivity even in the most hostile network environments:

### 🎭 MASQUE (HTTP/3 & HTTP/2)
The flagship protocol for stealth. By tunneling traffic over QUIC (H3) or TLS (H2), it makes VPN traffic look like standard web browsing, making it highly resilient to Deep Packet Inspection (DPI).

### 🛡️ WireGuard
A modern, high-performance VPN protocol that uses state-of-the-art cryptography. It is optimized for maximum speed and minimal battery drain on mobile devices.

### 🌀 Gool (Warp-in-Warp / WG-in-WG)
A specialized nested WireGuard configuration. By wrapping one WireGuard tunnel inside another, it provides an additional layer of encryption and obfuscation, effectively bypassing many restrictive firewalls and improving stability.

### ☁️ Cloudflare Zero Trust (Teams)
Enterprise-grade security for individuals and organizations. It allows you to route your traffic through Cloudflare's global network using Gateway filtering and Service Tokens, ensuring zero-trust access control.

### 🔗 Psiphon Chain
An optional second layer built on the open-source Psiphon tunnel core. It routes traffic via Psiphon to obtain a non-Iran exit IP and can chain over MASQUE, WireGuard, or Gool. Chain modes include Auto, Fallback, and Always, with a selectable egress region and local endpoints on `127.0.0.1:3080` (SOCKS) and `127.0.0.1:1820` (HTTP).

---

## 🏗️ Technical Architecture

### [Aether Core (v1.9.0)](https://github.com/CluvexStudio/Aether)
The orchestration layer responsible for:
- Encrypted tunnel management.
- Dynamic gateway health checks.
- Multi-protocol handling (MASQUE, WG).

### [HEV SOCKS5 Tunnel v2.17.1](https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.17.1)
The native bridge between the system and Aether (Android Native):
- Mature user-space TCP/IP stack.
- Zero-copy packet processing.
- Efficient UDP over SOCKS5 translation.

### [Psiphon Tunnel Core v2.0.41](https://github.com/Psiphon-Labs/psiphon-tunnel-core/releases/tag/v2.0.41)
The optional second-layer circumvention engine:
- Open-source Psiphon client core for restricted networks.
- Provides foreign exit IPs with selectable egress region.
- Chains over the Aether transports (MASQUE, WG, Gool).

### Compose Multiplatform UI
A unified UI layer sharing logic between Android and Desktop:
- Reactive state management using Kotlin Flows.
- Shared domain logic for IP lookup and configuration management.
- Native system integrations for each platform.

## 🚀 Getting Started

### Installation
1. Go to the [Releases](https://github.com/immaghzbad/AetherST/releases) page.
2. **Android:** Download the APK compatible with your device architecture (`arm64-v8a` is recommended).
3. **Windows:** Download the `.msi` or `.exe` installer.
4. Install and grant the necessary permissions (VPN on Android).

### Build from Source
- **IDE:** Android Studio Ladybug (2024.2.1) or newer.
- **JDK:** 17
- **NDK:** 30.0.15729638 (for Android native components).
- **Gradle Tasks:**
  - Android: `./gradlew :app:assembleRelease`
  - Desktop: `./gradlew :composeApp:run`

## ⚙️ CI/CD & Security

The project uses **GitHub Actions** for automated Multi-APK and Desktop releases.

## 💬 Community

Stay updated and get support through our official channels:

- 📢 **Telegram:** [PowerSigma](https://t.me/PowerSigma)
- 👨‍💻 **Developer:** [@immaghzbad](https://github.com/immaghzbad)

## 🙏 Credits

This project uses the following open-source resources:

- [flag-icons](https://github.com/lipis/flag-icons) — Country flag icons for multi-language and region UI elements.
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) — Open-source Persian (Farsi) typeface used for RTL language support.
- [Inter](https://github.com/rsms/inter) — Open-source English typeface used for the interface typeface.
- [psiphon-tunnel-core v2.0.41](https://github.com/Psiphon-Labs/psiphon-tunnel-core/releases/tag/v2.0.41) — Open-source Psiphon client core powering the optional Psiphon Chain layer.

---
<p align="center">
  Built with 💙 by <b>PowerSigma Team</b>
</p>
