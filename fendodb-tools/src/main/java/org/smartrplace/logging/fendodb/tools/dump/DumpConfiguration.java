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

import java.util.Collection;

import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;

/*
 *  TODO 
 *   - reduction for individual time series
 *   - print qualities
 *   - skip bad qualities 
 */
public interface DumpConfiguration extends SerializationConfiguration {
	
	Collection<String> getIncludedIds();

	boolean isRegexpIncludes();

	boolean isIgnoreCaseIncludes();

	Collection<String> getExcludedIds();

	boolean isRegexpExcludes();

	boolean isIgnoreCaseExcludes();
	
	/**
	 * Can only be true if {@link #samplingInterval()} is
	 * non-null, so that all time series have a common time base.
	 * @return
	 */
	boolean isWriteSingleFile();
	
	/**
	 * Compress the csv files into one zip archive?
	 * Default: false
	 * @return
	 */
	boolean doZip();

	/**
	 * A custom filter for time series to be included in the database dump.
	 * @return
	 */
	TimeSeriesMatcher getFilter();
	
}
