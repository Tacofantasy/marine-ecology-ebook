package com.marine.ecobook.ebook.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Centralized HTML sanitizer using OWASP Java HTML Sanitizer.
 * <p>
 * Allows basic block-level text, headings, lists, tables, links, images,
 * and necessary attributes. Removes script tags, event attributes,
 * javascript: URLs, and dangerous inline styles.
 */
@Component
public class HtmlSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowUrlProtocols("http", "https")
            .allowElements(
                    "p", "br", "hr",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li",
                    "blockquote", "pre", "code",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "a", "img",
                    "strong", "em", "b", "i", "u", "s", "del", "ins",
                    "span", "div"
            )
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("colspan", "rowspan").onElements("th", "td")
            .allowAttributes("class").onElements("p", "span", "div", "pre", "code")
            .allowAttributes("target").onElements("a")
            .allowAttributes("align").onElements("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "th", "td")
            .toFactory();

    /**
     * Sanitize the given HTML, returning safe HTML.
     *
     * @param dirtyHtml raw HTML input
     * @return sanitized HTML, or empty string if input is blank
     */
    public String sanitize(String dirtyHtml) {
        if (dirtyHtml == null || dirtyHtml.isBlank()) {
            return "";
        }
        return POLICY.sanitize(dirtyHtml);
    }

    /**
     * Sanitize and then extract plain text (strip all tags).
     *
     * @param dirtyHtml raw HTML input
     * @return plain text without tags, or empty string
     */
    public String toPlainText(String dirtyHtml) {
        String sanitized = sanitize(dirtyHtml);
        if (sanitized.isBlank()) {
            return "";
        }
        return sanitized
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace('\u00a0', ' ')
                .trim();
    }
}
