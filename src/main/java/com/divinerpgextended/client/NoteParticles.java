package com.divinerpgextended.client;

import net.minecraft.client.Minecraft;

import com.divinerpgextended.world.NoteInstruments;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Note particles of the note blocks with our instruments (world/NoteInstruments): the same colored note as vanilla, by the step. */
public final class NoteParticles {

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        int[] n;
        while ((n = NoteInstruments.PENDING.poll()) != null) {
            if (mc.theWorld != null) mc.theWorld.spawnParticle("note", n[0] + 0.5, n[1] + 1.2, n[2] + 0.5, n[3] / 24.0, 0.0, 0.0);
        }
    }
}
