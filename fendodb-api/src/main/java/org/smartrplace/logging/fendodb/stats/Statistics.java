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

import org.ogema.core.channelmanager.measurements.SampledValue;

public interface Statistics<Result> {

	// TODO allow passing multiple values from multiple time series at once?
	void step(SampledValue sv);
	
	/**
	 * @param finalTimestamp
	 * 		equal to or greater than the timestamp of the last value passed to {@link #step(SampledValue)}
	 * @return
	 */
	Result finish(long finalTimestamp);
	
}
