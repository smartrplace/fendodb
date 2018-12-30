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
package org.smartrplace.logging.fendodb.tools.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class SerializationConfigurationImpl implements SerializationConfiguration {

	private final boolean prettyPrint;
	private final int indentation;
	private final int maxNrValues;
	private final char delimiter;
	private final long startTime;
	private final long endTime;
	private final DateTimeFormatter formatter;
	private final ZoneId timeZone;
	private final Long samplingInterval;
	private final FendodbSerializationFormat format;
	
	SerializationConfigurationImpl(FendodbSerializationFormat format, char delimiter, long startTime, long endTime, 
			DateTimeFormatter formatter, ZoneId timeZone, Long samplingInterval, int maxNrValues,
			boolean prettyPrint, int indentation) {
		this.format = format;
		this.delimiter = delimiter; 
		this.startTime = startTime;
		this.endTime = endTime;
		this.formatter = formatter;
		this.timeZone = timeZone;
		this.samplingInterval = samplingInterval;
		this.maxNrValues = maxNrValues;
		this.prettyPrint = prettyPrint;
		this.indentation = indentation;
	}
	
	@Override
	public FendodbSerializationFormat getFormat() {
		return format;
	}

	@Override
	public long getStartTime() {
		return startTime;
	}

	@Override
	public long getEndTime() {
		return endTime;
	}


	@Override
	public DateTimeFormatter getFormatter() {
		return formatter;
	}
	
	@Override
	public ZoneId getTimeZone() {
		return timeZone;
	}
	
	@Override
	public char getDelimiter() {
		return delimiter;
	}

	@Override
	public Long samplingInterval() {
		return samplingInterval;
	}

	@Override
	public int getMaxNrValues() {
		return maxNrValues;
	}

	@Override
	public boolean isPrettyPrint() {
		return prettyPrint;
	}
	
	@Override
	public int getIndentation() {
		return indentation;
	}
}