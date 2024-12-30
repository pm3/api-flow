package eu.aston.flow.def;

import java.util.Map;

public record CronJob(String expression,
                      Map<String, Object> params) {
}
