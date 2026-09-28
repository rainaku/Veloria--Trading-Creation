# Veloria: Trading & Creation

[![Build](https://github.com/rainaku/Veloria--Trading-Creation/actions/workflows/build.yml/badge.svg)](https://github.com/rainaku/Veloria--Trading-Creation/actions/workflows/build.yml)
![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62b47a)
![Java 25](https://img.shields.io/badge/Java-25-e76f00)
[![License: CC0 1.0](https://img.shields.io/badge/License-CC0%201.0-lightgrey.svg)](LICENSE)

Veloria is a Fabric economy mod for Minecraft. It adds a searchable item market, the Velicoin currency, buyback, and high-cost item duplication through an interface designed to feel at home in vanilla Minecraft.

## Features

- Browse survival-obtainable vanilla and modded items in a categorized, searchable catalogue.
- Price rare items, enchanted equipment, and enchanted books by rarity, enchantment count, and level.
- Buy one item or a full stack, and sell individual inventory stacks or everything tradeable at once.
- Recover recently sold items at their original sale payout plus a 10% markup.
- Duplicate an exact item while preserving enchantments, durability, custom names, and other data components.
- Scale duplication costs with item value and attached properties.
- Block Creative-only items from trading and duplication.
- Receive sound and action-bar feedback for each transaction.
- Open the shop with the configurable `B` keybind or `/shop`.
- Use English by default, with Vietnamese loaded automatically when Minecraft is set to Vietnamese.

## Requirements

| Dependency | Supported version |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0+26.3 or compatible |
| Java | 25 or newer |

Veloria must be installed on the server and on every connecting client.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3.
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

Duplication starts at **150,000 Velicoins and 30 XP levels**. Rare items, enchantments, custom names, and additional data components increase the cost. Creative-only items cannot be duplicated.

Filled shulker boxes, bundles, loaded crossbows, and items carrying opaque entity,
block-entity or loot payloads cannot be duplicated. Empty containers remain eligible.

The shop buys enchanted equipment for its underlying material value, adjusted for
durability. Enchanted books sell as ordinary books. Enchantment premiums apply only
to purchase prices, so enchanting or combining books cannot mint resale profit.
Buyback prices are fixed when an item is sold and do not fall with market pressure;
buyback always recovers the entire sold stack. History lasts for the server session
and is cleared when changing worlds.

## Commands

### Fortuna's Pact

Open **Fortuna** from the shop toolbar or run `/fortuna`. Choose Anchor, Foresight,
or Reroll, then pay the entry fee displayed before starting. Claim the current reward at any time,
or risk the next gate at 85%, 65%, 45%, and 25% success respectively.

Each charm can be used once per pact. Anchor protects the current reward against a
later failure; Foresight reveals the reward two gates ahead; Reroll changes the next
reward to another item in the same tier. Failure grants 2/4/6/8 fragments depending
on the attempted gate. Between pacts, redeem 80 fragments for a chosen Fortuna
diamond pickaxe, sword, or boots. All rewards have normal durability. The final gate
offers diamond equipment with a level-I enchantment; earlier equipment is unenchanted.
Elytra, mace, trident, Netherite, and unbreakable equipment are excluded.

The entry fee is quoted by the server when opening the menu, with a 100,000-coin
minimum. Its conservative budget sums the maximum unbound item value at each tier
weighted by the probability of reaching it, adds the maximum fragment redemption
allowance, and divides by 0.65. This includes the benefit of adaptive stopping and
Anchor, and values items before the direct-sale restriction. The quote uses the
higher of reference and current shop prices and is rounded up to 1,000 coins.

Fortuna rewards cannot be sold directly to the shop or duplicated. These restrictions
apply to the original marked items; vanilla crafting transformations are not blocked.
The reward pools and fee model still need economy playtesting. Existing items already
awarded by the earlier implementation are not modified retroactively.

Pacts and fragments persist with the player's inventory and balance. Closing the menu
does not settle or reset a pact. The menu includes custom artwork, card flips, coin
animation, result particles, and vanilla sound cues. **Skip FX** skips the current
animation; the existing reduced-motion setting disables motion.

| Command | Description | Permission |
| --- | --- | --- |
| `/shop` | Open the market | Everyone |
| `/fortuna` | Open or resume Fortuna's Pact | Everyone |
| `/sell hand` | Sell the full stack in the main hand | Everyone |
| `/sell all` | Sell all tradeable inventory stacks | Everyone |
| `/vcoins get <player>` | View a player's balance | Operator |
| `/vcoins set <players> <amount>` | Set one or more balances | Operator |
| `/vcoins add <players> <amount>` | Add Velicoins to one or more balances | Operator |

## Building from source

Install JDK 25, clone the repository, and run:

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

Balances (`veloria:coins`) and Fortuna state (`veloria:fortuna`) are saved inside
vanilla player data alongside inventory and XP. Economic transactions checkpoint
that player after applying the payment and item changes. Autosaves and logout use
the same snapshot, preventing a coin-only rollback after a crash. Client balance
displays are isolated from authoritative server balances.

Existing `vcoins.json` and `fortuna.json` files are read as migration inputs; player
data takes precedence once saved, including a zero balance. The legacy files are
retained without being overwritten. Back up the whole world before downgrading:
older mod versions cannot read the new balance tags and would use stale JSON.
This protects the mod's player transactions; it does not make vanilla chunk saves,
item drops or transfers between different players globally transactional.

## License

Veloria is dedicated to the public domain under [CC0 1.0 Universal](LICENSE).
