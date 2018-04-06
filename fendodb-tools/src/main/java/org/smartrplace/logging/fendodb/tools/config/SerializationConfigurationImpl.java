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