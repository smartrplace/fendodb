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

	default boolean isReadOnly() {
		return true;
	}
}
