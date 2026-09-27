package com.vcoins;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

public final class VeloriaMerchantModel extends VillagerModel {

    private static final float PI = (float) Math.PI;

    /*
     * Có constructor ModelPart để dùng chuẩn với context.bakeLayer().
     */
    public VeloriaMerchantModel(ModelPart root) {
        super(root);
    }

    /*
     * Giữ constructor rỗng nếu renderer hiện tại của mày đang:
     *
     * new VeloriaMerchantModel()
     *
     * Tuy nhiên production thì nên bake qua ModelLayerLocation.
     */
    public VeloriaMerchantModel() {
        this(createBodyLayer().bakeRoot());
    }

    public static ModelPart createRoot() {
        return createBodyLayer().bakeRoot();
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        createHead(root);
        createBody(root);
        createArms(root);
        createLegs(root);

        /*
         * 256x256 vì model này có khá nhiều vùng texture riêng.
         */
        return LayerDefinition.create(mesh, 256, 256);
    }

    // -------------------------------------------------------------------------
    // HEAD
    // -------------------------------------------------------------------------

    private static void createHead(PartDefinition root) {

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F, -10.0F, -4.0F,
                                8.0F, 10.0F, 8.0F
                        ),
                PartPose.ZERO
        );

        /*
         * Villager nose.
         *
         * Cho dài / dày hơn vanilla một chút để đúng hình reference.
         */
        head.addOrReplaceChild(
                "nose",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(
                                -1.25F, -1.0F, -2.5F,
                                2.5F, 4.75F, 2.5F
                        ),
                PartPose.offset(0.0F, -2.3F, -4.0F)
        );

        /*
         * QUAN TRỌNG:
         *
         * Không làm hood thành một cube 10x11x10.
         * Hat chỉ là parent, sau đó dựng từng tấm vải riêng.
         *
         * Như vậy mặt không bị "đóng hộp".
         */
        PartDefinition hat = head.addOrReplaceChild(
                "hat",
                CubeListBuilder.create(),
                PartPose.ZERO
        );

        /*
         * VillagerModel mong có hat_rim.
         * Có thể để geometry rỗng.
         */
        hat.addOrReplaceChild(
                "hat_rim",
                CubeListBuilder.create(),
                PartPose.ZERO
        );

        // ---------------------------------------------------------------------
        // PURPLE HOOD
        // ---------------------------------------------------------------------

        // Đỉnh hood
        hat.addOrReplaceChild(
                "hood_top",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(
                                -4.75F, -1.4F, -4.65F,
                                9.5F, 2.4F, 9.3F,
                                new CubeDeformation(0.08F)
                        ),
                PartPose.offset(0.0F, -9.8F, 0.0F)
        );

        // Thành trái hood
        hat.addOrReplaceChild(
                "hood_left",
                CubeListBuilder.create()
                        .texOffs(40, 32)
                        .addBox(
                                -1.5F, -4.8F, -4.6F,
                                1.8F, 9.6F, 8.7F
                        ),
                PartPose.offset(-4.1F, -4.8F, 0.15F)
        );

        // Thành phải hood
        hat.addOrReplaceChild(
                "hood_right",
                CubeListBuilder.create()
                        .texOffs(64, 32)
                        .addBox(
                                -0.3F, -4.8F, -4.6F,
                                1.8F, 9.6F, 8.7F
                        ),
                PartPose.offset(4.1F, -4.8F, 0.15F)
        );

        // Sau đầu
        hat.addOrReplaceChild(
                "hood_back",
                CubeListBuilder.create()
                        .texOffs(88, 32)
                        .addBox(
                                -4.8F, -9.8F, -0.8F,
                                9.6F, 10.0F, 1.6F
                        ),
                PartPose.offset(0.0F, 0.0F, 4.0F)
        );

        /*
         * Phần hood phình ra phía sau/trái giống reference.
         */
        hat.addOrReplaceChild(
                "hood_crown",
                CubeListBuilder.create()
                        .texOffs(0, 48)
                        .addBox(
                                -4.6F, -2.0F, -1.5F,
                                8.2F, 4.0F, 6.0F
                        ),
                PartPose.offsetAndRotation(
                        -0.6F,
                        -10.0F,
                        2.0F,
                        -0.08F,
                        0.0F,
                        -0.06F
                )
        );

        /*
         * Đuôi khăn phía sau.
         */
        hat.addOrReplaceChild(
                "hood_tail",
                CubeListBuilder.create()
                        .texOffs(32, 48)
                        .addBox(
                                -3.8F, -1.0F, -1.0F,
                                7.6F, 9.0F, 2.0F
                        ),
                PartPose.offsetAndRotation(
                        -1.2F,
                        -5.0F,
                        4.6F,
                        0.18F,
                        0.0F,
                        0.12F
                )
        );

        // ---------------------------------------------------------------------
        // GOLD HOOD TRIM
        // ---------------------------------------------------------------------

        // Viền trái trước mặt
        hat.addOrReplaceChild(
                "hood_trim_left",
                CubeListBuilder.create()
                        .texOffs(112, 0)
                        .addBox(
                                -0.45F, -4.8F, -0.35F,
                                0.9F, 9.7F, 0.7F
                        ),
                PartPose.offsetAndRotation(
                        -4.18F,
                        -4.65F,
                        -4.55F,
                        0.0F,
                        0.0F,
                        -0.05F
                )
        );

        // Viền phải
        hat.addOrReplaceChild(
                "hood_trim_right",
                CubeListBuilder.create()
                        .texOffs(116, 0)
                        .addBox(
                                -0.45F, -4.8F, -0.35F,
                                0.9F, 9.7F, 0.7F
                        ),
                PartPose.offsetAndRotation(
                        4.18F,
                        -4.65F,
                        -4.55F,
                        0.0F,
                        0.0F,
                        0.05F
                )
        );

        // Viền ngang trên trán
        hat.addOrReplaceChild(
                "hood_trim_top",
                CubeListBuilder.create()
                        .texOffs(120, 0)
                        .addBox(
                                -4.0F, -0.45F, -0.35F,
                                8.0F, 0.9F, 0.7F
                        ),
                PartPose.offset(
                        0.0F,
                        -9.55F,
                        -4.6F
                )
        );

        /*
         * Những miếng trim bậc thang phía trên giúp hood
         * nhìn giống Minecraft artwork hơn thay vì một line thẳng.
         */
        hat.addOrReplaceChild(
                "hood_trim_step_1",
                CubeListBuilder.create()
                        .texOffs(136, 0)
                        .addBox(
                                -1.0F, -0.5F, -0.35F,
                                2.0F, 1.0F, 0.7F
                        ),
                PartPose.offset(
                        -3.2F,
                        -10.4F,
                        -3.5F
                )
        );

        hat.addOrReplaceChild(
                "hood_trim_step_2",
                CubeListBuilder.create()
                        .texOffs(142, 0)
                        .addBox(
                                -1.0F, -0.5F, -0.35F,
                                2.0F, 1.0F, 0.7F
                        ),
                PartPose.offset(
                        -2.0F,
                        -11.0F,
                        -2.0F
                )
        );

        // ---------------------------------------------------------------------
        // GOLD STAR ON HOOD
        // ---------------------------------------------------------------------

        /*
         * Ngôi sao/cross bằng các cube mỏng nằm bên trái hood.
         */
        PartDefinition star = hat.addOrReplaceChild(
                "hood_star",
                CubeListBuilder.create(),
                PartPose.offset(
                        -5.08F,
                        -5.7F,
                        0.6F
                )
        );

        star.addOrReplaceChild(
                "star_center",
                CubeListBuilder.create()
                        .texOffs(150, 0)
                        .addBox(
                                -0.18F, -0.65F, -0.65F,
                                0.36F, 1.3F, 1.3F
                        ),
                PartPose.ZERO
        );

        star.addOrReplaceChild(
                "star_up",
                CubeListBuilder.create()
                        .texOffs(154, 0)
                        .addBox(
                                -0.18F, -0.45F, -0.4F,
                                0.36F, 0.9F, 0.8F
                        ),
                PartPose.offset(
                        0.0F,
                        -1.0F,
                        0.0F
                )
        );

        star.addOrReplaceChild(
                "star_down",
                CubeListBuilder.create()
                        .texOffs(158, 0)
                        .addBox(
                                -0.18F, -0.45F, -0.4F,
                                0.36F, 0.9F, 0.8F
                        ),
                PartPose.offset(
                        0.0F,
                        1.0F,
                        0.0F
                )
        );

        star.addOrReplaceChild(
                "star_front",
                CubeListBuilder.create()
                        .texOffs(162, 0)
                        .addBox(
                                -0.18F, -0.4F, -0.45F,
                                0.36F, 0.8F, 0.9F
                        ),
                PartPose.offset(
                        0.0F,
                        0.0F,
                        -1.0F
                )
        );

        star.addOrReplaceChild(
                "star_back",
                CubeListBuilder.create()
                        .texOffs(166, 0)
                        .addBox(
                                -0.18F, -0.4F, -0.45F,
                                0.36F, 0.8F, 0.9F
                        ),
                PartPose.offset(
                        0.0F,
                        0.0F,
                        1.0F
                )
        );
    }

    // -------------------------------------------------------------------------
    // BODY / COAT / CAPE
    // -------------------------------------------------------------------------

    private static void createBody(PartDefinition root) {

        /*
         * Torso chuẩn villager.
         */
        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 80)
                        .addBox(
                                -4.0F, 0.0F, -3.0F,
                                8.0F, 12.0F, 6.0F
                        ),
                PartPose.ZERO
        );

        /*
         * Jacket upper shell.
         *
         * Không kéo nguyên 1 cube 20px xuống dưới,
         * vì như vậy nhân vật thành hình chữ nhật.
         */
        PartDefinition jacket = body.addOrReplaceChild(
                "jacket",
                CubeListBuilder.create()
                        .texOffs(32, 80)
                        .addBox(
                                -4.35F, 0.0F, -3.3F,
                                8.7F, 10.5F, 6.6F,
                                new CubeDeformation(0.08F)
                        ),
                PartPose.ZERO
        );

        // ---------------------------------------------------------------------
        // SHOULDER MANTLE / COWL
        // ---------------------------------------------------------------------

        body.addOrReplaceChild(
                "mantle",
                CubeListBuilder.create()
                        .texOffs(64, 80)
                        .addBox(
                                -6.1F, -0.65F, -4.0F,
                                12.2F, 3.5F, 8.0F,
                                new CubeDeformation(0.15F)
                        ),
                PartPose.ZERO
        );

        // Mantle trái buông thấp
        body.addOrReplaceChild(
                "mantle_left_drop",
                CubeListBuilder.create()
                        .texOffs(104, 80)
                        .addBox(
                                -3.0F, -0.8F, -3.5F,
                                4.2F, 5.0F, 7.0F
                        ),
                PartPose.offsetAndRotation(
                        -4.4F,
                        1.2F,
                        0.1F,
                        0.0F,
                        0.0F,
                        0.10F
                )
        );

        // Mantle phải
        body.addOrReplaceChild(
                "mantle_right_drop",
                CubeListBuilder.create()
                        .texOffs(128, 80)
                        .addBox(
                                -1.2F, -0.8F, -3.5F,
                                4.2F, 5.0F, 7.0F
                        ),
                PartPose.offsetAndRotation(
                        4.4F,
                        1.2F,
                        0.1F,
                        0.0F,
                        0.0F,
                        -0.10F
                )
        );

        /*
         * Gold line dưới mantle.
         */
        body.addOrReplaceChild(
                "mantle_gold_left",
                CubeListBuilder.create()
                        .texOffs(176, 0)
                        .addBox(
                                -3.0F, -0.35F, -3.65F,
                                5.5F, 0.7F, 7.3F
                        ),
                PartPose.offsetAndRotation(
                        -3.6F,
                        3.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.10F
                )
        );

        body.addOrReplaceChild(
                "mantle_gold_right",
                CubeListBuilder.create()
                        .texOffs(192, 0)
                        .addBox(
                                -2.5F, -0.35F, -3.65F,
                                5.5F, 0.7F, 7.3F
                        ),
                PartPose.offsetAndRotation(
                        3.6F,
                        3.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        -0.10F
                )
        );

        // ---------------------------------------------------------------------
        // BROOCH
        // ---------------------------------------------------------------------

        /*
         * Outer brooch xoay 45 độ -> nhìn giống diamond/gem tròn kiểu voxel.
         */
        body.addOrReplaceChild(
                "brooch_outer",
                CubeListBuilder.create()
                        .texOffs(128, 16)
                        .addBox(
                                -1.45F, -1.45F, -0.45F,
                                2.9F, 2.9F, 0.9F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        3.2F,
                        -4.45F,
                        0.0F,
                        0.0F,
                        PI / 4.0F
                )
        );

        body.addOrReplaceChild(
                "brooch_gem",
                CubeListBuilder.create()
                        .texOffs(140, 16)
                        .addBox(
                                -0.8F, -0.8F, -0.45F,
                                1.6F, 1.6F, 0.9F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        3.2F,
                        -4.98F,
                        0.0F,
                        0.0F,
                        PI / 4.0F
                )
        );

        // ---------------------------------------------------------------------
        // BELT
        // ---------------------------------------------------------------------

        body.addOrReplaceChild(
                "belt",
                CubeListBuilder.create()
                        .texOffs(128, 32)
                        .addBox(
                                -4.55F, -0.8F, -3.4F,
                                9.1F, 1.6F, 6.8F
                        ),
                PartPose.offset(
                        0.0F,
                        9.7F,
                        0.0F
                )
        );

        body.addOrReplaceChild(
                "belt_buckle",
                CubeListBuilder.create()
                        .texOffs(160, 32)
                        .addBox(
                                -0.9F, -0.85F, -0.4F,
                                1.8F, 1.7F, 0.8F
                        ),
                PartPose.offset(
                        0.0F,
                        9.7F,
                        -3.5F
                )
        );

        // ---------------------------------------------------------------------
        // LONG TEAL COAT / SKIRT
        // ---------------------------------------------------------------------

        /*
         * Chia coat làm 3 panel.
         * Nhờ vậy khi chân di chuyển silhouette vẫn đẹp hơn một cube duy nhất.
         */

        jacket.addOrReplaceChild(
                "coat_left",
                CubeListBuilder.create()
                        .texOffs(0, 104)
                        .addBox(
                                -4.5F, 0.0F, -3.25F,
                                4.2F, 11.5F, 6.5F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        9.5F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.025F
                )
        );

        jacket.addOrReplaceChild(
                "coat_right",
                CubeListBuilder.create()
                        .texOffs(32, 104)
                        .addBox(
                                0.3F, 0.0F, -3.25F,
                                4.2F, 11.5F, 6.5F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        9.5F,
                        0.0F,
                        0.0F,
                        0.0F,
                        -0.025F
                )
        );

        /*
         * Gold vertical trim giữa áo.
         */
        jacket.addOrReplaceChild(
                "coat_gold_center",
                CubeListBuilder.create()
                        .texOffs(176, 16)
                        .addBox(
                                -0.4F, 0.0F, -0.4F,
                                0.8F, 11.8F, 0.8F
                        ),
                PartPose.offset(
                        0.0F,
                        9.3F,
                        -3.35F
                )
        );

        // Gold hem trái
        jacket.addOrReplaceChild(
                "coat_hem_left",
                CubeListBuilder.create()
                        .texOffs(184, 16)
                        .addBox(
                                -4.4F, -0.4F, -3.38F,
                                4.2F, 0.8F, 6.75F
                        ),
                PartPose.offset(
                        0.0F,
                        20.8F,
                        0.0F
                )
        );

        // Gold hem phải
        jacket.addOrReplaceChild(
                "coat_hem_right",
                CubeListBuilder.create()
                        .texOffs(204, 16)
                        .addBox(
                                0.2F, -0.4F, -3.38F,
                                4.2F, 0.8F, 6.75F
                        ),
                PartPose.offset(
                        0.0F,
                        20.8F,
                        0.0F
                )
        );

        // ---------------------------------------------------------------------
        // PURPLE FRONT SASH
        // ---------------------------------------------------------------------

        PartDefinition sash = body.addOrReplaceChild(
                "front_sash",
                CubeListBuilder.create()
                        .texOffs(160, 48)
                        .addBox(
                                -1.25F, 0.0F, -0.35F,
                                2.5F, 10.0F, 0.7F
                        ),
                PartPose.offset(
                        1.4F,
                        9.8F,
                        -3.55F
                )
        );

        /*
         * Tạo đầu nhọn bằng cube nhỏ xoay 45 độ.
         */
        sash.addOrReplaceChild(
                "sash_tip",
                CubeListBuilder.create()
                        .texOffs(172, 48)
                        .addBox(
                                -1.0F, -1.0F, -0.35F,
                                2.0F, 2.0F, 0.7F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        10.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        PI / 4.0F
                )
        );

        // ---------------------------------------------------------------------
        // CAPE
        // ---------------------------------------------------------------------

        body.addOrReplaceChild(
                "cape_upper",
                CubeListBuilder.create()
                        .texOffs(128, 104)
                        .addBox(
                                -5.3F, 0.0F, -0.5F,
                                10.6F, 9.0F, 1.0F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        2.0F,
                        3.65F,
                        0.10F,
                        0.0F,
                        0.0F
                )
        );

        body.addOrReplaceChild(
                "cape_lower",
                CubeListBuilder.create()
                        .texOffs(160, 104)
                        .addBox(
                                -5.0F, 0.0F, -0.45F,
                                10.0F, 10.0F, 0.9F
                        ),
                PartPose.offsetAndRotation(
                        0.0F,
                        10.0F,
                        4.35F,
                        0.18F,
                        0.0F,
                        0.0F
                )
        );

        // ---------------------------------------------------------------------
        // SATCHEL
        // ---------------------------------------------------------------------

        body.addOrReplaceChild(
                "satchel",
                CubeListBuilder.create()
                        .texOffs(128, 128)
                        .addBox(
                                -2.0F, -2.5F, -1.3F,
                                4.0F, 5.0F, 2.6F,
                                new CubeDeformation(0.12F)
                        ),
                PartPose.offsetAndRotation(
                        -5.3F,
                        11.0F,
                        -0.7F,
                        0.0F,
                        0.0F,
                        0.08F
                )
        );

        body.addOrReplaceChild(
                "satchel_flap",
                CubeListBuilder.create()
                        .texOffs(148, 128)
                        .addBox(
                                -2.1F, -1.0F, -0.35F,
                                4.2F, 2.0F, 0.7F
                        ),
                PartPose.offset(
                        -5.3F,
                        9.4F,
                        -2.15F
                )
        );

        body.addOrReplaceChild(
                "satchel_buckle",
                CubeListBuilder.create()
                        .texOffs(166, 128)
                        .addBox(
                                -0.55F, -0.55F, -0.35F,
                                1.1F, 1.1F, 0.7F
                        ),
                PartPose.offset(
                        -5.3F,
                        10.2F,
                        -2.55F
                )
        );
    }

    // -------------------------------------------------------------------------
    // CROSSED ARMS
    // -------------------------------------------------------------------------

    private static void createArms(PartDefinition root) {

        /*
         * Vanilla villager arms parent.
         *
         * X rotation âm đưa cánh tay ra trước ngực.
         */
        PartDefinition arms = root.addOrReplaceChild(
                "arms",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        0.0F,
                        3.0F,
                        -1.0F,
                        -0.72F,
                        0.0F,
                        0.0F
                )
        );

        /*
         * Vai / tay trái.
         */
        PartDefinition leftSleeve = arms.addOrReplaceChild(
                "left_sleeve",
                CubeListBuilder.create()
                        .texOffs(0, 144)
                        .addBox(
                                -3.8F, -2.2F, -2.4F,
                                6.5F, 4.8F, 4.8F,
                                new CubeDeformation(0.12F)
                        ),
                PartPose.offsetAndRotation(
                        -4.2F,
                        1.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.13F
                )
        );

        /*
         * Vai / tay phải.
         */
        PartDefinition rightSleeve = arms.addOrReplaceChild(
                "right_sleeve",
                CubeListBuilder.create()
                        .texOffs(32, 144)
                        .addBox(
                                -2.7F, -2.2F, -2.4F,
                                6.5F, 4.8F, 4.8F,
                                new CubeDeformation(0.12F)
                        ),
                PartPose.offsetAndRotation(
                        4.2F,
                        1.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        -0.13F
                )
        );

        /*
         * Cuff vàng bên trái - đặt gần trung tâm.
         */
        leftSleeve.addOrReplaceChild(
                "left_cuff",
                CubeListBuilder.create()
                        .texOffs(72, 144)
                        .addBox(
                                -1.1F, -2.35F, -2.55F,
                                2.0F, 5.1F, 5.1F
                        ),
                PartPose.offset(
                        2.0F,
                        0.0F,
                        0.0F
                )
        );

        /*
         * Cuff vàng phải.
         */
        rightSleeve.addOrReplaceChild(
                "right_cuff",
                CubeListBuilder.create()
                        .texOffs(88, 144)
                        .addBox(
                                -0.9F, -2.35F, -2.55F,
                                2.0F, 5.1F, 5.1F
                        ),
                PartPose.offset(
                        -2.0F,
                        0.0F,
                        0.0F
                )
        );

        /*
         * Khối ở trung tâm che seam giữa 2 tay,
         * tạo đúng kiểu villager khoanh tay.
         */
        arms.addOrReplaceChild(
                "crossed_forearms",
                CubeListBuilder.create()
                        .texOffs(104, 144)
                        .addBox(
                                -3.7F, -2.1F, -2.6F,
                                7.4F, 4.2F, 5.2F
                        ),
                PartPose.offset(
                        0.0F,
                        2.15F,
                        -0.1F
                )
        );
    }

    // -------------------------------------------------------------------------
    // LEGS / BOOTS
    // -------------------------------------------------------------------------

    private static void createLegs(PartDefinition root) {

        PartDefinition rightLeg = root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 176)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 12.0F, 4.0F
                        ),
                PartPose.offset(
                        -2.0F,
                        12.0F,
                        0.0F
                )
        );

        PartDefinition leftLeg = root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 176)
                        .mirror()
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 12.0F, 4.0F
                        ),
                PartPose.offset(
                        2.0F,
                        12.0F,
                        0.0F
                )
        );

        /*
         * Boots reference khá to, vuông, nhô về trước.
         */
        rightLeg.addOrReplaceChild(
                "boot",
                CubeListBuilder.create()
                        .texOffs(40, 176)
                        .addBox(
                                -2.35F, -2.8F, -2.8F,
                                4.7F, 5.8F, 5.8F,
                                new CubeDeformation(0.10F)
                        ),
                PartPose.offset(
                        0.0F,
                        9.2F,
                        -0.25F
                )
        );

        leftLeg.addOrReplaceChild(
                "boot",
                CubeListBuilder.create()
                        .texOffs(64, 176)
                        .addBox(
                                -2.35F, -2.8F, -2.8F,
                                4.7F, 5.8F, 5.8F,
                                new CubeDeformation(0.10F)
                        ),
                PartPose.offset(
                        0.0F,
                        9.2F,
                        -0.25F
                )
        );

        /*
         * Upper boot cuff.
         */
        rightLeg.addOrReplaceChild(
                "boot_cuff",
                CubeListBuilder.create()
                        .texOffs(88, 176)
                        .addBox(
                                -2.2F, -0.7F, -2.25F,
                                4.4F, 1.4F, 4.5F
                        ),
                PartPose.offset(
                        0.0F,
                        6.9F,
                        0.0F
                )
        );

        leftLeg.addOrReplaceChild(
                "boot_cuff",
                CubeListBuilder.create()
                        .texOffs(108, 176)
                        .addBox(
                                -2.2F, -0.7F, -2.25F,
                                4.4F, 1.4F, 4.5F
                        ),
                PartPose.offset(
                        0.0F,
                        6.9F,
                        0.0F
                )
        );
    }

    // -------------------------------------------------------------------------
    // ANIMATION
    // -------------------------------------------------------------------------

    @Override
    public void setupAnim(VillagerRenderState state) {
        /*
         * Vanilla lo:
         * - head rotation
         * - walking legs
         * - villager animation state
         *
         * Hood gắn vào head nên tự follow đầu.
         * Boots gắn vào leg nên tự follow chân.
         */
        super.setupAnim(state);
    }
}