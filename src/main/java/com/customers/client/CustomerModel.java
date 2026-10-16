package com.customers.client;

import com.customers.entity.CustomerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Player-shaped model that can sit (legs forward) when the customer is on a chair. */
public class CustomerModel extends PlayerModel<CustomerEntity> {
    public CustomerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(CustomerEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (e.isSitting()) {
            rightArm.xRot += -0.62831855F;
            leftArm.xRot += -0.62831855F;
            rightLeg.xRot = -1.4137167F;
            rightLeg.yRot = 0.31415927F;
            rightLeg.zRot = 0.07853982F;
            leftLeg.xRot = -1.4137167F;
            leftLeg.yRot = -0.31415927F;
            leftLeg.zRot = -0.07853982F;
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
        }
    }
}
