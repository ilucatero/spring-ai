# Chat

This applications demonstrates both patterns:

* Stateless Chat - Each request is independent. The model has no memory of previous messages.
* Stateful Conversation - Each request includes conversation history managed by Spring AI's MessageWindowChatMemory.
  The model maintains context across multiple turns with automatic sliding-window trimming. This is what production applications require.

## How to run the Application

### Verify deployment

Ensure the `.env` file exists in the root directory filled with required credentials.

### Execute using shell scripts

```shell
echo "Loading environment variables from .env file"
ENV_FILE="./.env"
set -a              # active l'export automatique
source "$ENV_FILE"  # charge le fichier
set +a              # désactive l'export automatique

echo "Executing JAR file"
JAR_FILE="./target/spring-ai-chat-1.0.0.jar"
java -jar "$JAR_FILE"
```

### Access to web page

Open http://localhost:8080 in your browser.

The application provides a web interface with two chat implementations side-by-side : Stateless Chat (Left Panel) and Stateful Chat (Right Panel)
