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
package org.smartrplace.logging.fendodb.stats.samples;

import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.stats.Statistics;

public class MaxMinTimestamp implements Statistics<Long> {
	
	private final boolean minOrMax;

	// state
	private float max;
	private long t = Long.MAX_VALUE;
	
	public MaxMinTimestamp(boolean minOrMax) {
		this.minOrMax = minOrMax;
		this.max = minOrMax ? Float.MAX_VALUE : -Float.MAX_VALUE;
	}
	
	@Override
	public void step(final SampledValue sv) {
		if (sv == null || sv.getQuality() == Quality.BAD)
			return;
		final float current = sv.getValue().getFloatValue();
		if ((!minOrMax && current > max) || (minOrMax && current < max)) {
			max = current;
			t = sv.getTimestamp();
		}
		
	}

	@Override
	public Long finish(long tEnd) {
		if ((minOrMax && max == Float.MAX_VALUE) || (!minOrMax && max == -Float.MAX_VALUE))
			return null;
		return t;
	}
	
	
}
