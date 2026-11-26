package dev.datacircle.embabel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns the API's answers into the short text each tool gives the model, one plan or API a line. */
final class Formats {

    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_PROVIDER_APIS = 100;
    private static final String NEW_LINE = "\n";

    private Formats() {
    }

    static String formatApis(Object apis, String query) {
        if (!(apis instanceof List<?> list)) {
            throw unexpected("the list of APIs is not a list");
        }
        if (list.isEmpty()) {
            return "The Datacircle API returned no APIs.";
        }
        List<String> words = words(query);
        List<String> lines = new ArrayList<>();
        for (Object api : list) {
            String name = required(api, "api");
            String slug = required(api, "slug");
            if (!matchesAll(name + " " + slug, words)) {
                continue;
            }
            StringBuilder line = new StringBuilder("- ")
                    .append(truncate(name))
                    .append(" (")
                    .append(slug)
                    .append("): ")
                    .append(count(field(api, "plans"), "plan"))
                    .append(" from ")
                    .append(count(field(api, "providers"), "provider"))
                    .append('.');
            Object best = field(api, "best");
            if (best instanceof Map) {
                line.append(" Best: ")
                        .append(truncate(required(best, "provider")))
                        .append(", ")
                        .append(truncate(required(best, "plan")))
                        .append(", ")
                        .append(score(field(best, "score")))
                        .append(", ")
                        .append(price(field(best, "perThousand"), null))
                        .append('.');
            }
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            return "No API's name or slug holds every word of \"" + query.trim() + "\". Leave the query empty to list "
                    + list.size() + " APIs.";
        }
        return String.join(NEW_LINE, lines) + NEW_LINE + lines.size() + " of " + list.size()
                + " APIs. Pass a slug to datacircle_best_plans or datacircle_search_plans.";
    }

    static String formatBestPlans(Object response) {
        List<?> plans = list(response, "plans");
        StringBuilder result = new StringBuilder()
                .append(required(response, "api"))
                .append(" (")
                .append(required(response, "slug"))
                .append("): ");
        if (plans.isEmpty()) {
            return result.append("no plans found.").toString();
        }
        result.append("the best ")
                .append(plans.size())
                .append(" of ")
                .append(count(field(response, "total"), "plan"))
                .append('.');
        appendIfPresent(result, "Sheet", text(response, "sheetPage"));
        for (Object plan : plans) {
            result.append(NEW_LINE).append(formatPlan(plan, false));
        }
        return result.toString();
    }

    static String formatSearch(Object response, int requestedOffset) {
        List<?> plans = list(response, "plans");
        Object total = field(response, "total");
        int offset = field(response, "offset") instanceof BigDecimal n ? n.intValue() : requestedOffset;
        if (plans.isEmpty()) {
            if (total instanceof BigDecimal n && n.signum() > 0) {
                return "Found " + count(total, "plan") + ", but the offset " + offset
                        + " is past the last one. Use an offset below " + n.toPlainString() + ".";
            }
            return "No plans found.";
        }
        StringBuilder result = new StringBuilder("Found ")
                .append(count(total, "plan"))
                .append("; showing ")
                .append(offset + 1)
                .append(" to ")
                .append(offset + plans.size())
                .append('.');
        appendIfPresent(result, "Sheet", text(response, "sheetPage"));
        for (Object plan : plans) {
            result.append(NEW_LINE).append(formatPlan(plan, true));
        }
        return result.toString();
    }

    static String formatProvider(Object response) {
        List<?> apis = list(response, "apis");
        StringBuilder result = new StringBuilder(required(response, "provider"));
        String website = text(response, "website");
        if (!website.isEmpty()) {
            result.append(" (").append(website).append(')');
        }
        if (apis.isEmpty()) {
            return result.append(" sells no API on Datacircle.").toString();
        }
        result.append(" sells ").append(plural(apis.size(), "API")).append(". Its rank among each API's providers:");
        int shown = 0;
        for (Object api : apis) {
            if (shown == MAX_PROVIDER_APIS) {
                result.append(NEW_LINE)
                        .append("And ")
                        .append(plural(apis.size() - shown, "more API"))
                        .append('.');
                break;
            }
            shown++;
            Object rank = field(api, "rank");
            List<?> plans = list(api, "plans");
            result.append(NEW_LINE)
                    .append("- ")
                    .append(truncate(required(api, "api")))
                    .append(" (")
                    .append(required(api, "slug"))
                    .append("): ")
                    .append(rank instanceof BigDecimal n ? "rank " + n.intValue() : "not ranked")
                    .append(", ")
                    .append(plural(plans.size(), "plan"))
                    .append('.');
            Object best = bestScored(plans);
            if (best != null) {
                result.append(" Best: ")
                        .append(truncate(required(best, "plan")))
                        .append(", ")
                        .append(score(field(best, "score")))
                        .append(", ")
                        .append(price(field(best, "perThousand"), field(best, "per")))
                        .append('.');
            }
        }
        return result.toString();
    }

