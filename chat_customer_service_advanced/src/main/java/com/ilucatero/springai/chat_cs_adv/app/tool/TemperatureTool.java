package com.ilucatero.springai.chat_cs_adv.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;

import java.util.List;

public class TemperatureTool {

    private static final Logger log = LoggerFactory.getLogger(TemperatureTool.class);

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;

    public TemperatureTool(ToolExecutionInfo.Recorder toolExecutionRecorder) {
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    /** Convert temperature from Celsius to Fahrenheit. */
    @Tool(description = "Convert temperature from Celsius to Fahrenheit")
    public String celsiusToFahrenheit(@ToolParam(description = "Temperature in Celsius") double celsius) {
        return toolExecutionRecorder.record("celsiusToFahrenheit",
                List.of("celsius=" + celsius),
                () -> {
                    log.info("Converting {}°C to Fahrenheit", celsius);
                    double fahrenheit = (celsius * 9.0 / 5.0) + 32.0;
                    return String.format("%.1f°C = %.1f°F", celsius, fahrenheit);
                });
    }

    /** Convert temperature from Fahrenheit to Celsius. */
    @Tool(description = "Convert temperature from Fahrenheit to Celsius")
    public String fahrenheitToCelsius(@ToolParam(description = "Temperature in Fahrenheit") double fahrenheit) {
        return toolExecutionRecorder.record("fahrenheitToCelsius",
                List.of("fahrenheit=" + fahrenheit),
                () -> {
                    log.info("Converting {}°F to Celsius", fahrenheit);
                    double celsius = (fahrenheit - 32.0) * 5.0 / 9.0;
                    return String.format("%.1f°F = %.1f°C", fahrenheit, celsius);
                });
    }

}
