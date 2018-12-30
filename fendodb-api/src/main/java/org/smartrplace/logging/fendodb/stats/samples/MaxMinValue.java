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
import org.smartrplace.logging.fendodb.stats.Statistics;

public class MaxMinValue implements Statistics<Float> {
	
	private final boolean minOrMax;

	// state
	private float max;
	
	public MaxMinValue(boolean minOrMax) {
		this.minOrMax = minOrMax;
		this.max = minOrMax ? Float.MAX_VALUE : -Float.MAX_VALUE;
	}
	
	@Override
	public void step(final SampledValue sv) {
		if (sv == null || sv.getQuality() == Quality.BAD)
			return;
		final float current = sv.getValue().getFloatValue();
		if ((!minOrMax && current > max) || (minOrMax && current < max))
			max = current;
	}

	@Override
	public Float finish(long t) {
		if ((minOrMax && max == Float.MAX_VALUE) || (!minOrMax && max == -Float.MAX_VALUE))
			return Float.NaN;
		return max;
	}

	
	
}
