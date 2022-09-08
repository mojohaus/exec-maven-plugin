package org.codehaus.mojo.exec.it.github322;

import javax.xml.transform.sax.SAXTransformerFactory;

public class Main {
    public static void main(final String... args) {
        System.out.println(
                "Main Result: <" +
                (
                    SAXTransformerFactory.class.getProtectionDomain().getCodeSource() != null ?
                            SAXTransformerFactory.class.getProtectionDomain().getCodeSource().getLocation() :
                            null
                ) +
                ">");
    }
}
