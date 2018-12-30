/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;

@Ignore
class TestUtils {

	private final static AtomicInteger cnt = new AtomicInteger(0);
	private final static RecordedDataConfiguration createConfiguration(final long scale) {
		final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
		// fixed interval is problematic in tests, because it modifies the nr of datapoints
//		cfg.setStorageType(Math.random() > 0.6 ? StorageType.ON_VALUE_UPDATE : Math.random() > 0.5 ?
//				StorageType.ON_VALUE_CHANGED : StorageType.FIXED_INTERVAL);
//		if (cfg.getStorageType() == StorageType.FIXED_INTERVAL)
//			cfg.setFixedInterval((long) (Math.random() * scale));
		cfg.setStorageType(Math.random() > 0.5 ? StorageType.ON_VALUE_UPDATE : StorageType.ON_VALUE_CHANGED);
		return cfg;
	}

	static void generateRandomData(final RecordedDataStorage data, final long startTime, final long endTime, final long step) throws DataRecorderException {
		long start = startTime;
		int cnt = 0;
		while (cnt++ < (Math.random() * 20) && start < endTime) {
			final SampledValue sv = new SampledValue(new FloatValue((float) Math.random()), start, Quality.GOOD);
			data.insertValue(sv);
			start += step + ((Math.random()-0.5) * step);
		}
	}

	static Collection<RecordedDataStorage> createRandomSlotsData(final CloseableDataRecorder slots, final int nrConfigs, final long timescale) throws DataRecorderException {
		final List<RecordedDataStorage> storages = new ArrayList<>(nrConfigs);
		for (int i = 0;i<nrConfigs;i++) {
			storages.add(slots.createRecordedDataStorage("test" + cnt.getAndIncrement(), createConfiguration(timescale)));
		}
		return storages;
	}

	static Collection<RecordedDataStorage> createAndFillRandomSlotsData(final CloseableDataRecorder slots, final int nrConfigs, final long start, final long end, final long step) throws DataRecorderException {
		final List<RecordedDataStorage> storages = new ArrayList<>(nrConfigs);
		for (int i = 0;i<nrConfigs;i++) {
			storages.add(slots.createRecordedDataStorage("test" + cnt.getAndIncrement(), createConfiguration(5 * step)));
		}
		for (RecordedDataStorage s : storages)
			generateRandomData(s, start, end, step);
		return storages;
	}

}
