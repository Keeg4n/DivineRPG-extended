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
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        super.preInit(event);
        com.divinerpgextended.client.HevSounds.addPack(net.minecraft.client.Minecraft.getMinecraft().mcDataDir); // звуки Half-Life, если установлены
        com.divinerpgextended.client.DerivedTexturePack.install(); // текстуры из текстур DivineRPG собираются здесь, в jar их нет
    }

    @Override
    public net.minecraft.entity.player.EntityPlayer clientPlayer() {
        return net.minecraft.client.Minecraft.getMinecraft().thePlayer;
    }

    @Override
    public void postInit(cpw.mods.fml.common.event.FMLPostInitializationEvent event) {
        super.postInit(event);
        com.divinerpgextended.client.EasterClient.postInit(); // после Divine: обёртки его рендеров
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        com.divinerpgextended.client.EasterClient.init();
        com.divinerpgextended.client.RenderWandererAnvil.register();
        com.divinerpgextended.client.RenderLockedChest.register();
        com.divinerpgextended.client.MaulSwingClient maulSwing = new com.divinerpgextended.client.MaulSwingClient(); // перезарядка молотов
        MinecraftForge.EVENT_BUS.register(maulSwing); // мышь
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(maulSwing); // клавиатура
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.client.CameraTilt.Follow()); // взгляд за вагонеткой Апалачии
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(com.divinerpgextended.registry.ModItems.tsunami, new com.divinerpgextended.client.BowRender());
        net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(com.divinerpgextended.registry.ModItems.stormBow, new com.divinerpgextended.client.BowRender());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.client.FinsClient()); // ласты
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.ModelWandererArmor.Events()); // облики брони Странника
        com.divinerpgextended.DreTab.sort(); // вкладки творческого режима - только на клиенте
        ClientCommandHandler.instance.registerCommand(new CommandFlySpeed());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.DreBossBar());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.RenderSkyCart.Rider());
        com.divinerpgextended.client.DreamWaterView dreamView = new com.divinerpgextended.client.DreamWaterView();
        MinecraftForge.EVENT_BUS.register(dreamView);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(dreamView);
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.AccessoryButton());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.AccessoryWear()); // аксессуары из гнёзд брони на теле
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.boss.EnderGear.Client());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TooltipShift());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TooltipWrap());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TemperTooltip());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.ColorTooltip());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.TooltipRu());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.PickTierTooltip());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.InfusionTooltip());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.SocketTooltip());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.perk.Satiety.Client()); // бег голодным с перком Сытость
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.AmmoLoadKey());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.EarlyWeaponsTooltip());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.divinerpgextended.client.ShadowBowMove());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.LunaWings());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.client.EdenWater());
        MinecraftForge.EVENT_BUS.register(new com.divinerpgextended.magic.ArcanaTooltips());
        com.divinerpgextended.client.WeatherHud weatherHud = new com.divinerpgextended.client.WeatherHud();
        MinecraftForge.EVENT_BUS.register(weatherHud);
        FMLCommonHandler.instance().bus().register(weatherHud);
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
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityWanderer.class, new com.divinerpgextended.client.RenderWanderer());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.dummy.EntityTrainingDummy.class, new com.divinerpgextended.client.RenderTrainingDummy());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityCharon.class, new com.divinerpgextended.client.RenderCharon());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityGondola.class, new com.divinerpgextended.client.RenderGondola());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityShadowBolt.class,
            new net.minecraft.client.renderer.entity.RenderSnowball(net.minecraft.init.Items.ender_eye));
        // мини-боссы: увеличенные и подкрашенные модели мобов своего мира
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityNoonCori.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.twilight.model.ModelCori(), 1.6F, net.divinerpg.client.render.EntityResourceLocation.edenCori, 1.0F, 0.85F, 0.45F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityOldVerek.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.twilight.model.ModelSamek(), 1.6F, net.divinerpg.client.render.EntityResourceLocation.varek, 0.7F, 0.8F, 0.75F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityStonebreaker.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.twilight.model.ModelTwilightGolem(), 1.3F, net.divinerpg.client.render.EntityResourceLocation.apalachiaGolem, 0.85F, 0.8F, 0.9F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntitySkyGrabber.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.twilight.model.ModelCori(), 1.8F, net.divinerpg.client.render.EntityResourceLocation.skythernCori, 0.75F, 0.85F, 1.0F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityMourner.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.twilight.model.ModelDenseDemon(), 1.3F, net.divinerpg.client.render.EntityResourceLocation.mortumDemon, 0.65F, 0.55F, 0.75F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntitySleepwalker.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.vethea.model.Helio(), 1.2F, net.divinerpg.client.render.EntityResourceLocation.HELIO, 0.85F, 0.8F, 1.0F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityBlizzardSpruce.class,
            new com.divinerpgextended.client.RenderScaledBoss(new com.divinerpgextended.client.ModelBlizzardSpruce(), 1.25F, new net.minecraft.util.ResourceLocation("divinerpgextended", "textures/entity/blizzard_spruce.png"), 1.0F, 1.0F, 1.0F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntityBlizzardSnowman.class, new net.minecraft.client.renderer.entity.RenderSnowMan());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.mini.EntitySnowClump.class, new net.minecraft.client.renderer.entity.RenderSnowball(net.minecraft.init.Items.snowball));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityLostSoul.class,
            new net.minecraft.client.renderer.entity.RenderSnowball(net.divinerpg.utils.items.TwilightItemsOther.mortumSoul));
        RenderingRegistry.registerEntityRenderingHandler(EntityGreatCadillion.class, new RenderGreatCadillion());
        // око-прислужник Смотрителя: уменьшенная модель Смотрителя Divine, багровое
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityWatcherEye.class,
            new com.divinerpgextended.client.RenderScaledBoss(new net.divinerpg.client.render.entity.vanilla.model.ModelWatcher(), 1.3F,
                new net.minecraft.util.ResourceLocation("divinerpg", "textures/mobs/watcher.png"), 1.0F, 0.55F, 0.55F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityAncientGolem.class, new com.divinerpgextended.client.RenderAncientGolem());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityAncientDramcryx.class, new com.divinerpgextended.client.RenderAncientDramcryx());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityWarriorKing.class, new com.divinerpgextended.client.RenderWarriorKing());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityWindLord.class, new com.divinerpgextended.client.RenderWindLord());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityBoulder.class, new com.divinerpgextended.client.RenderBoulder(net.divinerpg.utils.blocks.TwilightBlocks.wildwoodDirt));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityRockfall.class, new com.divinerpgextended.client.RenderBoulder(net.divinerpg.utils.blocks.TwilightBlocks.twilightStone));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntitySkyCart.class, new com.divinerpgextended.client.RenderSkyCart());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityTomahawk.class, new com.divinerpgextended.client.RenderTomahawk());
        // Лиоплевродон-босс: модель Divine крупнее, с короной; призывы якорей - модели краба и акулы Divine
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityLiopleurodonBoss.class,
            new RenderScaledBoss(new net.divinerpg.client.render.entity.vanilla.model.ModelLiopleurodon(), 1.5F,
                new net.minecraft.util.ResourceLocation("divinerpg", "textures/mobs/liopleurodon.png"), 0.85F, 0.95F, 1.0F)
                    .crown(new com.divinerpgextended.client.BossCrown("head", 6F, 0F, 6.5F, 6F, 0x40C8FF)));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.entity.EntityWaterJet.class, new net.minecraft.client.renderer.entity.Render() {

            @Override
            public void doRender(net.minecraft.entity.Entity e, double x, double y, double z, float yaw, float pt) {} // видна брызгами

            @Override
            protected net.minecraft.util.ResourceLocation getEntityTexture(net.minecraft.entity.Entity e) {
                return null;
            }
        });
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.magic.EntitySummonCrab.class,
            new RenderScaledBoss(new ModelCrab(), 0.8F, EntityResourceLocation.crab, 1.0F, 0.9F, 0.9F));
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.magic.EntitySummonShark.class,
            new RenderScaledBoss(new net.divinerpg.client.render.entity.vanilla.model.ModelShark(), 0.6F,
                new net.minecraft.util.ResourceLocation("divinerpg", "textures/mobs/shark.png"), 0.8F, 0.9F, 1.0F));
        // слизень слизневого меча - ванильный слизень; око циклопического жезла - маленький Смотритель Divine, жёлтый
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.magic.EntitySummonSlime.class, new com.divinerpgextended.client.RenderSummonSlime());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.magic.EntitySummonEye.class,
            new RenderScaledBoss(new net.divinerpg.client.render.entity.vanilla.model.ModelWatcher(), 0.7F,
                new net.minecraft.util.ResourceLocation("divinerpg", "textures/mobs/watcher.png"), 1.0F, 0.95F, 0.6F));
        com.divinerpgextended.block.BlockSkyRail.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new com.divinerpgextended.client.RenderSkyRail());
        RenderingRegistry.registerEntityRenderingHandler(com.divinerpgextended.fishing.EntityDreHook.class,
            new com.divinerpgextended.client.RenderDreHook());
        // Карцинус - Король крабов, увеличенный в 4 раза
        RenderingRegistry.registerEntityRenderingHandler(EntityCarcinus.class,
            new RenderScaledBoss(new ModelCrab(), 4.0F, EntityResourceLocation.crab, 1.0F, 0.85F, 0.8F) // kingCrab.png 64x64 - не под модель краба (64x32), растягивалась
                .crown(new com.divinerpgextended.client.BossCrown("RearEnd", 1F, -4F, 0F, 5F, 0x4AB0FF))); // корона на панцире: отличить от свиты
        // Снежный Глакон - Глакон, увеличенный и холодного оттенка
        RenderingRegistry.registerEntityRenderingHandler(EntitySnowGlacon.class,
            new RenderScaledBoss(new ModelGlacon(), 2.2F, EntityResourceLocation.glacon, 0.8F, 0.9F, 1.0F)
                .crown(new com.divinerpgextended.client.BossCrown("head", 0F, -4F, -0.5F, 5F, 0xA8E8FF))); // корона на голове
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
