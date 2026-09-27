package com.vcoins;

import java.io.File;
import javax.imageio.ImageIO;

public final class MerchantModelTest {
    public static void main(String[] args) throws Exception {
        TextureGenerator.main(args);

        // Verify texture file size and 256x256 dimensions
        File texFile = new File("src/main/resources/assets/vcoins/textures/entity/veloria_materials.png");
        if (!texFile.exists()) throw new AssertionError("Texture file was not generated");
        var img = ImageIO.read(texFile);
        if (img.getWidth() != 256 || img.getHeight() != 256) {
            throw new AssertionError("Expected 256x256 texture, got: " + img.getWidth() + "x" + img.getHeight());
        }

        var merchantImg = ImageIO.read(new File("src/main/resources/assets/vcoins/textures/gui/veloria_merchant.png"));
        System.out.println("gui veloria_merchant.png: " + merchantImg.getWidth() + "x" + merchantImg.getHeight()
                + " hasAlpha=" + merchantImg.getColorModel().hasAlpha());
        var root = VeloriaMerchantModel.createRoot();
        var model = new VeloriaMerchantModel();

        // Verify all sculpted accessories in body hierarchy
        for (String name : new String[]{"mantle", "cape_upper", "cape_lower", "belt", "belt_buckle", "satchel", "brooch_outer", "brooch_gem", "front_sash"}) {
            if (!root.getChild("body").hasChild(name)) throw new AssertionError("Missing body part: " + name);
        }

        // Verify modular hood assembly
        var head = root.getChild("head");
        if (!head.hasChild("nose")) throw new AssertionError("Missing nose");
        if (!head.hasChild("hat")) throw new AssertionError("Missing hat");
        var hat = head.getChild("hat");
        for (String hoodPart : new String[]{"hood_top", "hood_left", "hood_right", "hood_back", "hood_crown", "hood_tail", "hood_trim_left", "hood_trim_right", "hood_trim_top", "hood_star"}) {
            if (!hat.hasChild(hoodPart)) throw new AssertionError("Missing hood part: " + hoodPart);
        }

        // Verify arms and crossed forearms
        var arms = root.getChild("arms");
        if (!arms.hasChild("left_sleeve") || !arms.hasChild("right_sleeve") || !arms.hasChild("crossed_forearms")) {
            throw new AssertionError("Missing arm parts");
        }

        // Verify legs and boots
        if (!root.getChild("right_leg").hasChild("boot") || !root.getChild("left_leg").hasChild("boot")) {
            throw new AssertionError("Missing boots");
        }

        // Verify real-time head/hood cursor tracking in 3D
        var state = new net.minecraft.client.renderer.entity.state.VillagerRenderState();
        state.yRot = 25;
        state.xRot = 15;
        model.setupAnim(state);
        if (Math.abs(model.getHead().yRot - (float) Math.toRadians(25)) > 0.001) throw new AssertionError("Head yaw tracking");
        if (Math.abs(model.getHead().xRot - (float) Math.toRadians(15)) > 0.001) throw new AssertionError("Head pitch tracking");

        System.out.println("Custom merchant model baked; 256x256 texture and 3D head cursor tracking verified.");
    }
}
