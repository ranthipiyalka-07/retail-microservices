package com.retail.notification.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain text templates with {{name}} placeholders. Values are inserted exactly as given (no HTML, no escaping rules). */
@Component
public class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9]*)\\s*\\}\\}");

    public record Rendered(String subject, String body) {}

    public Rendered render(String subjectTemplate, String bodyTemplate, Map<String, String> vars) {
        return new Rendered(Text.line(substitute(subjectTemplate, vars), 255), Text.body(substitute(bodyTemplate, vars)));
    }

    /** A placeholder without a value becomes empty text. */
    static String substitute(String template, Map<String, String> vars) {
        Matcher matcher = VARIABLE.matcher(template == null ? "" : template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = vars.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The placeholder names a template uses, in order of first use. */
    public static Set<String> placeholdersIn(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(template == null ? "" : template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}