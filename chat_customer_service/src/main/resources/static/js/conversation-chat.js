const messagesDiv = document.getElementById('messages');
const chatForm = document.getElementById('chat-form');
const messageInput = document.getElementById('message-input');
const sendBtn = document.getElementById('send-btn');
const startBtn = document.getElementById('start-btn');
const clearBtn = document.getElementById('clear-btn');
const statusText = document.getElementById('status');

let conversationId = null;
let awaitingDecision = false;
let decisionOptions = null;

// Load saved conversation ID from localStorage
const savedConversationId = localStorage.getItem('conversationId');
if (savedConversationId) {
  conversationId = savedConversationId;
  loadHistory(conversationId)
    .then(data => {
      if (!data) {
        throw new Error('Conversation not found');
      }
      showHistory(data);
      if (data && data.awaitingDecision) {
        addMessage('ai', 'Does this questionnaire work for you? Choose Approve or Regenerate.');
        showDecisionOptions();
      }
      enableChat();
    })
    .catch(error => {
      console.error('Failed to load history:', error);
      localStorage.removeItem('conversationId');
      conversationId = null;
      disableChat();
    });

}

startBtn.addEventListener('click', async () => {
  startBtn.disabled = true;
  try {
    const response = await fetch('/api/conversation/start', {
      method: 'POST'
    });

    const data = await response.json();

    if (response.ok) {
      conversationId = data.conversationId;
      localStorage.setItem('conversationId', conversationId);
      enableChat();
      addMessage('ai', data.message);
    } else {
      alert('Failed to start conversation: ' + (data.error || 'Unknown error'));
    }
  } catch (error) {
    alert('Failed to connect to server');
  } finally {
    startBtn.disabled = false;
  }
});

clearBtn.addEventListener('click', async () => {
  if (!confirm('Clear conversation history?')) return;

  clearBtn.disabled = true;
  try {
    await fetch(`/api/conversation/${conversationId}`, {
      method: 'DELETE'
    });

    conversationId = null;
    localStorage.removeItem('conversationId');
    messagesDiv.innerHTML = '';
    disableChat();
  } catch (error) {
    alert('Failed to clear conversation');
  } finally {
    clearBtn.disabled = false;
  }
});

chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();

  const message = messageInput.value.trim();
  if (!message || !conversationId) return;

  addMessage('user', message);
  messageInput.value = '';
  sendBtn.disabled = true;

  const loadingId = addLoadingMessage();

  try {
    const response = await fetch('/api/conversation/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        conversationId,
        message
      })
    });

    const data = await response.json();
    removeLoadingMessage(loadingId);

    if (response.ok) {
      renderQuestionnaireReply(data);
    } else {
      addMessage('error', data.error || 'An error occurred');
    }
  } catch (error) {
    removeLoadingMessage(loadingId);
    addMessage('error', 'Failed to connect to server');
  } finally {
    sendBtn.disabled = awaitingDecision;
    messageInput.focus();
  }
});

function renderQuestionnaireReply(data) {
  if (data.status === 'CREATED') {
    removeDecisionOptions();
    awaitingDecision = false;
    messageInput.disabled = false;
    sendBtn.disabled = false;
    addMessage('ai', data.message);
    return;
  }

  addMessage('ai', data.answer);
  addMessage('ai', data.message);
  showDecisionOptions();
}

function showDecisionOptions() {
  removeDecisionOptions();
  awaitingDecision = true;
  messageInput.disabled = true;
  sendBtn.disabled = true;

  decisionOptions = document.createElement('div');
  decisionOptions.className = 'chat-controls';

  const approveButton = document.createElement('button');
  approveButton.className = 'btn btn-primary';
  approveButton.textContent = 'Approve questionnaire';
  approveButton.addEventListener('click', () => sendDecision('APPROVE'));

  const regenerateButton = document.createElement('button');
  regenerateButton.className = 'btn btn-secondary';
  regenerateButton.textContent = 'Regenerate questionnaire';
  regenerateButton.addEventListener('click', () => sendDecision('REGENERATE'));

  decisionOptions.append(approveButton, regenerateButton);
  messagesDiv.appendChild(decisionOptions);
  scrollToBottom();
}

