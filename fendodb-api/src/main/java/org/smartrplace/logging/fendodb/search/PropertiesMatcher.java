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
