package com.vcoins;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

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
        net.minecraft.resources.ResourceKey<Item> key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
    }

    public static void register() {
        // Just calling this to initialize static fields
    }
}
