package com.divinerpgextended.asm;

/** Calls from EntityRenderer ({@link CameraTransformer}): the work itself is in the mod's client class. */
public class CameraHooks {

    private static boolean broken;

    /** Start of orientCamera: roll of the view in the Apalachia Cart. */
    public static void orient(float partialTicks) {
        if (broken) return;
        try {
            com.divinerpgextended.client.CameraTilt.apply(partialTicks);
        } catch (Throwable t) {
            broken = true; // do not spam errors every frame
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended-core").error("Minecart camera: error, tilt disabled", t);
        }
    }

    /** Instead of player.setAngles(dx, dy) in updateCameraAndRender: in the cart the mouse is in the axes of the tilted screen. */
    public static void angles(Object player, float dx, float dy) {
        net.minecraft.entity.Entity e = (net.minecraft.entity.Entity) player;
        if (broken) {
            e.setAngles(dx, dy);
            return;
        }
        try {
            com.divinerpgextended.client.CameraTilt.angles(e, dx, dy);
        } catch (Throwable t) {
            broken = true;
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended-core").error("Minecart camera: error, tilt disabled", t);
            e.setAngles(dx, dy);
        }
    }
}
