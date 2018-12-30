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
/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl.reduction;

import java.util.ArrayList;
import java.util.List;

import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.impl.DoubleValues;

public class MinimumReduction implements Reduction {

	@Override
	public List<SampledValue> performReduction(List<SampledValue> subIntervalValues, long timestamp) {

		List<SampledValue> toReturn = new ArrayList<SampledValue>();

		if (subIntervalValues.isEmpty()) {
			toReturn.add(new SampledValue(DoubleValues.of(0.f), timestamp, Quality.BAD));
		}
		else {
			double minValue = Double.MAX_VALUE;
			for (SampledValue value : subIntervalValues) {
				if (value.getValue().getDoubleValue() < minValue) {
					minValue = value.getValue().getDoubleValue();
				}
			}
			toReturn.add(new SampledValue(new SampledValue(DoubleValues.of(minValue), timestamp, Quality.GOOD)));
		}

		return toReturn;
	}

}
