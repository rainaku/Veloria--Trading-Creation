package com.vcoins;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

public final class VeloriaMerchantRenderer extends WanderingTraderRenderer {
    public VeloriaMerchantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new VeloriaMerchantModel();
        this.layers.clear();
    }
    @Override public Identifier getTextureLocation(VillagerRenderState state) {
        return Identifier.fromNamespaceAndPath("vcoins", "textures/entity/veloria_materials.png");
    }
}
