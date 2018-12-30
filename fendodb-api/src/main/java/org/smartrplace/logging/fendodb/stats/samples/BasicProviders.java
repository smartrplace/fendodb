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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

import org.ogema.core.timeseries.InterpolationMode;
import org.smartrplace.logging.fendodb.stats.Statistics;
import org.smartrplace.logging.fendodb.stats.StatisticsConfiguration;
import org.smartrplace.logging.fendodb.stats.StatisticsProvider;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class BasicProviders {

	private static final Map<String, StatisticsProvider<?>> providers;
	private final static long ONE_DAY = 24 * 60 * 60 * 1000;
	
	private static final StatisticsProvider<Float> avgProvider = new StatisticsProvider<Float>() {

		@Override
		public Statistics<Float> newStatistics(StatisticsConfiguration config) {
			return new AverageStatistics(getMode(config));
		}

		@Override
		public Float join(Collection<Float> individualResults) {
			return (float) individualResults.stream()
				.filter(result -> result != null && !Float.isNaN(result))
				.mapToDouble(result -> result)
				.average().orElse(Float.NaN);
		}
	};
	
	private static final StatisticsProvider<Integer> cntProvider = new StatisticsProvider<Integer>() {

		@Override
		public Statistics<Integer> newStatistics(StatisticsConfiguration config) {
			return new Count();
		}

		@Override
		public Integer join(Collection<Integer> individualResults) {
			return individualResults.stream()
				.filter(res -> res!=null)
				.mapToInt(res -> res)
				.sum();
		}
	};
	
	public static final class GapLengthProvider implements StatisticsProvider<Long> {
		
		private final long minGapSize;
	
		public GapLengthProvider(long minGapSize) {
			if (minGapSize < 0)
				throw new IllegalArgumentException("Gap size is negative: " + minGapSize);
			this.minGapSize = minGapSize;
		}

		@Override
		public Statistics<Long> newStatistics(StatisticsConfiguration config) {
			return new GapSizeStats(minGapSize);
		}

		@Override
		public Long join(Collection<Long> individualResults) {
			return individualResults.stream()
				.filter(result -> result != null)
				.mapToLong(i -> i)
				.sum();
		}
	}
	
	public static final class GapCntProvider implements StatisticsProvider<Integer> {
		
		private final long minGapSize;
	
		public GapCntProvider(long minGapSize) {
			if (minGapSize < 0)
				throw new IllegalArgumentException("Gap size is negative: " + minGapSize);
			this.minGapSize = minGapSize;
		}

		@Override
		public Statistics<Integer> newStatistics(StatisticsConfiguration config) {
			return new GapCntStats(minGapSize);
		}

		@Override
		public Integer join(Collection<Integer> individualResults) {
			return individualResults.stream()
				.filter(result -> result != null)
				.mapToInt(i -> i)
				.sum();
		}
	}
	
	private static final class MaxMinTimeProvider implements StatisticsProvider<Long> {
		
		private final boolean maxOrMin;
		
		public MaxMinTimeProvider(boolean maxOrMin) {
			this.maxOrMin = maxOrMin;
		}

		@Override
		public Statistics<Long> newStatistics(StatisticsConfiguration config) {
			return new MaxMinTimestamp(!maxOrMin);
		}

		// FIXME not really possible with this concept, since we do not keep track of the min/max values here
		@Override
		public Long join(Collection<Long> individualResults) {
			return individualResults.stream().filter(res -> res != null).findFirst().orElse(null);
		}
	}
	
	private static final class MaxMinValueProvider implements StatisticsProvider<Float> {
		
		private final boolean maxOrMin;
		
		public MaxMinValueProvider(boolean maxOrMin) {
			this.maxOrMin = maxOrMin;
		}

		@Override
		public Statistics<Float> newStatistics(StatisticsConfiguration config) {
			return new MaxMinValue(!maxOrMin);
		}

		@Override
		public Float join(Collection<Float> individualResults) {
			final DoubleStream stream = individualResults.stream()
				.filter(res -> res!= null)
				.mapToDouble(f -> f);
			final OptionalDouble opt;
			if (!maxOrMin)
				opt  = stream.min();
			else
				opt = stream.max();
			return (float) opt.orElse(Float.NaN);
		}
	}
	
	static {
		final Map<String, StatisticsProvider> providers0 = new HashMap<>(16,1);
		providers0.put("avg", avgProvider);
		providers0.put("cnt", cntProvider);
		providers0.put("max", new MaxMinValueProvider(true));
		providers0.put("min", new MaxMinValueProvider(false));
		providers0.put("maxT", new MaxMinTimeProvider(true));
		providers0.put("minT", new MaxMinTimeProvider(false));
		providers0.put("gapTime1min",new GapLengthProvider(60000));
		providers0.put("gapTime10min", new GapLengthProvider(600000));
		providers0.put("gapTime1h", new GapLengthProvider(360000));
		providers0.put("gapTime1d", new GapLengthProvider(ONE_DAY));
		providers0.put("gapTime2d", new GapLengthProvider(2 * ONE_DAY));
		providers0.put("gapCnt1min", new GapCntProvider(60000));
		providers0.put("gapCnt10min", new GapCntProvider(600000));
		providers0.put("gapCnt1h", new GapCntProvider(360000));
		providers0.put("gapCnt1d", new GapCntProvider(ONE_DAY));
		providers0.put("gapCnt2d", new GapCntProvider(2 * ONE_DAY));
		providers = Collections.unmodifiableMap((Map) providers0);
	}
	
	private static final InterpolationMode getMode(StatisticsConfiguration cfg) {
		final InterpolationMode mode0 = cfg.getInterpolationMode();
		return mode0 != null ? mode0 : InterpolationMode.LINEAR; 
	}
	
	public static Map<String, StatisticsProvider<?>> getBasicProviders() {
		return providers;
	}
	
}
