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
