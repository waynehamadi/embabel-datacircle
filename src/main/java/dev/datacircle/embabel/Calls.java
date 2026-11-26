package dev.datacircle.embabel;

import com.embabel.agent.api.tool.Tool;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * The four tools' calls: each reads the model's JSON arguments, calls the API and gives its answer as text, or an
 * error result whose message says what to do next.
 */
final class Calls {

    private final DatacircleClient client;

    Calls(String baseUrl, Duration timeout) {
        this.client = new DatacircleClient(baseUrl, timeout);
    }

    Tool.Result listApis(String input) {
        try {
            String query = string(arguments(input), "query");
            return Tool.Result.text(Formats.formatApis(client.listApis(), query));
        } catch (RuntimeException e) {
            return error(e, "");
        }
    }

    Tool.Result bestPlans(String input) {
        try {
            Map<?, ?> arguments = arguments(input);
            return Tool.Result.text(
                    Formats.formatBestPlans(client.bestPlans(string(arguments, "api"), integer(arguments, "limit"))));
        } catch (RuntimeException e) {
            return error(e, Formats.isStatus(e, 404) ? " Call datacircle_list_apis for every API's slug." : "");
        }
    }

    Tool.Result searchPlans(String input) {
        String api = null;
        try {
            Map<?, ?> arguments = arguments(input);
            api = string(arguments, "api");
            Integer offset = integer(arguments, "offset");
            Object found = client.searchPlans(
                    api, string(arguments, "query"), string(arguments, "sort"), integer(arguments, "limit"), offset);
            return Tool.Result.text(Formats.formatSearch(found, offset == null ? 0 : offset));
        } catch (RuntimeException e) {
            boolean unknownApi =
                    api != null && !api.isBlank() && (Formats.isStatus(e, 400) || Formats.isStatus(e, 404));
            return error(e, unknownApi ? " Call datacircle_list_apis for every API's name and slug." : "");
        }
    }

    Tool.Result provider(String input) {
        try {
            return Tool.Result.text(Formats.formatProvider(client.provider(string(arguments(input), "provider"))));
        } catch (RuntimeException e) {
            return error(e, "");
        }
    }

    /** The arguments object; an empty input is no argument at all. */
    private static Map<?, ?> arguments(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = Json.parse(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the arguments are not valid JSON");
        }
        if (parsed == null) {
            return Map.of();
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("the arguments must be a JSON object");
        }
        return map;
    }

    private static String string(Map<?, ?> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof BigDecimal || value instanceof Boolean) {
            return value.toString();
        }
        throw new IllegalArgumentException(name + " must be a string");
    }

    /** A whole number, given as a number or as its digits ("3"), as models sometimes write it. */
    private static Integer integer(Map<?, ?> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        try {
            BigDecimal number = value instanceof BigDecimal n ? n : new BigDecimal(((String) value).trim());
            return number.intValueExact();
        } catch (ArithmeticException | NumberFormatException | ClassCastException e) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }

    private static Tool.Result error(RuntimeException error, String hint) {
        String message = error.getMessage();
        return Tool.Result.error(
                (message == null || message.isBlank() ? "the Datacircle API request failed." : message) + hint, error);
    }
}
