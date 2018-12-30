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

import java.util.Objects;

import org.smartrplace.logging.fendodb.FendoTimeSeries;

class IdMatcher implements TimeSeriesMatcher {
	
	private final String id;
	private final boolean ignoreCase;
	private final boolean regexpMatching;
	
	IdMatcher(String id, boolean ignoreCase, boolean regexpMatching) {
		this.id = Objects.requireNonNull(id);
		this.ignoreCase = ignoreCase;
		this.regexpMatching = regexpMatching;
	}

	// TODO implement regexp matching
	@Override
	public boolean matches(FendoTimeSeries timeSeries) {
		if (!ignoreCase)
			return id.equals(timeSeries.getPath());
		else
			return id.equalsIgnoreCase(timeSeries.getPath());
	}
	
	@Override
	public String toString() {
		return "IdMatcher(" + id + ")";
	}
	
}
