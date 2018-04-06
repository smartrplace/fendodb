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

/*
 *  TODO 
 *   - skip/handle bad qualities?
 *   - pretty print config
 */
public interface SerializationConfiguration {
	
	FendodbSerializationFormat getFormat();

	/**
	 * Only relevant if {@link #getFormat() format} is {@link FendodbSerializationFormat#CSV}.
	 * Default is ';'
	 * @return
	 */
	char getDelimiter();
	
	/**
	 * Start time. Default is Long.MIN_VALUE.
	 * @return
	 */
	long getStartTime();

	/**
	 * End time. Default is Long.MAX_VALUE
	 * @return
	 */
	long getEndTime();

	/**
	 * Formatter for printing the time stamps. Default is null, in which case the timestamps
	 * are printed as long values (milliseconds since epoch).
	 * @return
	 */
	DateTimeFormatter getFormatter();
	
	/**
	 * Only relevant if {@link #getFormatter() formatter} is not null. 
	 * @return
	 */
	ZoneId getTimeZone();
	
	/**
	 * If null, values are written in raw form, if non-null they are downsampled to 
	 * the respective interval (in ms).
	 * @return
	 */
	Long samplingInterval();

	/**
	 * Get the maximum nr of entries to be printed per timeseries.
	 * @return
	 */
	int getMaxNrValues();
	
	/**
	 * Pretty print result? Only relevant if {@link #getFormat()} is 
	 * {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}. 
	 * @return
	 */
	boolean isPrettyPrint();
	
	/**
	 * Only relevant if {@link #isPrettyPrint()} is true, and 
	 * {@link #getFormat()} is {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}. 
	 * @return
	 */
	int getIndentation();
	
}
