# Customer Service Chat

Spring Boot application demonstrating AI-assisted customer request analysis and
questionnaire generation with Spring AI.

## Features

- **Simple chat** analyzes a customer request and returns a structured result
  with a category, priority, sentiment, and summary.
- **Conversational chat** remembers context while helping create a questionnaire:
  it proposes a title and up to five questions, then lets the user approve the
  proposal or ask the AI to regenerate it.
- A questionnaire proposal is returned as a `QuestionnaireAnalysis` object
  (`title` and `questions`), so the response can be processed as data rather
  than only displayed as text.

## Requirements

- Java 25
- Maven
- An OpenAI-compatible chat model endpoint and API key

## Configuration

Copy `.env.exemple` to `.env` in this module directory and fill in the values:

```dotenv
OPENAI_ENDPOINT=https://api.openai.com
OPENAI_API_KEY=your-api-key
OPENAI_MODEL=your-model-name
```

The application imports this file from its current working directory. Do not
commit real credentials.

## Run locally

From the `chat_customer_service` directory:

```bash
cp .env.exemple .env
# Edit .env and set the endpoint, API key, and model.
mvn spring-boot:run
```

The application listens on port `8081`. Open
<http://localhost:8081> for the home page:

- <http://localhost:8081/simple-chat> — stateless customer request analysis
- <http://localhost:8081/conversation-chat> — questionnaire generation flow

## Test

From the repository root:

```bash
mvn -pl chat_customer_service -am test
```

## Questionnaire conversation flow

1. Start a conversation. The service creates a conversation ID and greets the
   user.
2. Send the customer context. The AI returns a title and a list of suggested
   questions, then asks whether the proposal is acceptable.
3. Choose one of the decisions:
   - `APPROVE` completes the flow and returns `CREATED`.
   - `REGENERATE` asks the AI to produce an improved proposal using the
     conversation context and the previous draft.
4. A regenerated questionnaire is also returned for approval. The flow can be
   repeated until the user approves it.

### Conversation API

Start a conversation:

```http
POST /api/conversation/start
```

Example response:

```json
{
  "conversationId": "generated-conversation-id",
  "message": "Hello, share your customer context so I can suggest a list of questions to add to your questionnaire."
}
```

Generate a proposal:

```http
POST /api/conversation/chat
Content-Type: application/json

{
  "conversationId": "generated-conversation-id",
  "message": "We run a small online bookstore and want to learn why customers return books."
}
```

The response includes `status: "AWAITING_APPROVAL"`, a `message` asking the user
to decide, and an `answer` object:

```json
{
  "conversationId": "generated-conversation-id",
  "status": "AWAITING_APPROVAL",
  "message": "Does this questionnaire work for you? Choose Approve or Regenerate.",
  "answer": {
    "title": "Book Return Experience",
    "questions": [
      "What was the main reason for returning the book?",
      "How would you rate the condition of the book when it arrived?"
    ]
  }
}
```

Approve the pending proposal:

```http
POST /api/conversation/chat
Content-Type: application/json

{
  "conversationId": "generated-conversation-id",
  "decision": "APPROVE"
}
```

To regenerate instead, send `"decision": "REGENERATE"` with the same endpoint.
The response has the same questionnaire shape and a new `AWAITING_APPROVAL`
status.

Other conversation endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/conversation/{conversationId}/history` | Read the conversation history and whether approval is pending |
| `DELETE` | `/api/conversation/{conversationId}` | Clear the conversation and its pending proposal |

The customer request analysis endpoint is `POST /api/chat` with a JSON body
containing a `message` string.

## Data retention

Conversation messages are stored in an in-memory sliding window (up to ten
messages). Pending questionnaire proposals are also held in memory. They are
not durable and are lost when the application restarts.
