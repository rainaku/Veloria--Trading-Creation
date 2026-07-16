# Veloria: Trading & Creation

[![Build](https://github.com/rainaku/Veloria--Trading-Creation/actions/workflows/build.yml/badge.svg)](https://github.com/rainaku/Veloria--Trading-Creation/actions/workflows/build.yml)
![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62b47a)
![Java 21](https://img.shields.io/badge/Java-21-e76f00)
[![License: CC0 1.0](https://img.shields.io/badge/License-CC0%201.0-lightgrey.svg)](LICENSE)

Veloria is a Fabric economy mod for Minecraft. It adds a searchable item market, the Velicoin currency, buyback, and high-cost item duplication through an interface designed to feel at home in vanilla Minecraft.

## Features

- Browse survival-obtainable vanilla and modded items in a categorized, searchable catalogue.
- Buy one item or a full stack, and sell individual inventory stacks or everything tradeable at once.
- Recover recently sold items through buyback at a small markup.
- Duplicate an exact item while preserving enchantments, durability, custom names, and other data components.
- Scale duplication costs with item value and attached properties.
- Block Creative-only items from trading and duplication.
- Receive sound and action-bar feedback for each transaction.
- Open the shop with the configurable `B` keybind or `/shop`.
- Use English by default, with Vietnamese loaded automatically when Minecraft is set to Vietnamese.

## Requirements

| Dependency | Supported version |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.141.4+1.21.11 or compatible |
| Java | 21 or newer |

Veloria must be installed on the server and on every connecting client.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api).
3. Place the Veloria JAR in the `mods` folder on both the client and server.
4. Start Minecraft and configure the **Veloria** keybind if desired.

## Usage

Open the market with `B` or `/shop`.

### Trading controls

| Action | Control |
| --- | --- |
| Buy one item | Left-click a shop item |
| Buy a full stack | Right-click or Shift-click a shop item |
| Sell an inventory stack | Shift-click the stack |
| Sell the held cursor stack | Click a shop slot while holding the stack |
| Scroll the catalogue | Mouse wheel or scrollbar |
| Recover a sold stack | Open **Buyback** and select the item |

Tools and other non-stackable items are limited to one item per purchase. Middle-click never performs a transaction.

### Item duplication

Select **Duplicate** from the market, then place a sample item in the left slot. The preview shows the exact item that will be created and the transaction cost. The sample is not consumed and is returned when the screen closes.

Duplication starts at **500,000 Velicoins and 30 XP levels**. Rare items, enchantments, custom names, and additional data components increase the cost. Creative-only items cannot be duplicated.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shop` | Open the market | Everyone |
| `/sell hand` | Sell the full stack in the main hand | Everyone |
| `/sell all` | Sell all tradeable inventory stacks | Everyone |
| `/vcoins get <player>` | View a player's balance | Operator |
| `/vcoins set <players> <amount>` | Set one or more balances | Operator |
| `/vcoins add <players> <amount>` | Add Velicoins to one or more balances | Operator |

## Building from source

Install JDK 21, clone the repository, and run:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The distributable JAR is written to `build/libs/`. For local development, use `./gradlew runClient` to launch the Fabric test client.

## Project structure

```text
src/main/java/       Shared and server-side economy logic
src/client/java/     Client screens, input, and rendering
src/main/resources/  Fabric metadata, translations, models, and textures
```

## Saved data

Balances are saved as `vcoins.json` in each world folder. The internal mod ID remains `vcoins` to preserve compatibility with existing world data.

## License

Veloria is dedicated to the public domain under [CC0 1.0 Universal](LICENSE).
