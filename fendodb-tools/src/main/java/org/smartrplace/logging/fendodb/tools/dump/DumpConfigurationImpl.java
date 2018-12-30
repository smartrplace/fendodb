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
