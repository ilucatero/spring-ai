package com.ilucatero.springai.chat_cs.app.repositories;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/** In-memory repository backed by a thread-safe set. */
@Repository
public class ConversationRepository<T> {

	private final Set<T> conversations = ConcurrentHashMap.newKeySet();

	public T save(T conversation) {
		conversations.add(conversation);
		return conversation;
	}

	public boolean delete(T conversation) {
		return conversations.remove(conversation);
	}

	public List<T> findAll() {
		return List.copyOf(conversations);
	}

	public void clear() {
		conversations.clear();
	}
}
