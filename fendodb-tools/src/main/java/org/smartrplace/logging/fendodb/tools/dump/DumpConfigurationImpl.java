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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

class DumpConfigurationImpl implements DumpConfiguration {

	private final SerializationConfiguration serialConfig;
	private final Collection<String> includedIds;
	private final boolean regexpIncludes;
	private final boolean ignoreCaseIncludes;
	private final Collection<String> excludedIds;
	private final boolean regexpExcludes;
	private final boolean ignoreCaseExcludes;
	private final boolean doZip;
	private final boolean writeSingleFile;
	private final TimeSeriesMatcher filter;
	
	DumpConfigurationImpl(SerializationConfiguration serialConfig, Collection<String> includedIds, boolean regexpIncludes,
			boolean ignoreCaseIncludes, Collection<String> excludedIds, boolean regexpExcludes,
			boolean ignoreCaseExcludes, boolean doZip, boolean writeSingleFile, TimeSeriesMatcher filter) {
		this.serialConfig = Objects.requireNonNull(serialConfig);
		this.includedIds = includedIds == null ? null : Collections.unmodifiableList(new ArrayList<>(includedIds));
		this.regexpIncludes = regexpIncludes;
		this.ignoreCaseIncludes = ignoreCaseIncludes;
		this.excludedIds = excludedIds == null ? null : Collections.unmodifiableList(new ArrayList<>(excludedIds));
		this.regexpExcludes = regexpExcludes;
		this.ignoreCaseExcludes = ignoreCaseExcludes;
		this.doZip = doZip;
		this.filter = filter;
		this.writeSingleFile = writeSingleFile; 
	}

	@Override
	public long getStartTime() {
		return serialConfig.getStartTime();
	}

	@Override
	public long getEndTime() {
		return serialConfig.getEndTime();
	}

	@Override
	public Collection<String> getIncludedIds() {
		return includedIds;
	}

	@Override
	public boolean isRegexpIncludes() {
		return regexpIncludes;
	}

	@Override
	public boolean isIgnoreCaseIncludes() {
		return ignoreCaseIncludes;
	}

	@Override
	public Collection<String> getExcludedIds() {
		return excludedIds;
	}

	@Override
	public boolean isRegexpExcludes() {
		return regexpExcludes;
	}

	@Override
	public boolean isIgnoreCaseExcludes() {
		return ignoreCaseExcludes;
	}

	@Override
	public DateTimeFormatter getFormatter() {
		return serialConfig.getFormatter();
	}
	
	@Override
	public ZoneId getTimeZone() {
		return serialConfig.getTimeZone();
	}
	
	@Override
	public boolean doZip() {
		return doZip;
	}
	
	@Override
	public char getDelimiter() {
		return serialConfig.getDelimiter();
	}

	@Override
	public Long samplingInterval() {
		return serialConfig.samplingInterval();
	}

	@Override
	public TimeSeriesMatcher getFilter() {
		return filter;
	}
	
	@Override
	public FendodbSerializationFormat getFormat() {
		return serialConfig.getFormat();
	}

	@Override
	public int getMaxNrValues() {
		return serialConfig.getMaxNrValues();
	}

	@Override
	public boolean isPrettyPrint() {
		return serialConfig.isPrettyPrint();
	}

	@Override
	public int getIndentation() {
		return serialConfig.getIndentation();
	}
	
	@Override
	public boolean isWriteSingleFile() {
		return writeSingleFile;
	}
	
}
