package com.divinerpgextended.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.entity.Entity;

/**
 * CSV-журнал отладки: logs/dre-debug/дата-время-сторона.csv. Разделитель ";" и UTF-8 с BOM - Excel открывает
 * кириллицу и колонки сам. Пишется, пока у кого-то включена отладка; сбрасывается на диск раз в секунду.
 */
public final class DebugLog {

    private static final String HEAD = "время;тик;сторона;игрок;мир;x;y;z;категория;событие;подробности";
    private final File file;
    private BufferedWriter out;
    private final String side;

    public DebugLog(File gameDir, String side) {
        this.side = side;
        File dir = new File(new File(gameDir, "logs"), "dre-debug");
        dir.mkdirs();
        file = new File(dir, new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "-" + side + ".csv");
    }

    public File file() {
        return file;
    }

    private static String cell(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    public synchronized void write(Entity who, String category, String event, String details) {
        try {
            if (out == null) {
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
                out.write('﻿');
                out.write(HEAD);
                out.newLine();
            }
            StringBuilder b = new StringBuilder();
            b.append(cell(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()))).append(';');
            b.append(who != null ? who.worldObj.getTotalWorldTime() : "").append(';').append(side).append(';');
            b.append(cell(who != null ? who.getCommandSenderName() : "")).append(';');
            b.append(who != null ? who.dimension : "").append(';');
            b.append(who != null ? String.format("%.1f;%.1f;%.1f", who.posX, who.posY, who.posZ) : ";;").append(';');
            b.append(cell(category)).append(';').append(cell(event)).append(';').append(cell(details));
            out.write(b.toString());
            out.newLine();
        } catch (IOException ignored) {}
    }

    public synchronized void flush() {
        try {
            if (out != null) out.flush();
        } catch (IOException ignored) {}
    }

    public synchronized void close() {
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        out = null;
    }
}
