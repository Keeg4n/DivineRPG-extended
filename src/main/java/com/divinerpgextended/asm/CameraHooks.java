package com.divinerpgextended.asm;

/** Вызовы из EntityRenderer ({@link CameraTransformer}): сама работа - в клиентском классе мода. */
public class CameraHooks {

    private static boolean broken;

    /** Начало orientCamera: крен вида в вагонетке Апалачии. */
    public static void orient(float partialTicks) {
        if (broken) return;
        try {
            com.divinerpgextended.client.CameraTilt.apply(partialTicks);
        } catch (Throwable t) {
            broken = true; // не сыпать ошибками каждый кадр
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended-core").error("Камера вагонетки: ошибка, наклон выключен", t);
        }
    }

    /** Вместо player.setAngles(dx, dy) в updateCameraAndRender: в вагонетке мышь - в осях наклонённого экрана. */
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
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended-core").error("Камера вагонетки: ошибка, наклон выключен", t);
            e.setAngles(dx, dy);
        }
    }
}
