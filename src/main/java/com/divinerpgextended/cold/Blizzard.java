package com.divinerpgextended.cold;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Вьюга Iceika: затишье 10-20 минут, 30 секунд предупреждения ("ветер усиливается"), затем 2-3 минуты вьюги. Во
 * вьюгу под открытым небом холод растёт втрое быстрее и сносит ветром; под крышей - как обычно. Состояние
 * хранится в сохранении мира Iceika.
 */
public class Blizzard extends WorldSavedData {

    public static final int CALM = 0, WARNING = 1, STORM = 2;
    private static final String NAME = "divinerpgextended_blizzard";

    public int phase = CALM, timer = 12000;
    public float windX = 1, windZ = 0;

    public Blizzard(String name) {
        super(name);
    }

    public static Blizzard get(World world) {
        Blizzard b = (Blizzard) world.perWorldStorage.loadData(Blizzard.class, NAME);
        if (b == null) {
            b = new Blizzard(NAME);
            world.perWorldStorage.setData(NAME, b);
        }
        return b;
    }

    /** Под открытым небом: над головой нет ни одного блока. */
    public static boolean exposed(EntityPlayer p) {
        return p.worldObj.canBlockSeeTheSky(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY) + 1,
            MathHelper.floor_double(p.posZ));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        phase = tag.getInteger("Phase");
        timer = tag.getInteger("Timer");
        windX = tag.getFloat("WindX");
        windZ = tag.getFloat("WindZ");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Phase", phase);
        tag.setInteger("Timer", timer);
        tag.setFloat("WindX", windX);
        tag.setFloat("WindZ", windZ);
    }

    /** Смена фаз - раз в секунду в мире Iceika. */
    public static class Ticker {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            World w = event.world;
            if (event.phase != TickEvent.Phase.END || w.isRemote || w.getTotalWorldTime() % 20 != 0
                || !ColdHandler.isIceika(w)) return;
            Blizzard b = get(w);
            b.timer -= 20;
            if (b.timer > 0) {
                if (b.timer % 200 == 0) b.markDirty();
                return;
            }
            if (b.phase == CALM) {
                b.phase = WARNING;
                b.timer = 600;
                double a = w.rand.nextDouble() * Math.PI * 2;
                b.windX = (float) Math.cos(a);
                b.windZ = (float) Math.sin(a);
                tell(w, "dre.chat.blizzard_warning");
            } else if (b.phase == WARNING) {
                b.phase = STORM;
                b.timer = 2400 + w.rand.nextInt(1201);
                tell(w, "dre.chat.blizzard_start");
            } else {
                b.phase = CALM;
                b.timer = 12000 + w.rand.nextInt(12001);
                tell(w, "dre.chat.blizzard_end");
            }
            b.markDirty();
        }

        private static void tell(World w, String key) {
            for (Object o : w.playerEntities) ((EntityPlayer) o).addChatMessage(new ChatComponentTranslation(key));
        }
    }
}
