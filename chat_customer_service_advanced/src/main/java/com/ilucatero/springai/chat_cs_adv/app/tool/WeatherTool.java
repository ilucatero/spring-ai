package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;

import java.util.List;
import java.util.Random;

public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private final Random random = new Random();
    private final ToolExecutionInfo.Recorder toolExecutionRecorder;

    public WeatherTool(ToolExecutionInfo.Recorder toolExecutionRecorder) {
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    /**
     * Get the current weather for a given location.
     *
     * @param location the location to get weather for
     * @return weather description
     */
    @Tool(description = "Get the current weather for a given location")
    public String getCurrentWeatherTool(@ToolParam(description = "Location name") String location) {
        return toolExecutionRecorder.record("getCurrentWeather",
                List.of("location=" + location),
                () -> getCurrentWeather(location));
    }

    /**
     * Get the weather forecast for a location.
     *
     * @param location the location to get forecast for
     * @param days     number of days to forecast
     * @return weather forecast
     */
    @Tool(description = "Get the weather forecast for a given location and number of days")
    public String getWeatherForecastTool(
            @ToolParam(description = "Location name") String location,
            @ToolParam(description = "Number of days (1-7)") int days) {
        return toolExecutionRecorder.record("getWeatherForecast",
                List.of("location=" + location, "days=" + days),
                () -> getWeatherForecast(location, days));
    }

    /* ** Helper methods to simulate weather data and forecast ** */

    private String getCurrentWeather(String location) {
        log.info("Getting weather for location: {}", location);

        // Simulate weather data (in production, call real API)
        var temperature = 15 + random.nextInt(20); // 15-35 degrees
        var conditions = new String[] { "sunny", "cloudy", "partly cloudy", "rainy" };
        var condition = conditions[random.nextInt(conditions.length)];

        return String.format("The weather in %s is currently %d°C and %s.",
                location, temperature, condition);
    }

    private String getWeatherForecast(String location, Integer days) {
        log.info("Getting {}-day forecast for location: {}", days, location);

        // Validate input
        if (days < 1 || days > 7) {
            return "Forecast is available for 1 to 7 days only.";
        }

        StringBuilder forecast = new StringBuilder();
        forecast.append(String.format("%d-day forecast for %s:\n", days, location));

        for (int i = 1; i <= days; i++) {
            var temp = 15 + random.nextInt(20);
            var conditions = new String[] { "sunny", "cloudy", "rainy", "partly cloudy" };
            var condition = conditions[random.nextInt(conditions.length)];
            forecast.append(String.format("Day %d: %d°C, %s\n", i, temp, condition));
        }

        return forecast.toString();
    }
}
