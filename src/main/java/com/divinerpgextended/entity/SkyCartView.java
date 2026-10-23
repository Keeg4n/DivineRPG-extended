package com.divinerpgextended.entity;

/**
 * Вид из вагонетки Апалачии на стене и потолке: взгляд игрока не трогаем (куда смотришь, туда и бьёшь, ставишь, целишься),
 * а только наклоняем картинку вокруг оси взгляда - "верх" вагонетки вверху экрана. Мышь тогда поворачивается на тот же
 * угол: вверх по экрану - вверх по экрану, а не по миру. Здесь только расчёт (без клиента - проверяется автотестом).
 */
public final class SkyCartView {

    private SkyCartView() {}

    /** Направление взгляда по yaw/pitch (градусы), как у игры. */
    public static double[] look(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] { -Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p) };
    }

    /** "Верх" экрана без крена: куда уходит взгляд, если поднять голову. */
    public static double[] screenUp(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] { -Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p) };
    }

    /** "Право" экрана без крена. */
    public static double[] screenRight(double yaw, double pitch) {
        double[] f = look(yaw, pitch), c = screenUp(yaw, pitch);
        return new double[] { f[1] * c[2] - f[2] * c[1], f[2] * c[0] - f[0] * c[2], f[0] * c[1] - f[1] * c[0] };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * Крен картинки (градусы, против часовой на экране), при котором up вагонетки - вверху экрана; NaN - смотрим почти
     * вдоль up, крен не определён (оставить прежний).
     */
    public static double roll(double yaw, double pitch, double[] up) {
        double[] f = look(yaw, pitch);
        double k = dot(up, f);
        double[] u = { up[0] - f[0] * k, up[1] - f[1] * k, up[2] - f[2] * k };
        if (dot(u, u) < 0.0025) return Double.NaN;
        return Math.toDegrees(Math.atan2(dot(u, screenRight(yaw, pitch)), dot(u, screenUp(yaw, pitch))));
    }

    /**
     * Сдвиг мыши (как в Entity.setAngles: по горизонтали - yaw, по вертикали - вверх положительно) при крене roll: экранное
     * направление переводится в направление без крена.
     */
    public static float[] mouse(float dx, float dy, double roll) {
        double r = Math.toRadians(roll), c = Math.cos(r), s = Math.sin(r);
        return new float[] { (float) (dx * c + dy * s), (float) (-dx * s + dy * c) };
    }
}
