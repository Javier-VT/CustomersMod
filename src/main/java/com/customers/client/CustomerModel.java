package com.customers.client;

import com.customers.entity.CustomerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Player-shaped model (wide or slim arms). */
public class CustomerModel extends PlayerModel<CustomerEntity> {
    public CustomerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    // The customer rides an invisible seat, so HumanoidModel.riding already gives the sitting pose.
}
