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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;

/**
 * Create a {@link TimeSeriesMatcher filter} for time series search requests.
 * For use in {@link CloseableDataRecorder#findTimeSeries(TimeSeriesMatcher)}. 
 */
public class SearchFilterBuilder {
	
	private final static TimeSeriesMatcher matchAll = (timeSeries -> true);
	private final static TimeSeriesMatcher matchNone = (timeSeries -> false);
	private TimeSeriesMatcher matcher = matchAll;
	
	private SearchFilterBuilder() {}

	public static SearchFilterBuilder getInstance() {
		return new SearchFilterBuilder();
	}
	
	public TimeSeriesMatcher build() {
		return matcher;
	}
	
	public SearchFilterBuilder invert() {
		matcher = new InverseMatcher(matcher);
		return this;
	}
	
	/**
	 * Find a timeseries with a specific id.
	 * @param id
	 * @param ignoreCase
	 * @return
	 */
	public SearchFilterBuilder filterById(String id, boolean ignoreCase) {
		setOrAnd(new IdMatcher(id, ignoreCase, false));
		return this;
	}
	
	/**
	 * Find timeseries with a specific set of ids.
	 * @param includedIds
	 * @param excludedIds
	 * @param ignoreCase
	 * @return
	 */
	public SearchFilterBuilder filterByIds(
			Collection<String> includedIds,
			Collection<String> excludedIds,
			boolean ignoreCase) {
		final TimeSeriesMatcher newMatcher = SearchFilterBuilder.getInstance()
				.filterByIncludedIds(includedIds, ignoreCase)
				.filterByExcludedIds(excludedIds, ignoreCase)
				.build();
		setOrAnd(newMatcher);
		return this;
	}
	
	/**
	 * Find timeseries with a specific set of ids.
	 * @param includedIds
	 * @param ignoreCase
	 * @return
	 */
	public SearchFilterBuilder filterByIncludedIds(
			Collection<String> includedIds,
			boolean ignoreCase) {
		if (includedIds == null)
			return this;
		if (includedIds.isEmpty()) {
			matcher = matchNone;
			return this;
		}
		if (includedIds.size() == 1)
			return filterById(includedIds.iterator().next(), ignoreCase);
		final TimeSeriesMatcher newMatcher = new Or(includedIds.stream().map(id -> new IdMatcher(id, ignoreCase, false)).collect(Collectors.toList()));
		setOrAnd(newMatcher);
		return this;
	}
	
	/**
	 * Find the timeseries with ids not matching the passed ones.
	 * @param excludedIds
	 * @param ignoreCase
	 * @return
	 */
	public SearchFilterBuilder filterByExcludedIds(
			Collection<String> excludedIds,
			boolean ignoreCase) {
		if (excludedIds == null || excludedIds.isEmpty())
			return this;
		final TimeSeriesMatcher newMatcher = SearchFilterBuilder.getInstance()
			.filterByIncludedIds(excludedIds, ignoreCase)
			.invert()
			.build();
		setOrAnd(newMatcher);
		return this;
	}
	
	public SearchFilterBuilder filterByProperty(String key, String value, boolean valueIgnoreCase) {
		setOrAnd(new PropertyMatcher(key, value, valueIgnoreCase));
		return this;
	}
	
	public SearchFilterBuilder filterByPropertyMultiValue(String key, Collection<String> values, boolean valueIgnoreCase) {
		setOrAnd(new PropertiesMatcher(key, values, valueIgnoreCase));
		return this;
	}
	
	/**
	 * Find time series for which at least one of the specified properties match
	 * @param properties
	 * @param valueIgnoreCase
	 * @return
	 */
	public SearchFilterBuilder filterByProperties(Map<String,String> properties, boolean valueIgnoreCase) {
		Objects.requireNonNull(properties);
		if (properties.isEmpty())
			return this;
		if (properties.size() == 1)
			return filterByProperty(properties.keySet().iterator().next(), properties.values().iterator().next(), valueIgnoreCase);
		final Or newMatcher = new Or(properties.entrySet().stream()
				.map(entry -> new PropertyMatcher(entry.getKey(), entry.getValue(), valueIgnoreCase)).collect(Collectors.toList()));
		setOrAnd(newMatcher);
		return this;
	}
	
	/**
	 * Find time series for which at least one of the specified properties match
	 * @param properties
	 * @param valueIgnoreCase
	 * @return
	 */
	public SearchFilterBuilder filterByPropertiesMultiValues(Map<String,Collection<String>> properties, boolean valueIgnoreCase) {
		Objects.requireNonNull(properties);
		if (properties.size() == 0)
			return this;
		if (properties.size() == 1)
			return filterByPropertyMultiValue(properties.keySet().iterator().next(), properties.values().iterator().next(), valueIgnoreCase);
		final Or newMatcher = new Or(properties.entrySet().stream()
				.map(entry -> new PropertiesMatcher(entry.getKey(), entry.getValue(), valueIgnoreCase)).collect(Collectors.toList()));
		setOrAnd(newMatcher);
		return this;
	}
	
	/**
	 * Find time series that have at least one of the specified tags 
	 * @param tags
	 * @return
	 */
	public SearchFilterBuilder filterByTags(String... tags) {
		Objects.requireNonNull(tags);
		if (tags.length == 0)
			return this;
		if (tags.length == 1)
			return filterByTag(tags[0]);
		final Or newMatcher = new Or(Arrays.stream(tags).map(tag -> new TagMatcher(tag)).collect(Collectors.toList()));
		setOrAnd(newMatcher);
		return this;
	}
	
	public SearchFilterBuilder filterByTag(String tag) {
		setOrAnd(new TagMatcher(tag));
		return this;
	}
	
	/**
	 * Filter out empty time series
	 * @return
	 */
	public SearchFilterBuilder requireNonEmpty() {
		setOrAnd(EmptyMatcher.STANDARD_EMPTY_MATCHER);
		return this;
	}
	
	/**
	 * Filter out empty time series that are empty in the specified interval
	 * @param start
	 * 		start time, in milliseconds since epoch
	 * @param end
	 * 		end time, in milliseconds since epoch
	 * @return
	 */
	public SearchFilterBuilder requireNonEmpty(long start, long end) {
		setOrAnd(new EmptyMatcher(false, start, end));
		return this;
	}
	
	public SearchFilterBuilder and(TimeSeriesMatcher other) {
		setOrAnd(other);
		return this;
	}
	
	public SearchFilterBuilder or(TimeSeriesMatcher other) {
		if (matcher == matchAll)
			matcher = other;
		else
			matcher = new Or(Arrays.asList(matcher, other));
		return this;
	}
	
	private final void setOrAnd(final TimeSeriesMatcher newMatcher) {
		if (matcher == matchAll)
			matcher = newMatcher;
		else
			matcher = new And(Arrays.asList(matcher, newMatcher));
	}
	
	
}

