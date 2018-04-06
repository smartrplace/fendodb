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
