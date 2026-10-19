# Customer Service Chat - Advanced

Spring Boot application demonstrating AI-assisted customer request analysis and
questionnaire generation with Spring AI.

The `ToolCallingAdvisor` hook intercepts cancellation tool calls before they can
run, checks the order status, and shows the order details. The cancellation tool
runs only after the user explicitly confirms.

## Features

- Check order status
- Check order delivery details
- Cancel order
