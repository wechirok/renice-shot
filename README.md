<img align="left" src="https://cdn.modrinth.com/data/ZDCwiIc3/c6a7ece0167f8f4f4703a9468b125321a47d34bd.png" width="160" alt="Renice Shot logo">

<p><strong><font size="6">Renice Shot</font></strong></p>

A lightweight client-side Fabric mod for taking high-resolution screenshots.

It keeps the familiar [Fabrishot](https://github.com/ramidzkh/fabrishot) workflow while offering faster support for new versions, more reliable screenshot capture, and fixes for some Fabrishot issues.

## Available on [![MODRINTH](https://img.shields.io/badge/MODRINTH-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white&labelColor=000000)](https://modrinth.com/mod/renice-shot) [![CURSEFORGE](https://img.shields.io/badge/CURSEFORGE-F16436?style=for-the-badge&logo=curseforge&logoColor=white&labelColor=000000)](https://www.curseforge.com/minecraft/mc-mods/renice-shot)

---

<br clear="left"/>

## Screenshot controls

Press **F9** to take a screenshot.

The game is temporarily rendered at the configured resolution, the screenshot is captured, and the original resolution is restored automatically.

By default, screenshots are captured at 3840 × 2160 (4k).

## Features

- Optional override of normal screenshots
- Custom screenshot width and height
- PNG, JPG, TGA and BMP output
- Optional HUD hiding during capture
- Configurable capture delay
- Custom screenshot file names with `%time%` and `%world%` variables
- Automatic restoration of resolution and HUD state

Screenshots are saved to Minecraft's default `screenshots` folder with the `huge_` prefix by default.

## Improvements

- Faster support for new versions
- Optional override of screenshots requested by other mods through screenshot API
- If several screenshots are requested at once, they are taken in order instead of being skipped
- If a screenshot fails, your resolution and HUD are restored, unfinished files are removed, and the error is shown in-game and logged
- Screenshot files are saved more reliably, including when a filename is already taken or writing fails

## Configuration

With [Mod Menu](https://github.com/TerraformersMC/ModMenu) and [Cloth Config](https://github.com/shedaniel/cloth-config) installed, Renice Shot can be configured directly in-game. Both are optional.

Without them, settings can be changed manually in: `config/renice-shot.properties`

The maximum screenshot resolution depends on the limits supported by your graphics hardware.
