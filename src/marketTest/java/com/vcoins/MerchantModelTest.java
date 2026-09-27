package com.vcoins;

public final class MerchantModelTest {
    public static void main(String[] args) throws Exception {
        TextureGenerator.main(args);
        var root = VeloriaMerchantModel.createRoot();
        var model = new VeloriaMerchantModel();
        for (String name : new String[]{"mantle", "cape", "belt", "satchel", "clasp", "front_sash"}) {
            if (root.getChild("body").getChild(name).isEmpty()) throw new AssertionError(name);
        }
        if (root.getChild("head").getChild("hat").isEmpty()) throw new AssertionError("hood");
        var state = new net.minecraft.client.renderer.entity.state.VillagerRenderState();
        state.yRot = 25; state.xRot = 15;
        model.setupAnim(state);
        if (Math.abs(model.getHead().yRot - (float)Math.toRadians(25)) > 0.001) throw new AssertionError("Head yaw");
        if (Math.abs(model.getHead().xRot - (float)Math.toRadians(15)) > 0.001) throw new AssertionError("Head pitch");
        System.out.println("Custom merchant model baked; all accessories and head tracking verified.");
    }
}
