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
package org.smartrplace.logging.fendodb.tools.dump;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfigurationBuilder;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

public class DumpConfigurationBuilder {

	private final SerializationConfigurationBuilder serialConfig;
	private Collection<String> includedIds = null;
	private boolean regexpIncludes;
	private boolean ignoreCaseIncludes;
	private Collection<String> excludedIds = null;
	private boolean regexpExcludes;
	private boolean ignoreCaseExcludes;
	private boolean doZip;
	private TimeSeriesMatcher filter;
	
	private DumpConfigurationBuilder(SerializationConfiguration serialConfig) {
		this.serialConfig = SerializationConfigurationBuilder.getInstance(serialConfig);
	}
	
	public static DumpConfigurationBuilder getInstance() {
		return new DumpConfigurationBuilder(null);
	}
	
	public static DumpConfigurationBuilder getInstance(final SerializationConfiguration serialConfig) {
		final DumpConfigurationBuilder builder = new DumpConfigurationBuilder(serialConfig);
		if (serialConfig instanceof DumpConfiguration) {
			final DumpConfiguration cfg = (DumpConfiguration) serialConfig;
			builder.setIncludedIds(cfg.getIncludedIds(), cfg.isRegexpIncludes(), cfg.isIgnoreCaseIncludes());
			builder.setExcludedIds(cfg.getExcludedIds(), cfg.isRegexpExcludes(), cfg.isIgnoreCaseExcludes());
			builder.setDoZip(cfg.doZip());
			builder.setFilter(cfg.getFilter());
		}
		return builder;
	}

	public DumpConfigurationBuilder setInterval(long startTime, long endTime) {
		serialConfig.setInterval(startTime, endTime);
		return this;
	}
	
	public DumpConfiguration build() {
		return new DumpConfigurationImpl(
				serialConfig.build(),
				includedIds, 
				regexpIncludes, 
				ignoreCaseIncludes, 
				excludedIds, 
				regexpExcludes, 
				ignoreCaseExcludes, 
				doZip,
				filter);
	}
	
	/**
	 * Explicitly include a collection of recorded data ids. 
	 * Default is null, in which case all ids are included. See also
	 * {@link #setIncludedIds(Collection, boolean, boolean)}.
	 * @param includedIds
	 * @return
	 */
	public DumpConfigurationBuilder setIncludedIds(Collection<String> includedIds) {
		this.includedIds = includedIds;
		return this;
	}

	/**
	 * Explicitly include a collection of recorded data ids. 
	 * Default is null, in which case all ids are included.
	 * @param includedIds
	 * @param regexp
	 * 		use regular expression matching to evaluate the included ids? If false, 
	 * 		only exact matches are considered. Default is false.
	 * @param ignoreCase
	 * 		Ignore case for id matching? Default is false.
	 * @return
	 */
	public DumpConfigurationBuilder setIncludedIds(Collection<String> includedIds, boolean regexp, boolean ignoreCase) {
		this.includedIds = includedIds;
		this.regexpIncludes = regexp;
		this.ignoreCaseIncludes = ignoreCase;
		return this;
	}
	
	/**
	 * Exclude a set of recorded data ids from the csv dump
	 * @param excludedIds
	 * @return
	 */
	public DumpConfigurationBuilder setExcludedIds(Collection<String> excludedIds) {
		this.excludedIds = excludedIds;
		return this;
	}

	/**
	 * Exclude a set of recorded data ids from the csv dump
	 * @param excludedIds
	 * @param regexp
	 * 		use regular expression matching to evaluate the excluded ids? If false, 
	 * 		only exact matches are considered. Default is false.
	 * @param ignoreCase
	 * 		Ignore case for id matching? Default is false.
	 * @return
	 */
	public DumpConfigurationBuilder setExcludedIds(Collection<String> excludedIds, boolean regexp, boolean ignoreCase) {
		this.excludedIds = excludedIds;
		this.regexpExcludes = regexp;
		this.ignoreCaseExcludes = ignoreCase;
		return this;
	}
	
	
	/**
	 * Define a formatter for printing the time stamps. Default is null, in which case the timestamps
	 * are printed as long values (milliseconds since epoch).
	 * @param formatter
	 * @return
	 */
	public DumpConfigurationBuilder setFormatter(DateTimeFormatter formatter) {
		return setFormatter(formatter, null);
	}
	
	/**
	 * Define a formatter for printing the time stamps. Default is null, in which case the timestamps
	 * are printed as long values (milliseconds since epoch).
	 * @param formatter
	 * @param timeZone
	 * @return
	 */
	public DumpConfigurationBuilder setFormatter(DateTimeFormatter formatter, ZoneId timeZone) {
		serialConfig.setFormatter(formatter, timeZone);
		return this;
	}
	
	/**
	 * Compress the csv files into one zip archive?
	 * Default: false
	 * @param doZip
	 * @return
	 */
	public DumpConfigurationBuilder setDoZip(boolean doZip) {
		this.doZip = doZip;
		return this;
	}
	
	/**
	 * Set the delimiter between records
	 * Default: ';'
	 * @param delimiter
	 * @return
	 */
	public DumpConfigurationBuilder setDelimiter(char delimiter) {
		serialConfig.setDelimiter(delimiter);
		return this;
	}
	
	/**
	 * Define a custom filter for the timeseries to include in the database dump.
	 * Create a filter using the {@link SearchFilterBuilder}.
	 * @param filter
	 * @return
	 */
	public DumpConfigurationBuilder setFilter(TimeSeriesMatcher filter) {
		this.filter = filter;
		return this;
	}
	
	
	/**
	 * If null, values are written in raw form, if non-null they are downsampled to 
	 * the respective interval (in ms). Default is null.
	 * @param samplingInterval
	 * @return
	 * @throws IllegalArgumentException if samplingInterval is non-positive
	 */
	public DumpConfigurationBuilder setSamplingInterval(Long samplingInterval) {
		serialConfig.setSamplingInterval(samplingInterval);
		return this;
	}
	
	/**
	 * Set the serialization format. Default is {@link FendodbSerializationFormat#CSV}.
	 * @param format
	 * @return
	 */
	public DumpConfigurationBuilder setFormat(FendodbSerializationFormat format) {
		serialConfig.setFormat(format);
		return this;
	}
	
	/**
	 * Set the maximum number of entries to be written. Defaults to 10000.
	 * @param maxNrValues
	 * @return
	 */
	public DumpConfigurationBuilder setMaxNrValues(int maxNrValues) {
		serialConfig.setMaxNrValues(maxNrValues);
		return this;
	}
	
	/**
	 * Pretty print result? Default is true.
	 * Only relevant if serialization format is 
	 * {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}.
	 * @param prettyPrint
	 * @return
	 */
	public DumpConfigurationBuilder setPrettyPrint(boolean prettyPrint) {
		serialConfig.setPrettyPrint(prettyPrint);
		return this;
	}
	
	/**
	 * Set indentation. Also sets {@link #setPrettyPrint(boolean)} to true.
	 * Only relevant if serialization format is 
	 * {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}.
	 * @param indentation
	 * @return
	 */
	public DumpConfigurationBuilder setIndentation(int indentation) {
		serialConfig.setIndentation(indentation);
		return this;
	}
	
}
