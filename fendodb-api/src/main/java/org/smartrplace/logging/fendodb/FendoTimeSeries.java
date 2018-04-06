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
package org.smartrplace.logging.fendodb;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.RecordedDataStorage;

public interface FendoTimeSeries extends RecordedDataStorage {

	void setProperty(String key, String value);

	void addProperty(String key, String value);

	void addProperties(String key, Collection<String> properties);

	void setProperties(Map<String, Collection<String>> properties);

	void addProperties(Map<String, Collection<String>> properties);

	/**
	 * Remove all properties for this key.
	 * @param key
	 * @return
	 */
	boolean removeProperty(String key);

	/**
	 * Remove the specified value from the properties list for the specified key.
	 * If no further property values exist for this key, it will be removed completely.
	 * @param key
	 * @param value
	 * @return
	 */
	boolean removeProperty(String key, String value);

	/**
	 * Get all properties.
	 * @return
	 */
	Map<String, List<String>> getProperties();

	String getFirstProperty(String key);

	/**
	 * Get all properties for the specified key.
	 * @param key
	 * @return
	 */
	List<String> getProperties(String key);

	boolean hasProperty(String key);

	boolean hasProperty(String tag, boolean regexpMatching);

	default Stream<SampledValue> getValuesAsStream() {
		final Iterable<SampledValue> iterable = () -> iterator();
		return StreamSupport.stream(iterable.spliterator(), false);
	};

	default Stream<SampledValue> getValuesAsStream(long start, long end) {
		final Iterable<SampledValue> iterable = () -> iterator(start, end);
		return StreamSupport.stream(iterable.spliterator(), false);
	};

}
