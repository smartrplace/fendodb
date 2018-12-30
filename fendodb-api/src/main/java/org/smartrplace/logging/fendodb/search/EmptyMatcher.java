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

import org.smartrplace.logging.fendodb.FendoTimeSeries;

class EmptyMatcher implements TimeSeriesMatcher {

	private final boolean emptyOrNot;
	private final long startTime;
	private final long endTime;
	static final EmptyMatcher STANDARD_EMPTY_MATCHER = new EmptyMatcher(false, Long.MIN_VALUE, Long.MAX_VALUE);
	
	EmptyMatcher(boolean emptyOrNot, long startTime, long endTime) {
		this.emptyOrNot = emptyOrNot;
		this.startTime = startTime;
		this.endTime = endTime;
	}
	
	@Override
	public boolean matches(FendoTimeSeries timeSeries) {
		return emptyOrNot == timeSeries.isEmpty(startTime, endTime);
	}
	
	@Override
	public String toString() {
		return "EmptyMatcher";
	}
	
}
