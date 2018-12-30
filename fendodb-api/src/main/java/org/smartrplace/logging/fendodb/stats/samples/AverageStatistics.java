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
