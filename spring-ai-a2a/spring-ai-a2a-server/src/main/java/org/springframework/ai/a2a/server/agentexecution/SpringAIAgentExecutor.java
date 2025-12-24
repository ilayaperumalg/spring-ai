/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.a2a.server.agentexecution;

import java.util.List;

import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Spring AI-specific agent executor that integrates with {@link ChatClient}.
 *
 * <p>
 * This class extends {@link SimpleAgentExecutor} and provides Spring AI ChatClient
 * integration. It implements both {@link io.a2a.server.agentexecution.AgentExecutor} and
 * {@link AgentExecutorLifecycle}, making it easy to create Spring AI-based agents with
 * minimal code.
 *
 * <p>
 * Implementations only need to provide a system prompt via {@link #getSystemPrompt()},
 * and the ChatClient handles the rest.
 *
 * <p>
 * Example implementation:
 *
 * <pre>
 * public class MyAgent extends SpringAIAgentExecutor {
 *   public MyAgent(ChatClient chatClient) {
 *     super(chatClient);
 *   }
 *
 *   protected String getSystemPrompt() {
 *     return "You are a helpful assistant that...";
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more complex scenarios where you need full control over the ChatClient interaction
 * or want to use additional Spring AI features, override
 * {@link AgentExecutorLifecycle#onExecute(String, RequestContext, TaskUpdater)}.
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
public abstract class SpringAIAgentExecutor extends SimpleAgentExecutor {

	private final ChatClient chatClient;

	/**
	 * Create a new SpringAIAgentExecutor with the given ChatClient.
	 * @param chatClient the ChatClient for LLM interactions
	 */
	protected SpringAIAgentExecutor(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	/**
	 * Get the system prompt for this agent.
	 * <p>
	 * Subclasses should override this method to provide their own system prompt that
	 * defines the agent's behavior and capabilities.
	 * @return the system prompt
	 */
	protected abstract String getSystemPrompt();

	/**
	 * Get the ChatClient instance.
	 * <p>
	 * Subclasses can access the ChatClient for advanced usage scenarios.
	 * @return the ChatClient
	 */
	protected ChatClient getChatClient() {
		return this.chatClient;
	}

	/**
	 * Execute the agent logic using ChatClient.
	 * <p>
	 * Default implementation uses the system prompt from {@link #getSystemPrompt()} and
	 * the user input to call the ChatClient. Subclasses can override this method for more
	 * complex interactions.
	 * @param userInput the user's input extracted from the request
	 * @param context the request context
	 * @param taskUpdater the task updater for managing task state
	 * @return list of response parts (e.g., TextPart, ImagePart)
	 * @throws Exception if execution fails
	 */
	@Override
	public List<Part<?>> onExecute(String userInput, RequestContext context, TaskUpdater taskUpdater) throws Exception {
		String systemPrompt = getSystemPrompt();
		String response = this.chatClient.prompt().system(systemPrompt).user(userInput).call().content();
		return List.of(new TextPart(response));
	}

}
