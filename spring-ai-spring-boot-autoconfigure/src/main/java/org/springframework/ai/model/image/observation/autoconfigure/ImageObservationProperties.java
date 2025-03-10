/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.image.observation.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for image model observations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ConfigurationProperties(ImageObservationProperties.CONFIG_PREFIX)
public class ImageObservationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.image.observations";

	/**
	 * Whether to include the prompt content in the observations.
	 */
	private boolean includePrompt = false;

	public boolean isIncludePrompt() {
		return this.includePrompt;
	}

	public void setIncludePrompt(boolean includePrompt) {
		this.includePrompt = includePrompt;
	}

}
