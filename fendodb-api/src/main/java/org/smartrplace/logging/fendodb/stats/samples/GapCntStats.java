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

/**
 * Returns the number of gaps found
 */
public class GapCntStats implements Statistics<Integer> {
	
	private final long minGapSize;
	
	// state
	private SampledValue previous;
	private int gapCnt = 0;
	
	/**
	 * The minimum time considered as a gap, in ms
	 * @param minGapSize
	 */
	public GapCntStats(long minGapSize) {
		this.minGapSize = minGapSize;
	}
	
	@Override
	public void step(final SampledValue sv) {
		if (sv == null || sv.getQuality() == Quality.BAD) {
			if (previous == null) {
				// TODO add to gap?
			}
			return;
		}
		if (previous == null) {
			previous = sv;
			return;
		}
		final long diff = sv.getTimestamp() - previous.getTimestamp();
		if (diff > minGapSize)
			gapCnt++;
	}

	@Override
	public Integer finish(long finalT) {
		if (previous != null) {
			final long diff = finalT - previous.getTimestamp();
			if (diff > minGapSize)
				gapCnt++;
		}
		return gapCnt;
	}

}
