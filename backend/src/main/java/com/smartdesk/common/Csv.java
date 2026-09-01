package com.smartdesk.common;

import java.util.List;

/** 0.5-d: 아주 작은 CSV 직렬화 (RFC 4180). Excel 한글 인식을 위해 UTF-8 BOM 포함. */
public final class Csv {

    private Csv() {}

    public static String of(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, header.stream().map(h -> (Object) h).toList());
        for (List<Object> row : rows) line(sb, row);
        return sb.toString();
    }

    private static void line(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    private static String escape(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
