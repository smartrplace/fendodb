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
