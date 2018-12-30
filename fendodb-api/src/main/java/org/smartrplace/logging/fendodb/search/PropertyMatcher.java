/**
 * ﻿Copyright 2018 Smartrplace UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartrplace.logging.fendodb.search;

import java.util.List;
import java.util.Objects;

import org.smartrplace.logging.fendodb.FendoTimeSeries;

class PropertyMatcher implements TimeSeriesMatcher {

	private final String key;
	private final String value;
	private final boolean valueIgnoreCase;
	
	PropertyMatcher(String key, String value, boolean valueIgnoreCase) {
		this.key = Objects.requireNonNull(key);
		this.value = Objects.requireNonNull(value);
		this.valueIgnoreCase = valueIgnoreCase;
	}
	
	@Override
	public boolean matches(final FendoTimeSeries timeSeries) {
		final List<String> values = timeSeries.getProperties(key);
		if (values == null)
			return false;
		if (valueIgnoreCase) {
			return values.stream()
				.filter(v -> v.equalsIgnoreCase(value))
				.findAny().isPresent();
		} else {
			return values.contains(value);
		}
	}
	
	@Override
	public String toString() {
		return "PropertyMatcher(" + key + "=" + value + ")";
	}
	
}
