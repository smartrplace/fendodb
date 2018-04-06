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

class InverseMatcher implements TimeSeriesMatcher {
	
	private final TimeSeriesMatcher matcher;
	
	InverseMatcher(TimeSeriesMatcher matcher) {
		this.matcher = matcher;
	}
	
	@Override
	public boolean matches(FendoTimeSeries timeSeries) {
		return !matcher.matches(timeSeries);
	}

	@Override
	public String toString() {
		return "InverseMatcher(" + matcher + ")";
	}
	
}
