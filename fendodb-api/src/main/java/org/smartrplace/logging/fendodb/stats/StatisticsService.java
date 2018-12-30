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
