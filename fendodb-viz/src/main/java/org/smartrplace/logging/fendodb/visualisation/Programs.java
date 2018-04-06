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
