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
