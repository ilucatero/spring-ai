# Customer Service Chat - Advanced

Spring Boot application demonstrating an AI-powered customer support agent
built with Spring AI. The agent can look up order information, retrieve
product return policies from an MCP server, and prepare order cancellations
for explicit customer confirmation.

## Features

- Check an order's status and delivery details
- Retrieve return policies through the MCP server
- Prepare a cancellation after checking the order
- Cancel an order only after the user confirms the request

## Requirements

- Java 25
- Maven
- An OpenAI-compatible chat model endpoint and API key

## Configuration

Create `chat_customer_service_advanced/.env` from
[`chat_customer_service_advanced/.env.exemple`](./.env.exemple) and set:

```properties
OPENAI_ENDPOINT=
OPENAI_API_KEY=
OPENAI_MODEL=
```

## Run

Run both applications from the repository root, in separate terminals.

1. Start the MCP server:

   ```bash
   mvn -pl chat_customer_service_advanced_mcp spring-boot:run
   ```

   The server is provided by the [`chat_customer_service_advanced_mcp` Maven module](../chat_customer_service_advanced_mcp).
   It exposes the `getReturnPolicy` tool, backed by the policies in
   `chat_customer_service_advanced_mcp/src/main/resources/data/return-policies.json`.

2. In another terminal, start the chat application:

   ```bash
   mvn -pl chat_customer_service_advanced spring-boot:run
   ```

3. Open [http://localhost:8082](http://localhost:8082).

Start the MCP server before the chat application. The chat client connects to
the server at `http://localhost:8092/mcp`.

## MCP server

The chat application uses Spring AI's MCP client to connect to the separate
`chat_customer_service_advanced_mcp` module. That module uses
`spring-ai-starter-mcp-server-webmvc` and publishes the `getReturnPolicy` tool
over Streamable HTTP. For its implementation and integration test, see the
[`chat_customer_service_advanced_mcp` module](../chat_customer_service_advanced_mcp).
