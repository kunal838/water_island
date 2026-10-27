package com.kunal.waterisland;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the exported file contents: Excel workbook, CSV or JSON. */
final class Exporter {
    static final int XLSX = 0;
    static final int CSV = 1;
    static final int JSON = 2;

    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private Exporter() {}

    static String extension(int format) {
        return format == XLSX ? "xlsx" : format == CSV ? "csv" : "json";
    }

    static String mime(int format) {
        return format == XLSX ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : format == CSV ? "text/csv" : "application/json";
    }

    static String fileName(int format) {
        return "water-island-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())
                + "." + extension(format);
    }

    static byte[] build(Context c, int format) throws IOException, JSONException {
        switch (format) {
            case XLSX: return xlsx(c);
            case CSV: return csv(c).getBytes(StandardCharsets.UTF_8);
            default: return json(c).getBytes(StandardCharsets.UTF_8);
        }
    }

    // ---- shared rows ---------------------------------------------------------------------

    private static List<Object[]> dailyRows(Context c) {
        int glass = Store.glassMl(c);
        int goal = Store.goalMl(c);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Date", "Water (ml)", "Glasses", "Goal met"});
        for (Map.Entry<String, Integer> e : Store.dailyTotals(c).entrySet()) {
            rows.add(new Object[]{e.getKey(), e.getValue(), Math.round(10f * e.getValue() / glass) / 10.0,
                    e.getValue() >= goal ? "yes" : "no"});
        }
        return rows;
    }

    private static List<Object[]> todayRows(Context c) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Time", "Amount (ml)"});
        for (long[] e : Store.todayLog(c)) rows.add(new Object[]{fmt.format(new Date(e[0])), (int) e[1]});
        return rows;
    }

    private static List<Object[]> settingsRows(Context c) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Setting", "Value"});
        rows.add(new Object[]{"Glass size (ml)", Store.glassMl(c)});
        rows.add(new Object[]{"Daily goal (glasses)", Store.goalGlasses(c)});
        rows.add(new Object[]{"Daily goal (ml)", Store.goalMl(c)});
        rows.add(new Object[]{"Remind every (seconds)", Store.intervalSec(c)});
        rows.add(new Object[]{"Reminder sound", Store.sound(c) ? "on" : "off"});
        return rows;
    }

    // ---- CSV -----------------------------------------------------------------------------

    private static String csv(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Object[] row : dailyRows(c)) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(row[i]);
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    // ---- JSON ----------------------------------------------------------------------------

    private static String json(Context c) throws JSONException {
        int glass = Store.glassMl(c);
        int goal = Store.goalMl(c);
        JSONObject root = new JSONObject();
        root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));

        JSONObject settings = new JSONObject();
        settings.put("glassMl", glass);
        settings.put("goalGlasses", Store.goalGlasses(c));
        settings.put("goalMl", goal);
        settings.put("remindEverySeconds", Store.intervalSec(c));
        settings.put("reminderSound", Store.sound(c));
        root.put("settings", settings);

        JSONArray days = new JSONArray();
        for (Map.Entry<String, Integer> e : Store.dailyTotals(c).entrySet()) {
            JSONObject d = new JSONObject();
            d.put("date", e.getKey());
            d.put("ml", e.getValue());
            d.put("glasses", Math.round(10f * e.getValue() / glass) / 10.0);
            d.put("goalMet", e.getValue() >= goal);
            days.put(d);
        }
        root.put("days", days);

        JSONArray log = new JSONArray();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        for (long[] e : Store.todayLog(c)) {
            JSONObject entry = new JSONObject();
            entry.put("time", fmt.format(new Date(e[0])));
            entry.put("ml", e[1]);
            log.put(entry);
        }
        root.put("todayLog", log);
        return root.toString(2);
    }

    // ---- Excel (.xlsx, hand-built OOXML) -------------------------------------------------

    private static byte[] xlsx(Context c) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", header()
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + sheetOverride(1) + sheetOverride(2) + sheetOverride(3)
                    + "</Types>");
            put(zip, "_rels/.rels", header()
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", header()
                    + "<workbook xmlns=\"" + NS_MAIN + "\" xmlns:r=\"" + NS_REL + "\"><sheets>"
                    + "<sheet name=\"Daily\" sheetId=\"1\" r:id=\"rId1\"/>"
                    + "<sheet name=\"Today\" sheetId=\"2\" r:id=\"rId2\"/>"
                    + "<sheet name=\"Settings\" sheetId=\"3\" r:id=\"rId3\"/>"
                    + "</sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", header()
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + sheetRel(1) + sheetRel(2) + sheetRel(3) + "</Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheet(dailyRows(c)));
            put(zip, "xl/worksheets/sheet2.xml", sheet(todayRows(c)));
            put(zip, "xl/worksheets/sheet3.xml", sheet(settingsRows(c)));
        }
        return bytes.toByteArray();
    }

    private static String header() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    }

    private static String sheetOverride(int n) {
        return "<Override PartName=\"/xl/worksheets/sheet" + n
                + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>";
    }

    private static String sheetRel(int n) {
        return "<Relationship Id=\"rId" + n + "\" Type=\"" + NS_REL + "/worksheet\" Target=\"worksheets/sheet" + n + ".xml\"/>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheet(List<Object[]> rows) {
        StringBuilder sb = new StringBuilder(header());
        sb.append("<worksheet xmlns=\"").append(NS_MAIN).append("\">")
                .append("<cols><col min=\"1\" max=\"4\" width=\"22\" customWidth=\"1\"/></cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            Object[] row = rows.get(r);
            for (int col = 0; col < row.length; col++) {
                String ref = (char) ('A' + col) + String.valueOf(r + 1);
                if (row[col] instanceof Number) {
                    sb.append("<c r=\"").append(ref).append("\"><v>")
                            .append(String.format(Locale.US, row[col] instanceof Integer ? "%d" : "%s", row[col]))
                            .append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                            .append(escape(String.valueOf(row[col]))).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
