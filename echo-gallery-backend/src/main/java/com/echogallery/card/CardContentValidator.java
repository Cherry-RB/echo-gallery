package com.echogallery.card;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CardContentValidator implements ConstraintValidator<ValidCardContent, CardContentRequest> {

    @Override
    public boolean isValid(CardContentRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if ("link".equals(request.getType()) && isBlank(request.getUrl())) {
            addViolation(context, "連結類卡片必須提供來源網址", "url");
            valid = false;
        } else if (!isBlank(request.getUrl()) && !isHttpUrl(request.getUrl())) {
            addViolation(context, "來源網址必須是有效的 HTTP 或 HTTPS 網址", "url");
            valid = false;
        }

        if (!isBlank(request.getCoverImageUrl()) && !isHttpUrl(request.getCoverImageUrl())) {
            addViolation(context, "封面圖片網址必須是有效的 HTTP 或 HTTPS 網址", "coverImageUrl");
            valid = false;
        }

        if (!hasValidTags(request.getTags(), context)) {
            valid = false;
        }

        return valid;
    }

    private boolean hasValidTags(List<String> tags, ConstraintValidatorContext context) {
        if (tags == null) {
            return true;
        }

        boolean valid = true;
        Set<String> normalizedTags = new HashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }

            String normalizedTag = tag.trim();
            if (normalizedTag.isEmpty()) {
                addViolation(context, "標籤不可為空", "tags");
                valid = false;
                continue;
            }
            if (normalizedTag.length() > 50) {
                addViolation(context, "單一標籤不可超過 50 個字元", "tags");
                valid = false;
            }
            if (!normalizedTags.add(normalizedTag)) {
                addViolation(context, "標籤不可重複", "tags");
                valid = false;
            }
        }
        return valid;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && Set.of("http", "https").contains(scheme.toLowerCase(Locale.ROOT))
                    && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addViolation(ConstraintValidatorContext context, String message, String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
