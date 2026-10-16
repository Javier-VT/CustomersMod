package com.customers;

import com.customers.entity.CustomerEntity;
import com.customers.net.Net;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(CustomersMod.MODID)
public class CustomersMod {
    public static final String MODID = "customers";

    public CustomersMod(IEventBus modBus, ModContainer container) {
        Reg.BLOCKS.register(modBus);
        Reg.ITEMS.register(modBus);
        Reg.ENTITIES.register(modBus);
        Reg.TABS.register(modBus);
        modBus.addListener(Net::register);
        modBus.addListener(CustomersMod::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(Reg.CUSTOMER.get(), CustomerEntity.createAttributes().build());
    }
}
