# 🛸 openprop

<div align="center">

![Platform](https://img.shields.io/badge/Platform-Android_Native-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white)
![Build](https://img.shields.io/badge/Build-AAPT2_%2B_D8-0284C7)
![License](https://img.shields.io/badge/License-MIT-green)
![Author](https://img.shields.io/badge/Developer-dragon8957-181717?logo=github&logoColor=white)
![Telegram](https://img.shields.io/badge/Telegram-@mubava-2AABEE?logo=telegram&logoColor=white)

**An ultra-smooth, lightweight native Android arcade game built entirely from scratch with pure Java, hardware-accelerated Canvas, procedural real-time synth audio, and zero bloated third-party engines.**

[English](#-about-the-game) | [Русский](#-об-игре)

</div>

---

## 🎮 About the Game

**openprop** is a high-octane cybernetic infinite descent arcade game. Control an agile cyber capsule descending into the endless abyss, dodge hazardous titanium platforms and crystalline plasma spikes, collect energy diamonds, and push your depth record to the absolute limit!

### 🌟 Key Features

- ⚡ **Pure Native Android Engine**: 120 FPS high-refresh rate support via hardware-accelerated `SurfaceView` with zero garbage-collection stutter.
- 🎵 **Dynamic Procedural Audio Engine**: Built-in 3-track real-time synthesizer (`AudioTrack` PCM generation) creating dynamic Cyberpunk, Chillwave, and Deep Space synth tracks on the fly with zero external audio assets!
- 🛡️ **Tactical Powerups**:
  - 💎 **Diamonds**: Score multiplier (+100 Depth).
  - 🛡️ **Shields**: Protects against one fatal collision with custom fracture FX.
  - ⏳ **Hourglass (Slow-Mo)**: Dilates time and creates motion-blur ghost trails.
- ✍️ **Custom Modern Typography**: Powered by the geometric `Comfortaa` font for both Latin and Cyrillic.
- ⚙️ **Deep Customization Settings**:
  - Language toggle (**English** / **Русский**).
  - Dual control modes (Fluid finger drag follow vs. Left/Right tap zones).
  - Sound & Haptics toggles with Android Vibration API.
  - Dynamic Fall Speed Acceleration toggle.
  - Screen shake & dust particle options.
- 📱 **Edge-to-Edge Display**: Full immersive cutout support (no system bars, 100% full-bleed display).

---

## 🇷🇺 Об игре

**openprop** — это высокоскоростная аркада бесконечного спуска в стиле киберпанк. Управляйте маневренной капсулой, уворачивайтесь от титановых платформ и плазменных шипов, собирайте алмазы и бейте рекорды глубины!

### 🚀 Особенности:
- **100% Native Java & Android API** — игра весит меньше 1 МБ и летает на 120 FPS.
- **Встроенный процедурный синтезатор музыки** — 3 крутых трека генерируются в реальном времени.
- **Чистый геймплей** — только понятные и сбалансированные бонусы (Алмазы, Щит, Слоумо).
- **Стильный интерфейс и рукописный дизайн** — красивый шрифт Comfortaa и полная русская локализация.

---

## 🛠️ Project Structure

```text
openprop/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/openprop/game/
│       │   ├── MainActivity.java      # Edge-to-edge full bleed & lifecycle hooks
│       │   ├── GameView.java          # Core game loop, Canvas rendering, physics & UI
│       │   ├── SoundManager.java      # Procedural real-time multi-track synth engine
│       │   ├── VibrationManager.java  # Tactile haptic feedback manager
│       │   └── Localization.java      # Multilingual translation system (EN/RU)
│       └── res/
│           ├── font/game_font.ttf     # Comfortaa clean gaming typeface
│           ├── drawable/              # App icon & Developer avatar
│           └── mipmap-*/              # Adaptive launcher icons
├── build_apk.ps1                      # Direct lightning-fast build script (AAPT2 + javac + D8)
└── README.md
```

---

## 🏗️ How to Build from Source

### Prerequisites:
- JDK 17+ (e.g. Eclipse Adoptium OpenJDK 17)
- Android SDK Build-Tools (AAPT2, D8, android.jar)

### Direct PowerShell Build:
```powershell
# Clone the repository
git clone https://github.com/Dragon8957/OpenDrop.git
cd OpenDrop

# Compile and package the signed APK in 5 seconds
.\build_apk.ps1
```
The output APK will be generated at `bin/openprop.apk`.

---

## 👨‍💻 Developer & Contacts

- **GitHub**: [@dragon8957](https://github.com/dragon8957)
- **Telegram**: [@mubava](https://t.me/mubava)

---

## 📜 License

This project is licensed under the **MIT License** — feel free to modify, fork, and play!
