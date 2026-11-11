package com.ilucatero.springai.chat_cs_adv.app.model;

import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

public record OrderToolSet(ToolExecutionInfo.Recorder toolExecutionRecorder,
        OrderStatusTool orderStatusTool,
        OrderDeliveranceTool orderDeliveranceTool,
        OrderCancelationTool orderCancelationTool) {

    public Object[] getTools() {
        return new Object[] { orderStatusTool, orderDeliveranceTool, orderCancelationTool };
    }
}
