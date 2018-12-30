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

import org.ogema.core.recordeddata.ReductionMode;

public class ReductionFactory {

	public Reduction getReduction(ReductionMode mode) {

		//NOTE: NONE is not considered here since no reduction is needed. 
		switch (mode) {
		case AVERAGE:
			return new AverageReduction();
		case MAXIMUM_VALUE:
			return new MaximumReduction();
		case MIN_MAX_VALUE:
			return new MinMaxReduction();
		case MINIMUM_VALUE:
			return new MinimumReduction();
		default:
			throw new IllegalArgumentException("Mode " + mode.toString() + " not supported yet");
		}

	}

}
