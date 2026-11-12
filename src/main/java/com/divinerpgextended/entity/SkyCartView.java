package com.divinerpgextended.entity;

/**
 * Вид из вагонетки Апалачии на стене и потолке: картинка наклоняется вокруг оси взгляда - "верх" вагонетки вверху экрана.
 * Мышь тогда поворачивается на тот же угол: вверх по экрану - вверх по экрану, а не по миру. Когда вагонетка меняет плоскость
 * (пол - стена - потолок), взгляд поворачивается вместе с ней ({@link #follow}): смотрел вдоль пути - и дальше смотришь вдоль
 * пути. Здесь только расчёт (без клиента - проверяется автотестом).
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

    /** Повернуть вектор v тем же поворотом, что переводит единичный a в единичный b (кратчайшим); null - поворота нет. */
    public static double[] follow(double[] v, double[] a, double[] b) {
        double cx = a[1] * b[2] - a[2] * b[1], cy = a[2] * b[0] - a[0] * b[2], cz = a[0] * b[1] - a[1] * b[0];
        double s = Math.sqrt(cx * cx + cy * cy + cz * cz), c = dot(a, b);
        if (s < 1e-6 || c < -0.999) return null;
        double kx = cx / s, ky = cy / s, kz = cz / s, kv = kx * v[0] + ky * v[1] + kz * v[2];
        double xx = ky * v[2] - kz * v[1], xy = kz * v[0] - kx * v[2], xz = kx * v[1] - ky * v[0];
        return new double[] { v[0] * c + xx * s + kx * kv * (1 - c), v[1] * c + xy * s + ky * kv * (1 - c), v[2] * c + xz * s + kz * kv * (1 - c) };
    }

    /** Взгляд (вектор) - в yaw и pitch, как у игры; yaw - ближайший к прежнему (без скачка на 360), у вертикали - прежний. */
    public static float[] angles(double[] f, float oldYaw) {
        double l = Math.sqrt(dot(f, f));
        if (l < 1e-9) return new float[] { oldYaw, 0 };
        double y = f[1] / l;
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -y))));
        float yaw = oldYaw;
        if (Math.abs(y) < 0.999) {
            double ny = Math.toDegrees(Math.atan2(-f[0] / l, f[2] / l));
            double d = ((ny - oldYaw) % 360 + 540) % 360 - 180;
            yaw = (float) (oldYaw + d);
        }
        return new float[] { yaw, pitch };
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
