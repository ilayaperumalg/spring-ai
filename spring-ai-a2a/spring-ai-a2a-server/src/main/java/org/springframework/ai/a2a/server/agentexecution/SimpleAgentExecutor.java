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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.Artifact;
import io.a2a.spec.Event;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Part;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;

import org.springframework.ai.a2a.core.A2ARequest;
import org.springframework.ai.a2a.core.A2AResponse;

/**
 * Abstract base class for implementing A2A agent executors with simplified lifecycle
 * hooks.
 *
 * <p>
 * This class implements both the A2A Java SDK {@link AgentExecutor} interface and the
 * Spring AI {@link AgentExecutorLifecycle} interface, providing a bridge between the two.
 * It handles all the boilerplate of task management, allowing implementations to focus on
 * the core agent logic.
 *
 * <p>
 * Implementations only need to implement the
 * {@link AgentExecutorLifecycle#onExecute(String, RequestContext, TaskUpdater)} method
 * from the lifecycle interface.
 *
 * <p>
 * Example implementation:
 *
 * <pre>
 * public class MyAgent extends SimpleAgentExecutor {
 *   public List&lt;Part&lt;?&gt;&gt; onExecute(String userInput, RequestContext context, TaskUpdater taskUpdater) {
 *     String response = processRequest(userInput);
 *     return List.of(new TextPart(response));
 *   }
 * }
 * </pre>
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
public abstract class SimpleAgentExecutor implements AgentExecutor, AgentExecutorLifecycle {

	/**
	 * Standard AgentExecutor execute method with lifecycle management.
	 * <p>
	 * This method implements the full task lifecycle by delegating to the lifecycle
	 * hooks: 1. Submit task (if new) 2. Start work 3. Call
	 * {@link AgentExecutorLifecycle#onExecute(String, RequestContext, TaskUpdater)} 4.
	 * Add artifacts 5. Complete task
	 * <p>
	 * On error, calls
	 * {@link AgentExecutorLifecycle#onError(Exception, RequestContext, TaskUpdater)}.
	 */
	@Override
	public final void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);

		try {
			// Submit and start task if it's a new task
			if (context.getTask() == null) {
				taskUpdater.submit();
			}
			taskUpdater.startWork();

			// Extract user input from the request
			String userInput = context.getUserInput(" ");

			// Execute agent logic via lifecycle hook
			List<Part<?>> responseParts = onExecute(userInput, context, taskUpdater);

			// Add response as artifacts
			if (responseParts != null && !responseParts.isEmpty()) {
				taskUpdater.addArtifact(responseParts);
			}

			// Complete the task
			taskUpdater.complete();
		}
		catch (Exception e) {
			onError(e, context, taskUpdater);
		}
	}

	/**
	 * Standard AgentExecutor cancel method with lifecycle management.
	 */
	@Override
	public final void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
		onCancel(context, taskUpdater);
	}

	/**
	 * Executes the agent synchronously and returns the response.
	 * <p>
	 * This method provides synchronous execution support for agents that implement
	 * {@link AgentExecutorLifecycle}. It:
	 * <ul>
	 * <li>Creates an EventQueue to collect responses</li>
	 * <li>Invokes the AgentExecutor with RequestContext and EventQueue</li>
	 * <li>Waits for task completion and collects all artifacts</li>
	 * <li>Returns the collected response synchronously</li>
	 * </ul>
	 * @param request the A2A request
	 * @return the A2A response
	 */
	public A2AResponse executeSynchronous(A2ARequest request) {
		try {
			// Create a synchronous event queue to collect responses
			SynchronousEventQueue eventQueue = new SynchronousEventQueue();

			// Build request context from A2ARequest
			RequestContext.Builder contextBuilder = new RequestContext.Builder();

			// Build message with contextId and taskId from request
			io.a2a.spec.Message message = io.a2a.spec.Message.builder(request.getMessage())
				.contextId(request.getContextId())
				.taskId(request.getTaskId())
				.build();

			// Create MessageSendParams from the message with contextId/taskId
			contextBuilder.setParams(new io.a2a.spec.MessageSendParams(message, null, null));

			RequestContext context = contextBuilder.build();

			// Execute the agent
			execute(context, eventQueue);

			// Wait for completion and collect response
			A2AResponse response = eventQueue.awaitCompletion(30);

			return response;
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to execute agent", e);
		}
	}

	/**
	 * Synchronous EventQueue implementation that collects events and waits for task
	 * completion.
	 */
	private static class SynchronousEventQueue extends EventQueue {

		private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();

		private final List<Part<?>> responseParts = new ArrayList<>();

		private volatile boolean completed = false;

		private volatile boolean failed = false;

		private volatile Exception error;

		@Override
		public void enqueueEvent(Event event) {
			this.events.offer(event);
			processEvent(event);
		}

		private void processEvent(Event event) {
			if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
				Artifact artifact = artifactEvent.artifact();
				if (artifact != null && artifact.parts() != null) {
					synchronized (this.responseParts) {
						this.responseParts.addAll(artifact.parts());
					}
				}
			}
			else if (event instanceof TaskStatusUpdateEvent statusEvent) {
				TaskState state = statusEvent.status().state();
				if (state == TaskState.COMPLETED) {
					synchronized (this) {
						this.completed = true;
						this.notifyAll();
					}
				}
				else if (state == TaskState.FAILED || state == TaskState.CANCELED) {
					synchronized (this) {
						this.failed = true;
						this.error = new RuntimeException("Task ended with state: " + state);
						this.notifyAll();
					}
				}
			}
		}

		public A2AResponse awaitCompletion(int timeoutSeconds) throws InterruptedException {
			synchronized (this) {
				long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

				while (!this.completed && !this.failed) {
					long remaining = deadline - System.currentTimeMillis();
					if (remaining <= 0) {
						throw new RuntimeException("Agent execution timed out after " + timeoutSeconds + " seconds");
					}
					this.wait(remaining);
				}

				if (this.failed) {
					throw new RuntimeException("Agent execution failed", this.error);
				}

				synchronized (this.responseParts) {
					return A2AResponse.of(new ArrayList<>(this.responseParts));
				}
			}
		}

		@Override
		public EventQueue tap() {
			throw new UnsupportedOperationException("Synchronous event queue does not support tapping");
		}

		@Override
		public void awaitQueuePollerStart() throws InterruptedException {
			// No-op for synchronous execution
		}

		@Override
		public void signalQueuePollerStarted() {
			// No-op for synchronous execution
		}

		@Override
		public void close() {
			// No-op for synchronous execution
		}

		@Override
		public void close(boolean immediate) {
			// No-op for synchronous execution
		}

		@Override
		public void close(boolean immediate, boolean notifyParent) {
			// No-op for synchronous execution
		}

	}

}
