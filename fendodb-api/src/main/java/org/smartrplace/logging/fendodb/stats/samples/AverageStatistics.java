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
import org.ogema.core.timeseries.InterpolationMode;
import org.smartrplace.logging.fendodb.stats.Statistics;

public class AverageStatistics implements Statistics<Float> {
	
	// FIXME currently, we treat NEAREST as LINEAR
	private final InterpolationMode mode;
	// state
	private float integral = 0;
	private float length = 0;
	private SampledValue previous = null;
	
	public AverageStatistics(InterpolationMode mode) {
		this.mode = mode;
	}

	@Override
	public void step(final SampledValue sv) {
		if (sv != null && previous != null && sv.getTimestamp() <= previous.getTimestamp())
			throw new IllegalArgumentException("Timestamps not chronological, got " + previous.getTimestamp() + " followed by " + sv.getTimestamp());
		final boolean valid = sv != null && sv.getQuality() == Quality.GOOD;
		final boolean previousValid = previous != null && previous.getQuality() == Quality.GOOD;
		if (!valid) {
			if (sv != null && previousValid && mode == InterpolationMode.STEPS) {
				final long diff = sv.getTimestamp() - previous.getTimestamp();
				integral += previous.getValue().getFloatValue() * diff;
				length += diff;
			}
			previous = sv;
			return;
		}
		if (mode == InterpolationMode.NONE) {
			integral += sv.getValue().getFloatValue();
			length++;
			return;
		}
		if (!previousValid) {
			previous = sv;
			return;
		}
		if (mode == InterpolationMode.STEPS) {
			final long diff = sv.getTimestamp() - previous.getTimestamp();
			integral += previous.getValue().getFloatValue() * diff;
			length += diff;
		} else { // LINEAR or NEAREST
			final long t0 = previous.getTimestamp();
			final long t1 = sv.getTimestamp();
			final float v0 = previous.getValue().getFloatValue();
			final float v1 = sv.getValue().getFloatValue();
			integral += (v0 + v1) * (t1 - t0) / 2;
			length += t1 - t0;
		}
		previous = sv;
	}

	@Override
	public Float finish(long finalT) {
		return integral/length;
	}

	
	
}
