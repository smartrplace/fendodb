/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
