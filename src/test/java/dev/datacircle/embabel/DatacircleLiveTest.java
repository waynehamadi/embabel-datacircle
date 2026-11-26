package dev.datacircle.embabel;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.embabel.agent.api.tool.Tool;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Calls each tool once on the live API: {@code DATACIRCLE_LIVE=1 ./mvnw test}. */
@EnabledIfEnvironmentVariable(named = "DATACIRCLE_LIVE", matches = "1")
class DatacircleLiveTest {

    @Test
    void eachToolAnswersOnTheLiveApi() {
        Map<String, Tool> tools = new TreeMap<>();
        for (Tool tool : DatacircleTools.toolGroup().getTools()) {
            tools.put(tool.getDefinition().getName(), tool);
        }
        String apis = text(tools.get("datacircle_list_apis").call("{\"query\": \"email\"}"));
        assertTrue(apis.contains("(email-finder)"), apis);
        String best = text(tools.get("datacircle_best_plans").call("{\"api\": \"email-finder\", \"limit\": 3}"));
        assertTrue(best.contains("\n1. ") && best.contains("https://datacircle.dev/"), best);
        String plans = text(tools.get("datacircle_search_plans").call("{\"query\": \"hunter\", \"sort\": \"price\", \"limit\": 3}"));
        assertTrue(plans.startsWith("Found "), plans);
        String provider = text(tools.get("datacircle_provider").call("{\"provider\": \"Hunter\"}"));
        assertTrue(provider.startsWith("Hunter") && provider.contains("rank"), provider);
        System.out.println(apis.lines().findFirst().orElse("") + "\n" + best + "\n" + plans + "\n" + provider);
    }

    private static String text(Tool.Result result) {
        return assertInstanceOf(Tool.Result.Text.class, result, result.toString()).getContent();
    }
}
