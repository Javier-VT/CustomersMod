package com.customers.client;

import com.customers.CustomersMod;
import com.customers.entity.CustomerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CustomerRenderer extends HumanoidMobRenderer<CustomerEntity, CustomerModel> {
    /** Skins whose arms are 3px wide (Alex model). */
    private static final boolean[] SLIM = {false, false, true, false, false, true, false, false, false, true, true};
    private static final ResourceLocation[] SKINS = new ResourceLocation[SLIM.length];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = ResourceLocation.fromNamespaceAndPath(CustomersMod.MODID, "textures/entity/customer/skin_" + i + ".png");
        }
    }

    private final CustomerModel wide;
    private final CustomerModel slim;

    public CustomerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CustomerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new CustomerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(CustomerEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        int v = Math.max(0, Math.min(SLIM.length - 1, entity.getVariant()));
        this.model = SLIM[v] ? slim : wide;
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CustomerEntity entity) {
        return SKINS[Math.max(0, Math.min(SKINS.length - 1, entity.getVariant()))];
    }

    @Override
    protected void scale(CustomerEntity entity, PoseStack pose, float partialTick) {
        pose.scale(0.95F, 0.95F, 0.95F);
    }

    /** Name lines are only shown while the player looks at the customer. */
    @Override
    protected boolean shouldShowName(CustomerEntity entity) {
        return entity == Minecraft.getInstance().crosshairPickEntity
                && this.entityRenderDispatcher.distanceToSqr(entity) < 144.0
                && !entity.getLines().isEmpty();
    }

    @Override
    protected void renderNameTag(CustomerEntity entity, Component name, PoseStack pose, MultiBufferSource buffer, int light, float partialTick) {
        List<Component> lines = entity.getLines();
        for (int i = 0; i < lines.size(); i++) {
            pose.pushPose();
            pose.translate(0.0, (lines.size() - 1 - i) * 0.27, 0.0);
            super.renderNameTag(entity, lines.get(i), pose, buffer, light, partialTick);
            pose.popPose();
        }
    }
}
