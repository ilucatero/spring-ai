package com.ilucatero.springai.chat_cs_adv.app.model;

import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

public record OrderToolSet(ToolExecutionInfo.Recorder toolExecutionRecorder,
        OrderStatusTool orderStatusTool,
        OrderDeliveranceTool orderDeliveranceTool) {

    public Object[] getTools() {
        return new Object[] { orderStatusTool, orderDeliveranceTool };
    }
}