    private static Object bestScored(List<?> plans) {
        Object best = null;
        BigDecimal bestScore = null;
        for (Object plan : plans) {
            if (field(plan, "score") instanceof BigDecimal score && (bestScore == null || score.compareTo(bestScore) > 0)) {
                best = plan;
                bestScore = score;
            }
        }
        return best;
    }

    private static String formatPlan(Object plan, boolean withApi) {
        StringBuilder line = new StringBuilder();
        Object rank = field(plan, "rank");
        line.append(!withApi && rank instanceof BigDecimal n ? n.intValue() + ". " : "- ")
                .append(truncate(required(plan, "provider")))
                .append(", ")
                .append(truncate(required(plan, "plan")));
        if (withApi) {
            line.append(" (").append(truncate(required(plan, "api"))).append(')');
        }
        line.append(": ");
        if (withApi && rank instanceof BigDecimal n) {
            line.append("rank ").append(n.intValue()).append(", ");
        }
        line.append(score(field(plan, "score")))
                .append(", ")
                .append(price(field(plan, "perThousand"), field(plan, "per")));
        if (field(plan, "notFoundPerThousand") instanceof BigDecimal notFound) {
            line.append(", ").append(dollars(notFound)).append(" per 1,000 not found");
        }
        String billing = text(plan, "billing");
        if (!billing.isEmpty()) {
            line.append(", ").append(billing);
        }
        if (field(plan, "latencyMs") instanceof BigDecimal latency) {
            line.append(", median ").append(latency.longValue()).append(" ms");
        }
        line.append('.');
        String page = text(plan, "page");
        if (!page.isEmpty()) {
            line.append(' ').append(page);
        }
        return line.toString();
    }

    private static String score(Object score) {
        return score instanceof BigDecimal n
                ? "score " + n.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "/100"
                : "not scored";
    }

    private static String price(Object perThousand, Object per) {
        if (!(perThousand instanceof BigDecimal amount)) {
            return "no listed price";
        }
        String unit = per instanceof String word && !word.isBlank() ? " " + word + "s" : "";
        return dollars(amount) + " per 1,000" + unit;
    }

    private static String dollars(BigDecimal amount) {
        return "$" + amount.stripTrailingZeros().toPlainString();
    }

    private static String count(Object number, String word) {
        return number instanceof BigDecimal n ? plural(n.longValue(), word) : "? " + word + "s";
    }

    private static String plural(long count, String word) {
        return String.format(Locale.ROOT, "%,d", count) + " " + word + (count == 1 ? "" : "s");
    }

    private static List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(fold(query).trim().split("[\\s-]+"));
    }

    private static boolean matchesAll(String text, List<String> words) {
        String folded = fold(text);
        return words.stream().allMatch(folded::contains);
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static void appendIfPresent(StringBuilder result, String label, String value) {
        if (!value.isEmpty()) {
            result.append(' ').append(label).append(": ").append(value);
        }
    }

    private static Object field(Object node, String name) {
        return node instanceof Map<?, ?> map ? map.get(name) : null;
    }

    private static String required(Object node, String name) {
        if (!(field(node, name) instanceof String value) || value.isBlank()) {
            throw unexpected(name + " is missing");
        }
        return value;
    }

    private static List<?> list(Object node, String name) {
        if (!(field(node, name) instanceof List<?> value)) {
            throw unexpected(name + " is not a list");
        }
        return value;
    }

    private static String text(Object node, String name) {
        return field(node, name) instanceof String value && !value.isBlank() ? value : "";
    }

    private static DatacircleException unexpected(String what) {
        return new DatacircleException("The Datacircle API returned an answer of an unexpected shape: " + what
                + ". Retry later, or read https://api.datacircle.dev/v1/openapi.json.");
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH - 3) + "...";
    }

    static boolean isStatus(RuntimeException error, int statusCode) {
        return error instanceof DatacircleException e && e.statusCode() == statusCode;
    }
}
