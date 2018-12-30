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
package org.smartrplace.logging.fendodb.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.timeseries.InterpolationMode;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.stats.Statistics;
import org.smartrplace.logging.fendodb.stats.StatisticsConfiguration;
import org.smartrplace.logging.fendodb.stats.StatisticsProvider;
import org.smartrplace.logging.fendodb.stats.StatisticsService;
import org.smartrplace.logging.fendodb.stats.samples.BasicProviders;

@Component(service=StatisticsService.class)
public class StatisticsServiceImpl implements StatisticsService {
	
	private final Map<String, StatisticsProvider<?>> statistic = new ConcurrentHashMap<>();
	
	{
		statistic.putAll(BasicProviders.getBasicProviders());
	}
	
	@Reference(
			service=StatisticsProvider.class,
			bind="addProvider",
			unbind="removeProvider",
			cardinality=ReferenceCardinality.MULTIPLE,
			policy=ReferencePolicy.DYNAMIC
	)
	protected void addProvider(StatisticsProvider<?> provider, Map<String, Object> props) {
		final Object o = props.get("providerId");
		if (!(o instanceof String))
			return;
		final String id = (String) o;
		if (statistic.entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(id))
				.findAny().isPresent()) {
			LoggerFactory.getLogger(SlotsDbFactoryImpl.class).warn("Provider with id {} already exists",id);
			return;
		}
		statistic.put(id, provider);
	}
	
	protected void removeProvider(StatisticsProvider<?> provider, Map<String, Object> props) {
		final Object o = props.get("providerId");
		if (!(o instanceof String))
			return;
		final String id = (String) o;
		final Iterator<Map.Entry<String, StatisticsProvider<?>>> it = statistic.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, StatisticsProvider<?>> entry = it.next();
			if (entry.getKey().equalsIgnoreCase(id) && entry.getValue().equals(provider)) {
				it.remove();
				break;
			}
		}
	}
	
	private StatisticsProvider<?> findProvider(final String id) {
		if (id == null)
			return null;
		return statistic.entrySet().stream()
			.filter(entry -> entry.getKey().equalsIgnoreCase(id))
			.map(entry -> entry.getValue())
			.findAny().orElse(null);
	}
	
	@Override
	public List<?> evaluate(ReadOnlyTimeSeries timeSeries, List<StatisticsProvider<?>> providers) {
		final boolean empty = timeSeries.isEmpty();
		long start = 0;
		long end = 1;
		if (!empty)  {
			start = timeSeries.getNextValue(Long.MIN_VALUE).getTimestamp();
			end = timeSeries.getPreviousValue(Long.MAX_VALUE).getTimestamp();
			if (end == start)
				end++;
		}
		return evaluate(timeSeries, providers, start, end); 
	}

	@Override
	public List<?> evaluate(ReadOnlyTimeSeries timeSeries, List<StatisticsProvider<?>> providers, long start, long end) {
		final StatisticsConfiguration cfg = new ConfigImpl(timeSeries.getInterpolationMode());
		final List<Statistics<?>> stats = 
				providers.stream().map(provider -> provider.newStatistics(cfg)).collect(Collectors.toList());
		final Iterator<SampledValue> it = timeSeries.iterator(start, end);
		while (it.hasNext()) {
			final SampledValue sv = it.next();
			stats.forEach(stat -> stat.step(sv));
		}
		return stats.stream()
			.map(stat -> stat.finish(end))
			.collect(Collectors.toList());
	}
	
	@Override
	public Map<String, ?> evaluateByIds(ReadOnlyTimeSeries timeSeries, List<String> providersIds) {
		final boolean empty = timeSeries.isEmpty();
		long start = 0;
		long end = 1;
		if (!empty)  {
			start = timeSeries.getNextValue(Long.MIN_VALUE).getTimestamp();
			end = timeSeries.getPreviousValue(Long.MAX_VALUE).getTimestamp();
			if (end == start)
				end++;
		}
		return evaluateByIds(timeSeries, providersIds, start, end); 
	}
	
	@Override
	public Map<String, ?> evaluateByIds(final ReadOnlyTimeSeries timeSeries, final List<String> providersIds, long start, long end) {
		final List<LabelledProvider> providers = statistic.entrySet().stream()
				.filter(entry -> providersIds.stream().filter(id -> entry.getKey().equalsIgnoreCase(id)).findAny().isPresent())
				.map(entry -> new LabelledProvider(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
		final List<StatisticsProvider<?>> providers1 = providers.stream().map(p -> p.provider).collect(Collectors.toList());
		final List<?> results = evaluate(timeSeries, providers1, start, end);
		final Map<String, Object> result1 = new HashMap<>(providers.size(), 1);
		for (int i=0; i<providers.size(); i++) {
			result1.put(providers.get(i).id, results.get(i));
		}
		return result1;
	}
	
	// TODO refactor to avoid double implementation
	@Override
	public Map<String, ?> evaluateByIds(List<? extends ReadOnlyTimeSeries> timeSeries, List<String> providerIds) {
		final List<LabelledProvider> providers = statistic.entrySet().stream()
				.filter(entry -> providerIds.stream().filter(id -> entry.getKey().equalsIgnoreCase(id)).findAny().isPresent())
				.map(entry -> new LabelledProvider(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
		final List<StatisticsProvider<?>> providers1 = providers.stream().map(p -> p.provider).collect(Collectors.toList());
		final AtomicInteger cnt = new AtomicInteger(0);
		final Map<Integer, List<?>> results = timeSeries.stream()	
			.collect(Collectors.toMap(ts -> cnt.getAndIncrement(), ts -> evaluate(ts, providers1)));
		final Map<String, Object> totalresult = new HashMap<>(providers.size(),1);
		for (int i = 0; i <providers.size(); i++) {
			final int j= i;
			final List<?> providerResults = results.values().stream()
				.map(list -> list.get(j))
				.collect(Collectors.toList());
			final LabelledProvider lp = providers.get(j);
			@SuppressWarnings({ "rawtypes", "unchecked" })
			final Object providerResult = lp.provider.join((List) providerResults);
			totalresult.put(lp.id, providerResult);
		}
		return totalresult;
	}
	
	@Override
	public Map<String, ?> evaluateByIds(final List<? extends ReadOnlyTimeSeries> timeSeries, final List<String> providerIds,
			final long startTime, final long endTime) {
		final List<LabelledProvider> providers = statistic.entrySet().stream()
				.filter(entry -> providerIds.stream().filter(id -> entry.getKey().equalsIgnoreCase(id)).findAny().isPresent())
				.map(entry -> new LabelledProvider(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList());
		final List<StatisticsProvider<?>> providers1 = providers.stream().map(p -> p.provider).collect(Collectors.toList());
		final AtomicInteger cnt = new AtomicInteger(0);
		final Map<Integer, List<?>> results = timeSeries.stream()	
			.collect(Collectors.toMap(ts -> cnt.getAndIncrement(), ts -> evaluate(ts, providers1, startTime, endTime)));
		final Map<String, Object> totalresult = new HashMap<>(providers.size(),1);
		for (int i = 0; i <providers.size(); i++) {
			final int j= i;
			final List<?> providerResults = results.values().stream()
				.map(list -> list.get(j))
				.collect(Collectors.toList());
			final LabelledProvider lp = providers.get(j);
			@SuppressWarnings({ "rawtypes", "unchecked" })
			final Object providerResult = lp.provider.join((List) providerResults);
			totalresult.put(lp.id, providerResult);
		}
		return totalresult;
	}
	
	private static final class LabelledProvider {
		
		final String id;
		final StatisticsProvider<?> provider;
		
		LabelledProvider(String id, StatisticsProvider<?> provider) {
			this.id = id;
			this.provider = provider;
		}
		
	}
	
	private static final class ConfigImpl implements StatisticsConfiguration {
		
		private final InterpolationMode mode;
		
		ConfigImpl(InterpolationMode mode) {
			this.mode = mode;
		}
		
		@Override
		public InterpolationMode getInterpolationMode() {
			return mode;
		}
		
	}

}
