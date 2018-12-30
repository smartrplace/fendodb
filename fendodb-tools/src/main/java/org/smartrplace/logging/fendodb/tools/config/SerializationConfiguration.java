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
