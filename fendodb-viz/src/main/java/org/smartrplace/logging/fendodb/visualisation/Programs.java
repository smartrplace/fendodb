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
package org.smartrplace.logging.fendodb.visualisation;

import java.util.ArrayList;
import java.util.Collection;

import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.reswidget.scheduleviewer.api.TimeSeriesFilter;

public class Programs {

	static final Collection<TimeSeriesFilter> programs;

	static {
		programs = new ArrayList<>();
		programs.add(TimeSeriesFilter.ALL_TIME_SERIES);
		
		programs.add(new TimeSeriesFilter() {
			
			@Override
			public String label(OgemaLocale arg0) {
				return "Points";
			}
			
			@Override
			public String id() {
				return "points";
			}
			
			@Override
			public boolean accept(ReadOnlyTimeSeries arg0) {
				if (!(arg0 instanceof RecordedData))
					return false;
				String id = ((RecordedData) arg0).getPath();
				if (!id.startsWith("semaFTConfigConfig/"))
					return false;
				String[] components = id.split("/");
				int length = components.length;
				if (length < 2)
					return false;
				String last = components[length-1].toLowerCase();
				return last.contains("points");
			}
		});
		
		programs.add(new TimeSeriesFilter() {
			
			@Override
			public String label(OgemaLocale arg0) {
				return "Levels";
			}
			
			@Override
			public String id() {
				return "levels";
			}
			
			@Override
			public boolean accept(ReadOnlyTimeSeries arg0) {
				if (!(arg0 instanceof RecordedData))
					return false;
				String id = ((RecordedData) arg0).getPath();
				if (!id.startsWith("semaFTConfigConfig/"))
					return false;
				String[] components = id.split("/");
				int length = components.length;
				if (length < 2)
					return false;
				String last = components[length-1].toLowerCase();
				return last.contains("levels");
			}
		});
		
		programs.add(TimeSeriesFilter.POWER_MEASUREMENTS);
		programs.add(TimeSeriesFilter.ALL_TEMPERATURES);
		programs.add(TimeSeriesFilter.TEMPERATURE_MEASUREMENTS);
		programs.add(TimeSeriesFilter.THERMOSTAT_SETPOINTS);
		programs.add(TimeSeriesFilter.TEMPERATURE_MANAGEMENT_SETPOINTS);
		programs.add(TimeSeriesFilter.ALL_HUMIDITIES);
	}
	
	
}
