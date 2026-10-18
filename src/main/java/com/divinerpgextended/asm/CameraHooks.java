package com.divinerpgextended.asm;

/** Вызов из EntityRenderer.orientCamera ({@link CameraTransformer}): сама работа - в клиентском классе мода. */
public class CameraHooks {

    private static boolean broken;

    public static void orient(float partialTicks) {
        if (broken) return;
        try {
            com.divinerpgextended.client.CameraTilt.apply(partialTicks);
        } catch (Throwable t) {
            broken = true; // не сыпать ошибками каждый кадр
            org.apache.logging.log4j.LogManager.getLogger("divinerpgextended-core").error("Камера вагонетки: ошибка, наклон выключен", t);
        }
    }
}
