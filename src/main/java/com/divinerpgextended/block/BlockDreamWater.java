package com.divinerpgextended.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Вода мечтаний - перевёрнутая вода озёр в потолке этажей Ветеи: течёт вверх (плотность меньше нуля), гладь смотрит
 * вниз. Для игры это вода (материал воды): в ней плавают, тонут и рыбачат. Между двумя источниками под опорой рождается
 * новый (бесконечная, как вода).
 * <p>
 * Кто коснулся её верхом хитбокса, тот сразу перевёрнут ({@link #inverted}): висит вверх ногами, ноги в воде, голова в
 * воздухе - у глади держится сам, тут и рыбачат (поплавок закидывается вверх, в озеро). Экран перевёрнут, клавиши - как в обычной воде по экрану: Shift -
 * нырнуть глубже (в мире вверх), пробел - всплыть к глади (вниз), пробел у глади - вынырнуть и упасть. В глубине тянет к
 * дну озера (вверх).
 */
public class BlockDreamWater extends BlockFluidClassic {

    /** С какой глубины (сверху хитбокса) игрок перевёрнут; на какой держится у глади; где начинается глубина. */
    public static final double FLIP = 0.01, HOLD = 0.6, DEEP = 1.3;

    @SideOnly(Side.CLIENT)
    private IIcon still, flow;

    public BlockDreamWater(Fluid f) {
        super(f, Material.water);
        setBlockName("dre.dreamWater");
        setLightLevel(0.4F);
        setHardness(100F);
        setLightOpacity(2);
    }

    @Override
    public void updateTick(World w, int x, int y, int z, Random r) {
        super.updateTick(w, x, y, z, r);
        if (w.isRemote || w.getBlock(x, y, z) != this || w.getBlockMetadata(x, y, z) == 0) return;
        int sources = 0;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            if (isSource(w, x + d[0], y, z + d[1])) sources++;
        // опора - сверху: вода течёт вверх
        if (sources >= 2 && (w.getBlock(x, y + 1, z).getMaterial().isSolid() || isSource(w, x, y + 1, z))) w.setBlock(x, y, z, this, 0, 3);
    }

    public static boolean isSource(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) instanceof BlockDreamWater && w.getBlockMetadata(x, y, z) == 0;
    }

    /** На сколько верх хитбокса ушёл в воду мечтаний (по столбцу середины); 0 - не в ней. */
    public static double depth(Entity e) {
        int x = MathHelper.floor_double(e.posX), z = MathHelper.floor_double(e.posZ);
        int y0 = MathHelper.floor_double(e.boundingBox.minY), y1 = MathHelper.floor_double(e.boundingBox.maxY);
        for (int y = y0; y <= y1; y++) if (e.worldObj.getBlock(x, y, z) instanceof BlockDreamWater) return Math.max(0, e.boundingBox.maxY - y);
        return 0;
    }

    public static boolean inside(Entity e) {
        return depth(e) > 0;
    }

    /** Перевёрнут: ноги (верх хитбокса) в воде мечтаний. */
    public static boolean inverted(Entity e) {
        return depth(e) >= FLIP;
    }

    /** Голова перевёрнутого (низ хитбокса) в воде - тонет. У мелких мобов голова - нижняя четверть хитбокса. */
    public static boolean headUnder(EntityLivingBase e) {
        return e.worldObj.getBlock(MathHelper.floor_double(e.posX), MathHelper.floor_double(e.boundingBox.minY + Math.min(0.2, e.height * 0.25)),
            MathHelper.floor_double(e.posZ)) instanceof BlockDreamWater;
    }

    /** На какой глубине держится у глади: игрок - {@link #HOLD}, мелкий моб - на треть роста (иначе голова под водой и тонет). */
    public static double hold(EntityLivingBase e) {
        return e instanceof EntityPlayer ? HOLD : Math.min(HOLD, e.height * 0.35);
    }

    /**
     * Не живое (выпавшие вещи, опыт): в воде мечтаний тонет по-своему - уходит вверх, к дну озера, и там лежит. Вызывается
     * каждый тик на сервере и на клиенте (движение вещей считают обе стороны).
     */
    public static void sinkLoose(World w) {
        if (w == null) return;
        // по копии и только в загруженных чанках: чтение блока на краю догружало чанк, его сущности вставали в список
        // посреди обхода - сервер падал (ConcurrentModificationException, полёт к острову Энда)
        for (Object o : w.loadedEntityList.toArray()) {
            if (!(o instanceof net.minecraft.entity.item.EntityItem) && !(o instanceof net.minecraft.entity.item.EntityXPOrb)) continue;
            Entity e = (Entity) o;
            int ex = MathHelper.floor_double(e.posX), ez = MathHelper.floor_double(e.posZ);
            if (!w.checkChunksExist(ex - 1, 0, ez - 1, ex + 1, 0, ez + 1) || !inside(e)) continue;
            e.motionY += 0.04 + 0.02; // гасит тяжесть (0.04) и тянет вверх, к дну озера
            e.motionY = Math.min(0.12, e.motionY);
            e.fallDistance = 0;
        }
    }

    public static class Loose {

        @SubscribeEvent
        public void onWorld(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent ev) {
            if (ev.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.START) sinkLoose(ev.world);
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public void onClient(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent ev) {
            if (ev.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.START) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.isGamePaused()) return;
            sinkLoose(mc.theWorld);
        }
    }

    /** Перевёрнутое плавание (на обеих сторонах: движение игрока считает клиент). */
    public static class Swim {

        private static java.lang.reflect.Field jumping, floating;

        static boolean jumping(EntityLivingBase e) {
            try {
                if (jumping == null) jumping = ReflectionHelper.findField(EntityLivingBase.class, "isJumping", "field_70703_bu");
                return jumping.getBoolean(e);
            } catch (Throwable t) {
                return false;
            }
        }

        /** Сервер считает висящего у глади "летающим" и через 4 с выкидывает - сбрасываем его счётчик. */
        static void notFlying(EntityPlayerMP p) {
            if (p.playerNetServerHandler == null) return;
            try {
                if (floating == null) floating = ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");
                floating.setInt(p.playerNetServerHandler, 0);
            } catch (Throwable ignored) {}
        }

        /** Отпустил пробел у глади: следующее нажатие выкидывает из озера. */
        private static final java.util.Map<EntityLivingBase, Boolean> ARMED = new java.util.WeakHashMap<EntityLivingBase, Boolean>();

        @SubscribeEvent
        public void onUpdate(LivingEvent.LivingUpdateEvent ev) {
            EntityLivingBase e = ev.entityLiving;
            double d = depth(e);
            if (d <= 0) {
                ARMED.remove(e);
                return;
            }
            if (e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isFlying) return;
            e.fallDistance = 0;
            if (!e.worldObj.isRemote && e instanceof EntityPlayerMP) {
                notFlying((EntityPlayerMP) e);
                if (!headUnder(e)) e.setAir(300); // голова в воздухе (игра считает глаза у верха хитбокса - в воде)
            } else if (!e.worldObj.isRemote && !headUnder(e)) {
                e.setAir(300); // и моб: висит вверх ногами, голова в воздухе - не тонет
            }
            // как в обычной воде, но по перевёрнутому экрану: пробел - к глади (в мире вниз), Shift - глубже (вверх)
            // мобы в воде всё время "прыгают" (ИИ плавания) - для них клавиш нет: просто висят у глади
            boolean player = e instanceof EntityPlayer, surface = player && jumping(e), dive = player && e.isSneaking();
            if (!player) { // моб всплывает к глади из любой глубины (как в обычной воде) и висит головой в воздухе
                if (d >= FLIP || e.motionY > 0) e.motionY = Math.max(-0.3, (hold(e) - d) * 0.3 + 0.02);
                return;
            }
            if (d >= DEEP) { // в глубине тянет к дну озера (вверх), как в обычной воде ко дну
                e.motionY += 0.04; // вода тянет вниз на 0.02, прыжок воды поднимает на 0.04 - итог вверх
                if (surface) e.motionY -= 0.10;
                if (dive) e.motionY += 0.03;
                e.motionY = Math.max(-0.3, Math.min(0.3, e.motionY));
                if (surface) ARMED.remove(e); // всплыл с зажатым пробелом - у глади держится, а не вываливается
            } else if (d >= FLIP || e.motionY > 0) { // у глади: держится ногами в воде
                if (!surface) ARMED.put(e, Boolean.TRUE);
                if (surface && ARMED.containsKey(e)) e.motionY = -0.35; // пробел у глади - вынырнуть: падает из озера
                else if (dive) e.motionY = 0.12;
                else e.motionY = (HOLD - d) * 0.3 + 0.02; // пружинит к глубине HOLD (+0.02 гасит тягу воды вниз)
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        still = reg.registerIcon("divinerpgextended:dreamwater_still");
        flow = reg.registerIcon("divinerpgextended:dreamwater_flow");
        getFluid().setIcons(still, flow);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? still : flow;
    }
}
