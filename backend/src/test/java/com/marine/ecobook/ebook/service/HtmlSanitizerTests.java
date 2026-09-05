package com.marine.ecobook.ebook.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTests {
    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void countsDecodedCharactersAndRejectsEncodedWhitespace() {
        assertEquals("鱼&虾<海>", sanitizer.toPlainText("<p>鱼&amp;虾&lt;海&gt;</p>"));
        assertEquals("", sanitizer.toPlainText("<p>&nbsp;&#160;&#xA0; </p><script>alert(1)</script>"));
    }

    @Test
    void stripsActiveContentAndKeepsLocalImages() {
        String result = sanitizer.sanitize("<p onclick='bad()'>海洋</p><img src='/uploads/content/fish.png' onerror='bad()'><a href='javascript:bad()'>链接</a>");
        assertFalse(result.contains("onclick"));
        assertFalse(result.contains("onerror"));
        assertFalse(result.contains("javascript:"));
        assertTrue(result.contains("/uploads/content/fish.png"));
    }
}
