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

package org.springframework.ai.a2a.core.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.a2a.spec.AgentCard;

import org.springframework.util.Assert;

/**
 * Registry for managing A2A agents (both local and remote).
 *
 * <p>
 * This registry provides a centralized place to register and access A2A agents by name.
 * It supports both:
 * <ul>
 * <li>Remote agents - Created via agent URL, automatically discovered</li>
 * <li>Local agents - Directly registered A2AAgent instances</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre class="code">
 * // Create registry
 * A2AAgentRegistry registry = new A2AAgentRegistry();
 *
 * // Register remote agents
 * registry.registerAgent("weather", "http://localhost:10001/a2a");
 * registry.registerAgent("airbnb", "http://localhost:10002/a2a");
 *
 * // Get agent and send message
 * A2AAgent weatherAgent = registry.getAgent("weather");
 * A2AResponse response = weatherAgent.sendMessage(A2ARequest.of("What's the weather?"));
 *
 * // Check if agent is registered
 * if (registry.isRegistered("weather")) {
 *     System.out.println("Weather agent is available");
 * }
 * </pre>
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 * @see A2AAgent
 * @see A2AAgentClient
 */
public class A2AAgentRegistry {

	private final Map<String, A2AAgent> agents = new ConcurrentHashMap<>();

	/**
	 * Register a remote agent with the registry by URL.
	 * <p>
	 * The agent card will be automatically discovered from the agent's endpoint.
	 * @param agentName a logical name for the agent (e.g., "weather", "airbnb")
	 * @param agentUrl the A2A endpoint URL for the agent
	 * @return the AgentCard for the registered agent
	 * @throws RuntimeException if agent card cannot be fetched
	 */
	public AgentCard registerAgent(String agentName, String agentUrl) {
		Assert.hasText(agentName, "agentName cannot be null or empty");
		Assert.hasText(agentUrl, "agentUrl cannot be null or empty");

		A2AAgentClient client = DefaultA2AAgentClient.builder().agentUrl(agentUrl).build();

		this.agents.put(agentName, client);

		return client.getAgentCard();
	}

	/**
	 * Register an agent instance directly with the registry.
	 * <p>
	 * This is useful for registering local agents or custom agent implementations.
	 * @param agentName a logical name for the agent
	 * @param agent the agent instance to register
	 * @return the AgentCard for the registered agent
	 */
	public AgentCard registerAgent(String agentName, A2AAgent agent) {
		Assert.hasText(agentName, "agentName cannot be null or empty");
		Assert.notNull(agent, "agent cannot be null");

		this.agents.put(agentName, agent);

		return agent.getAgentCard();
	}

	/**
	 * Get an agent by name.
	 * @param agentName the logical name of the agent
	 * @return the agent instance
	 * @throws IllegalArgumentException if agent is not registered
	 */
	public A2AAgent getAgent(String agentName) {
		A2AAgent agent = this.agents.get(agentName);
		if (agent == null) {
			throw new IllegalArgumentException("Agent not registered: " + agentName);
		}
		return agent;
	}

	/**
	 * Get the agent card for a registered agent.
	 * @param agentName the logical name of the agent
	 * @return the AgentCard for the agent
	 * @throws IllegalArgumentException if agent is not registered
	 */
	public AgentCard getAgentCard(String agentName) {
		A2AAgent agent = getAgent(agentName);
		return agent.getAgentCard();
	}

	/**
	 * Check if an agent is registered.
	 * @param agentName the logical name of the agent
	 * @return true if the agent is registered
	 */
	public boolean isRegistered(String agentName) {
		return this.agents.containsKey(agentName);
	}

	/**
	 * Unregister an agent.
	 * @param agentName the logical name of the agent to unregister
	 * @return true if the agent was registered and has been removed
	 */
	public boolean unregisterAgent(String agentName) {
		return this.agents.remove(agentName) != null;
	}

	/**
	 * Get all registered agent names.
	 * @return a set of registered agent names
	 */
	public java.util.Set<String> getRegisteredAgentNames() {
		return java.util.Collections.unmodifiableSet(this.agents.keySet());
	}

	/**
	 * Clear all registered agents.
	 */
	public void clear() {
		this.agents.clear();
	}

}
