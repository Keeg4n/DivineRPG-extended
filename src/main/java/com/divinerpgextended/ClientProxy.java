package com.divinerpgextended;

import net.divinerpg.client.render.EntityResourceLocation;
import net.divinerpg.client.render.entity.vanilla.model.ModelCrab;
import net.divinerpg.client.render.entity.vanilla.model.ModelGlacon;
import net.divinerpg.utils.items.IceikaItems;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;

import com.divinerpgextended.client.ColdHud;
import com.divinerpgextended.client.CommandFlySpeed;
import com.divinerpgextended.client.RenderFrostColossus;
import com.divinerpgextended.client.RenderGreatCadillion;
import com.divinerpgextended.client.RenderLichKing;
import com.divinerpgextended.client.RenderScaledBoss;
import com.divinerpgextended.entity.EntityCarcinus;
import com.divinerpgextended.entity.EntityFrostCharge;
import com.divinerpgextended.entity.EntityFrostColossus;
import com.divinerpgextended.entity.EntityGreatCadillion;
import com.divinerpgextended.entity.EntityIcicle;
import com.divinerpgextended.entity.EntityLichKing;
import com.divinerpgextended.entity.EntityLairCrab;
import com.divinerpgextended.entity.EntitySnowGlacon;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientCommandHandler.instance.registerCommand(new CommandFlySpeed());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.DreBossBar());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.AccessoryButton());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TooltipWrap());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TemperTooltip());
        ColdHud coldHud = new ColdHud();
        com.divinerpgextended.trinket.DoubleJump.Client airMoves = new com.divinerpgextended.trinket.DoubleJump.Client();
        MinecraftForge.EVENT_BUS.register(coldHud);
        FMLCommonHandler.instance()
            .bus()
            .register(coldHud);
        FMLCommonHandler.instance()
            .bus()
            .register(airMoves);
        MinecraftForge.EVENT_BUS.register(airMoves);
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.client.ArcanaText());
        FMLCommonHandler.instance()
            .bus()
            .register(new com.divinerpgextended.client.TwilightRelight());
        com.divinerpgextended.debug.DebugClient debugClient = new com.divinerpgextended.debug.DebugClient();
        MinecraftForge.EVENT_BUS.register(debugClient);
        FMLCommonHandler.instance()
            .bus()
            .register(debugClient);
        RenderingRegistry.registerEntityRenderingHandler(EntityGreatCadillion.class, new RenderGreatCadillion());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.fishing.EntityDreHook.class,
            new com.divinerpgextended.client.RenderDreHook());
        // Карцинус - Король крабов, увеличенный в 4 раза
        RenderingRegistry.registerEntityRenderingHandler(EntityCarcinus.class,
            new RenderScaledBoss(new ModelCrab(), 4.0F, EntityResourceLocation.kingCrab, 1.0F, 0.85F, 0.8F));
        // Снежный Глакон - Глакон, увеличенный и холодного оттенка
        RenderingRegistry.registerEntityRenderingHandler(EntitySnowGlacon.class,
            new RenderScaledBoss(new ModelGlacon(), 2.2F, EntityResourceLocation.glacon, 0.8F, 0.9F, 1.0F));
        RenderingRegistry.registerEntityRenderingHandler(EntityFrostCharge.class,
            new RenderSnowball(IceikaItems.snowflake));
        RenderingRegistry.registerEntityRenderingHandler(EntityFrostColossus.class, new RenderFrostColossus());
        RenderingRegistry.registerEntityRenderingHandler(EntityLichKing.class, new RenderLichKing());
        RenderingRegistry.registerEntityRenderingHandler(EntityIcicle.class,
            new RenderSnowball(Item.getItemFromBlock(Blocks.ice)));
        // норные крабы - крабы Divine песочного оттенка, король вдвое крупнее
        RenderingRegistry.registerEntityRenderingHandler(EntityLairCrab.class,
            new RenderScaledBoss(new ModelCrab(), 1.0F, EntityResourceLocation.crab, 1.0F, 0.85F, 0.65F));
        RenderingRegistry.registerEntityRenderingHandler(EntityLairCrab.King.class,
            new RenderScaledBoss(new ModelCrab(), 2.0F, EntityResourceLocation.crab, 1.0F, 0.85F, 0.65F));
    }
}
