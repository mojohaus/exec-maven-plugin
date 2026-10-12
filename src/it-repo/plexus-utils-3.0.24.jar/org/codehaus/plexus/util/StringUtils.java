package org.codehaus.plexus.util;

public class StringUtils {
    public static String swapCase(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        for (char c : str.toCharArray()) {
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }
}
