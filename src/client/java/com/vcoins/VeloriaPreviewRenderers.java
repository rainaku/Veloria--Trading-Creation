package com.vcoins;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class VeloriaPreviewRenderers {
    public static EntityRendererProvider.Context context;
    private static VeloriaMerchantRenderer renderer;
    public static final java.util.Set<EntityRenderState> states =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private VeloriaPreviewRenderers() {}
    public static void reset(EntityRendererProvider.Context value) { context = value; renderer = null; states.clear(); }
    public static VeloriaMerchantRenderer get() {
        if (renderer == null && context != null) renderer = new VeloriaMerchantRenderer(context);
        return renderer;
    }
}
