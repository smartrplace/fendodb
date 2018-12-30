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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.smartrplace.logging.fendodb.FendoTimeSeries;

class PropertiesMatcher implements TimeSeriesMatcher {
	
	private final String key;
	private final Collection<String> values;
	private final boolean valueIgnoreCase;
	
	PropertiesMatcher(String key, Collection<String> values, boolean valueIgnoreCase) {
		this.key = Objects.requireNonNull(key);
		this.values = values == null || values.size() == 0 ? null : new ArrayList<>(Objects.requireNonNull(values));
		this.valueIgnoreCase = valueIgnoreCase;
	}
	
	@Override
	public boolean matches(final FendoTimeSeries timeSeries) {
		if (values == null || values.isEmpty())
			return true;
		final List<String> values = timeSeries.getProperties(key);
		if (values == null || values.isEmpty())
			return false;
		if (valueIgnoreCase) {
			return values.stream()
				.filter(v -> this.values.stream().filter(v2 -> v2.equalsIgnoreCase(v)).findAny().isPresent())
				.findAny().isPresent();
		} else {
			return values.stream()
				.filter(v -> this.values.contains(v))
				.findAny().isPresent();
		}
	}
	
	@Override
	public String toString() {
		return "PropertiesMatcher(" + key + "=" + values + ")";
	}
	

}
