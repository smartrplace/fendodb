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
package org.smartrplace.logging.fendodb.stats;

import java.util.List;
import java.util.Map;

import org.ogema.core.timeseries.ReadOnlyTimeSeries;

/**
 * Retrieve as an OSGi service
 */
public interface StatisticsService {
	
	List<?> evaluate(ReadOnlyTimeSeries timeSeries, List<StatisticsProvider<?>> providers);
	List<?> evaluate(ReadOnlyTimeSeries timeSeries, List<StatisticsProvider<?>> providers, long startTime, long endTime);
	/**
	 * 
	 * @param timeSeries
	 * @param providerIds
	 * @return
	 * 		map: provider id -> result 
	 */
	Map<String, ?> evaluateByIds(ReadOnlyTimeSeries timeSeries, List<String> providerIds);
	
	/**
	 * 
	 * @param timeSeries
	 * @param providerIds
	 * @param startTime
	 * @param endTime
	 * @return
	 * 		map: provider id -> result
	 */		
	Map<String, ?> evaluateByIds(ReadOnlyTimeSeries timeSeries, List<String> providerIds, long startTime, long endTime);	
	
	Map<String, ?> evaluateByIds(List<? extends ReadOnlyTimeSeries> timeSeries, List<String> providerIds);	
	Map<String, ?> evaluateByIds(List<? extends ReadOnlyTimeSeries> timeSeries, List<String> providerIds, long startTime, long endTime);	

	
}