function removeDecisionOptions() {
  if (decisionOptions) {
    decisionOptions.remove();
    decisionOptions = null;
  }
}

async function sendDecision(decision) {
  if (!conversationId || !awaitingDecision) return;

  decisionOptions.querySelectorAll('button').forEach(button => {
    button.disabled = true;
  });
  const loadingId = addLoadingMessage();

  try {
    const response = await fetch('/api/conversation/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ conversationId, decision })
    });

    const data = await response.json();
    removeLoadingMessage(loadingId);

    if (response.ok) {
      renderQuestionnaireReply(data);
    } else {
      addMessage('error', data.error || 'An error occurred');
      decisionOptions.querySelectorAll('button').forEach(button => {
        button.disabled = false;
      });
    }
  } catch (error) {
    removeLoadingMessage(loadingId);
    addMessage('error', 'Failed to connect to server');
    decisionOptions.querySelectorAll('button').forEach(button => {
      button.disabled = false;
    });
  }
}

async function loadHistory(convId) {
  try {
    const response = await fetch(`/api/conversation/${convId}/history`);
    if (response.status === 404) {
      throw new Error('Conversation not found');
    }
    const json = await response.json();
    if (!json) {
      throw new Error('Conversation not found');
    }
    return json;

  } catch (error) {
    console.error('Failed to load history:', error);
  }
}
async function showHistory(data) {
  try {
    if (data.messages && data.messages.length > 0) {
      data.messages.forEach(msg => {
        const messageType = msg.messageType || msg.type;
        if (messageType === 'USER') {
          const text = msg.text || (msg.contents && msg.contents[0] ? msg.contents[0].text : '');
          if (text) addMessage('user', text);
        } else if (messageType === 'ASSISTANT' || messageType === 'AI') {
          if (msg.text) addMessage('ai', msg.text);
        }
      });
    }
  } catch (error) {
    console.error('Failed to load history:', error);
  }
}

function enableChat() {
  chatForm.style.display = 'flex';
  startBtn.style.display = 'none';
  clearBtn.style.display = 'inline-block';
  statusText.textContent = `Active conversation: ${conversationId.substring(0, 8)}...`;
  messageInput.focus();
}

function disableChat() {
  removeDecisionOptions();
  awaitingDecision = false;
  chatForm.style.display = 'none';
  startBtn.style.display = 'inline-block';
  clearBtn.style.display = 'none';
  statusText.textContent = 'Click "Start Conversation" to begin';
  messageInput.disabled = false;
  sendBtn.disabled = false;
}

function addMessage(type, answer) {
  const messageDiv = document.createElement('div');
  messageDiv.className = `message ${type}`;

  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = type === 'user' ? 'You' : type === 'error' ? 'Error' : 'AI';

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  bubble.textContent = formatAnswer(answer);

  messageDiv.appendChild(label);
  messageDiv.appendChild(bubble);
  messagesDiv.appendChild(messageDiv);

  scrollToBottom();
}

function formatAnswer(answer) {
  let value = answer;
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      return value;
    }
  }

  if (value && Array.isArray(value.questions)) {
    const questions = value.questions.map((question, index) => `${index + 1}. ${question}`).join('\n');
    return value.title ? `${value.title}\n\n${questions}` : questions;
  }

  return typeof value === 'string' ? value : JSON.stringify(value);
}

function addLoadingMessage() {
  const messageDiv = document.createElement('div');
  messageDiv.className = 'message ai';
  messageDiv.id = 'loading-message';

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble loading';
  bubble.textContent = 'AI is thinking';

  messageDiv.appendChild(bubble);
  messagesDiv.appendChild(messageDiv);

  scrollToBottom();
  return 'loading-message';
}

function removeLoadingMessage(id) {
  const loadingMsg = document.getElementById(id);
  if (loadingMsg) {
    loadingMsg.remove();
  }
}

function scrollToBottom() {
  messagesDiv.scrollTop = messagesDiv.scrollHeight;
}
