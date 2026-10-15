package com.divinerpgextended.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.divinerpg.entities.vanilla.EntityTheWatcher;
import net.divinerpg.utils.blocks.ArcanaBlocks;
import net.divinerpg.utils.config.ConfigurationHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.divinerpgextended.registry.ModItems;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Вход в Аркану - после Смотрителя (Watcher).
 * <ul>
 * <li>Со Смотрителя падает Око Смотрителя; рамки портала Арканы крафтятся только с ним (рецепт Divine заменён).</li>
 * <li>Портал пускает только тех, кто сам участвовал в убийстве Смотрителя. Остальным уже на подходе к порталу
 * держим паузу переноса (Divine переносит только при нулевой паузе, причём при первом же касании - проверять
 * только блок под ногами поздно) и подсказываем, что нужно.</li>
 * <li>Если всё же проскочил (падение, телепорт) - сразу возвращаем туда, где игрок стоял недавно, вдали от портала.</li>
 * </ul>
 */
public class ArcanaGate {

    /** Последнее безопасное место игрока вне Арканы: измерение и координаты. */
    private static final Map<UUID, double[]> SAFE = new HashMap<UUID, double[]>();

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.entityLiving instanceof EntityTheWatcher) || event.entityLiving.worldObj.isRemote) return;
        EntityItem eye = new EntityItem(event.entityLiving.worldObj, event.entityLiving.posX, event.entityLiving.posY + 1,
            event.entityLiving.posZ, new ItemStack(ModItems.watcherEye));
        event.drops.add(eye);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.START || p.worldObj.isRemote || !(p instanceof EntityPlayerMP)) return;
        if (BossProgress.has(p, Boss.WATCHER) || p.capabilities.isCreativeMode) return;
        if (p.dimension == ConfigurationHelper.arcana) {
            sendBack((EntityPlayerMP) p);
            return;
        }
        if (nearPortal(p)) {
            p.timeUntilPortal = 20; // портал Divine не перенесёт, пока пауза не кончилась
            if (p.ticksExisted % 60 == 0) p.addChatMessage(new ChatComponentTranslation("dre.chat.arcana_locked"));
        } else if (p.ticksExisted % 20 == 0 && p.onGround) {
            SAFE.put(p.getUniqueID(), new double[] { p.dimension, p.posX, p.posY, p.posZ });
        }
    }

    /** Портал в паре блоков вокруг (и ниже - с запасом на скорость падения). */
    private static boolean nearPortal(EntityPlayer p) {
        int x0 = MathHelper.floor_double(p.boundingBox.minX) - 2, x1 = MathHelper.floor_double(p.boundingBox.maxX) + 2;
        int z0 = MathHelper.floor_double(p.boundingBox.minZ) - 2, z1 = MathHelper.floor_double(p.boundingBox.maxZ) + 2;
        int y1 = MathHelper.floor_double(p.boundingBox.minY) + 1;
        int y0 = MathHelper.floor_double(p.boundingBox.minY + Math.min(0, p.motionY * 2)) - 2;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (p.worldObj.getBlock(x, y, z) == ArcanaBlocks.arcanaPortal) return true;
        return false;
    }

    private static void sendBack(EntityPlayerMP p) {
        double[] s = SAFE.get(p.getUniqueID());
        int dim = s == null ? 0 : (int) s[0];
        WorldServer target = p.mcServer.worldServerForDimension(dim);
        final double x, y, z;
        if (s != null) {
            x = s[1];
            y = s[2];
            z = s[3];
        } else {
            ChunkCoordinates sp = target.getSpawnPoint();
            x = sp.posX + 0.5;
            y = target.getTopSolidOrLiquidBlock(sp.posX, sp.posZ) + 1;
            z = sp.posZ + 0.5;
        }
        p.timeUntilPortal = 100;
        p.mcServer.getConfigurationManager().transferPlayerToDimension(p, dim, new Teleporter(target) {

            @Override
            public void placeInPortal(Entity e, double px, double py, double pz, float yaw) {
                e.setLocationAndAngles(x, y, z, e.rotationYaw, e.rotationPitch);
                e.motionX = e.motionY = e.motionZ = 0;
            }

            @Override
            public boolean placeInExistingPortal(Entity e, double px, double py, double pz, float yaw) {
                placeInPortal(e, px, py, pz, yaw);
                return true;
            }

            @Override
            public boolean makePortal(Entity e) {
                return true;
            }

            @Override
            public void removeStalePortalLocations(long time) {}
        });
        p.setPositionAndUpdate(x, y, z);
        p.addChatMessage(new ChatComponentTranslation("dre.chat.arcana_locked"));
    }
}
