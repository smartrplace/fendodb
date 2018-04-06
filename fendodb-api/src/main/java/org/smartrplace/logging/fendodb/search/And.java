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

import java.util.Collection;

import org.smartrplace.logging.fendodb.FendoTimeSeries;

class And extends Concatenation {

	And(Collection<TimeSeriesMatcher> matchers) {
		super(matchers);
	}
	
	@Override
	public boolean matches(FendoTimeSeries timeSeries) {
		return !matchers.stream()
			.filter(m -> !m.matches(timeSeries))
			.findAny().isPresent();
	}
	
	@Override
	public String toString() {
		return "AND" + matchers;
	}
	
}
