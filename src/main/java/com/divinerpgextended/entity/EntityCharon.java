package com.divinerpgextended.entity;

import net.divinerpg.utils.items.TwilightItemsOther;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.divinerpgextended.world.twilight.MortumArenas;
import com.divinerpgextended.world.twilight.TwilightWorld;

/**
 * Харон - перевозчик на пристанях Леты. Не торгует: покажи ему кристалл босса Мортума и заплати {@link #FARE} душ
 * Мортума - перевезёт к ближайшей арене этого босса (по Лете, мимо мобов). Без кристалла - говорит, что берёт и куда возит.
 */
public class EntityCharon extends EntityWanderer {

    public static final int FARE = 5, SEARCH = 8;

    public EntityCharon(World w) {
        super(w);
        setHome(TwilightWorld.MORTUM);
    }

    @Override
    public boolean interact(EntityPlayer p) {
        if (worldObj.isRemote) return true;
        ItemStack h = p.getHeldItem();
        MortumArenas.Type t = h == null ? null : MortumArenas.ofCrystal(h.getItem());
        if (t == null) {
            say(p, "dre.charon.hello", FARE);
            return true;
        }
        if (!p.capabilities.isCreativeMode && souls(p) < FARE) {
            say(p, "dre.charon.fare", FARE);
            return true;
        }
        int[] c = MortumArenas.nearestOf(worldObj, t, posX, posZ, SEARCH);
        if (c == null) {
            say(p, "dre.charon.none");
            return true;
        }
        if (!p.capabilities.isCreativeMode) for (int i = 0; i < FARE; i++) p.inventory.consumeInventoryItem(TwilightItemsOther.mortumSoul);
        say(p, "dre.charon.go." + t.key());
        ferry(p, c, t);
        return true;
    }

    private static int souls(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == TwilightItemsOther.mortumSoul) n += s.stackSize;
        return n;
    }

    /** На арену: прогрузить её чанки (чтобы зал уже был вырезан) и поставить у южного края зала. */
    public static double[] ferry(EntityPlayer p, int[] c, MortumArenas.Type t) {
        WorldServer w = (WorldServer) p.worldObj;
        for (int i = -3; i <= 3; i++) for (int k = -3; k <= 3; k++) w.theChunkProviderServer.loadChunk((c[0] >> 4) + i, (c[2] >> 4) + k);
        double x = c[0] + 0.5, y = MortumArenas.floorY(t) + 1, z = c[2] + t.zone - 4 + 0.5;
        w.func_147487_a("portal", p.posX, p.posY + 1, p.posZ, 40, 0.5, 1, 0.5, 0.5);
        p.fallDistance = 0;
        if (p instanceof EntityPlayerMP) ((EntityPlayerMP) p).setPositionAndUpdate(x, y, z);
        else p.setPosition(x, y, z);
        w.playSoundEffect(x, y, z, "mob.endermen.portal", 1.0F, 0.6F);
        return new double[] { x, y, z };
    }

    private void say(EntityPlayer p, String key, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation("dre.charon.says", new ChatComponentTranslation(key, args));
        m.getChatStyle().setColor(EnumChatFormatting.DARK_AQUA).setItalic(true);
        p.addChatMessage(m);
    }

    @Override
    public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : StatCollector.translateToLocal("entity.divinerpgextended.Charon.name");
    }
}
