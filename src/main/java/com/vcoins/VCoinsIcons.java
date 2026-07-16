package com.vcoins;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class VCoinsIcons {

    public static final Item ICON_ALL = registerIcon("icon_all");
    public static final Item ICON_BUILDING = registerIcon("icon_building");
    public static final Item ICON_COMBAT = registerIcon("icon_combat");
    public static final Item ICON_TOOLS = registerIcon("icon_tools");
    public static final Item ICON_FOOD = registerIcon("icon_food");
    public static final Item ICON_REDSTONE = registerIcon("icon_redstone");
    public static final Item ICON_MISC = registerIcon("icon_misc");
    public static final Item ICON_BUYBACK = registerIcon("icon_buyback");

    private static Item registerIcon(String name) {
        net.minecraft.registry.RegistryKey<Item> key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, Identifier.of(VCoinsMod.MOD_ID, name));
        return Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key).maxCount(1)));
    }

    public static void register() {
        // Just calling this to initialize static fields
    }
}
