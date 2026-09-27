package com.vcoins;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

/**
 * Custom 3D Veloria Merchant model accurately reflecting the fantasy merchant artwork:
 * authentic villager face, pointed hood with gold trim & star emblem, draped cowl mantle,
 * amber brooch clasp, emerald teal coat with geometric fret hems, purple sash,
 * crossed sleeves with ornate cuffs, satchel, and folded leather boots.
 */
public final class VeloriaMerchantModel extends VillagerModel {

    public VeloriaMerchantModel() {
        super(createRoot());
    }

    public static ModelPart createRoot() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 1. Head & Nose
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -10f, -4f, 8f, 10f, 8f),
                PartPose.ZERO);
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(32, 0).addBox(-1f, -3f, -6f, 2f, 4f, 2f),
                PartPose.ZERO);

        // 2. Hood with peaked crest and gold trim
        PartDefinition hood = head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(0, 18).addBox(-5f, -10.5f, -5f, 10f, 11f, 10f),
                PartPose.ZERO);
        hood.addOrReplaceChild("hat_rim", CubeListBuilder.create(), PartPose.ZERO);
        hood.addOrReplaceChild("peak",
                CubeListBuilder.create().texOffs(40, 18).addBox(-4f, -12.5f, -0.5f, 8f, 3f, 6f),
                PartPose.ZERO);
        hood.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(68, 18).addBox(-3f, -11.5f, 4.5f, 6f, 4f, 3f),
                PartPose.ZERO);

        // 3. Body: Emerald Teal Coat & Accessories
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 53).addBox(-4.5f, 0f, -3f, 9f, 19f, 6f),
                PartPose.ZERO);

        // Shoulder cowl / mantle
        body.addOrReplaceChild("mantle",
                CubeListBuilder.create().texOffs(0, 39).addBox(-6.5f, -0.5f, -4.5f, 13f, 5f, 9f),
                PartPose.ZERO);

        // Golden brooch with amber gem
        body.addOrReplaceChild("clasp",
                CubeListBuilder.create().texOffs(44, 39).addBox(-1.5f, 1.5f, -4.8f, 3f, 3f, 1f),
                PartPose.ZERO);

        // Flowing cape
        body.addOrReplaceChild("cape",
                CubeListBuilder.create().texOffs(30, 53).addBox(-6f, 0.5f, 0f, 12f, 19f, 1f),
                PartPose.offsetAndRotation(0f, 1f, 3.2f, 0.08f, 0f, 0f));

        // Leather belt with gold buckle
        body.addOrReplaceChild("belt",
                CubeListBuilder.create().texOffs(56, 53).addBox(-4.8f, 8f, -3.3f, 9.6f, 2f, 6.6f),
                PartPose.ZERO);

        // Royal purple front sash with golden chevron and star emblem
        body.addOrReplaceChild("front_sash",
                CubeListBuilder.create().texOffs(56, 62).addBox(-2f, 10f, -3.5f, 4f, 8f, 1f),
                PartPose.ZERO);

        // Leather hip satchel
        body.addOrReplaceChild("satchel",
                CubeListBuilder.create().texOffs(66, 62).addBox(-7.5f, 8.5f, -2.5f, 3f, 5f, 4f),
                PartPose.ZERO);

        // 4. Arms (Crossed villager pose)
        PartDefinition arms = root.addOrReplaceChild("arms",
                CubeListBuilder.create(),
                PartPose.offset(0f, 6f, -4.5f));
        arms.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(0, 78).addBox(-6f, -2f, -2f, 5f, 4f, 4f),
                PartPose.ZERO);
        arms.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(18, 78).addBox(1f, -2f, -2f, 5f, 4f, 4f),
                PartPose.ZERO);
        arms.addOrReplaceChild("hands",
                CubeListBuilder.create().texOffs(36, 78).addBox(-1f, -1.5f, -1.8f, 2f, 3f, 3.6f),
                PartPose.ZERO);
        arms.addOrReplaceChild("left_cuff",
                CubeListBuilder.create().texOffs(46, 78).addBox(-6.2f, -2.2f, -2.2f, 3f, 4.4f, 4.4f),
                PartPose.ZERO);
        arms.addOrReplaceChild("right_cuff",
                CubeListBuilder.create().texOffs(60, 78).addBox(3.2f, -2.2f, -2.2f, 3f, 4.4f, 4.4f),
                PartPose.ZERO);

        // 5. Legs & Boots
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 86).addBox(-2f, 0f, -2f, 4f, 10f, 4f),
                PartPose.offset(2f, 12f, 0f));
        leftLeg.addOrReplaceChild("boot",
                CubeListBuilder.create().texOffs(16, 86).addBox(-2.2f, 6.5f, -2.6f, 4.4f, 5.5f, 5.2f),
                PartPose.ZERO);

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(34, 86).addBox(-2f, 0f, -2f, 4f, 10f, 4f),
                PartPose.offset(-2f, 12f, 0f));
        rightLeg.addOrReplaceChild("boot",
                CubeListBuilder.create().texOffs(52, 86).addBox(-2.2f, 6.5f, -2.6f, 4.4f, 5.5f, 5.2f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
    }
}
