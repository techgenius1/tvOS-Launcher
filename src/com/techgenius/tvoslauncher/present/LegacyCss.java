package com.techgenius.tvoslauncher.present;

/**
 * Removes CSS cascade layers (@layer) for WebViews older than Chrome 99, which ignore layered
 * rules entirely (port of LegacyWebViewCss.kt): statement layers are dropped, block layers are
 * unwrapped in place.
 */
final class LegacyCss {
    private LegacyCss() { }

    private static final String LAYER = "@layer";

    static String unwrapCascadeLayers(String css) {
        if (!css.contains(LAYER)) return css;
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            int at = css.indexOf(LAYER, i);
            if (at == -1) {
                out.append(css, i, css.length());
                break;
            }
            out.append(css, i, at);
            int end = preludeEnd(css, at + LAYER.length());
            if (end == -1) {
                out.append(css, at, css.length());
                break;
            }
            char c = css.charAt(end);
            if (c == ';') {
                i = end + 1;
            } else if (c == '{') {
                int close = matchingBrace(css, end);
                if (close == -1) {
                    out.append(css, at, css.length());
                    break;
                }
                out.append(unwrapCascadeLayers(css.substring(end + 1, close)));
                i = close + 1;
            } else {
                out.append(css, at, end + 1);
                i = end + 1;
            }
        }
        return out.toString();
    }

    private static int preludeEnd(String css, int start) {
        for (int i = start; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == ';' || c == '{') return i;
            if (c == '\'' || c == '"') i = skipString(css, i);
            else if (c == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') i = skipComment(css, i);
        }
        return -1;
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '\'' || c == '"') i = skipString(css, i);
            else if (c == '/' && i + 1 < css.length() && css.charAt(i + 1) == '*') i = skipComment(css, i);
        }
        return -1;
    }

    private static int skipString(String css, int q) {
        char quote = css.charAt(q);
        for (int i = q + 1; i < css.length(); i++) {
            if (css.charAt(i) == '\\') {
                i++;
                continue;
            }
            if (css.charAt(i) == quote) return i;
        }
        return css.length() - 1;
    }

    private static int skipComment(String css, int start) {
        int end = css.indexOf("*/", start + 2);
        return end == -1 ? css.length() - 1 : end + 1;
    }
}
