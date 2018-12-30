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

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

public class DeletionTest extends FactoryTest {

	// data in day 0 and day 5
	private static final List<SampledValue> testValues = Arrays.asList(
			new SampledValue(new FloatValue(12), 0, Quality.GOOD),
			new SampledValue(new FloatValue(-23.4F), 342, Quality.GOOD),
			new SampledValue(new FloatValue(5), 1231, Quality.GOOD),
			new SampledValue(new FloatValue(12), 5 * ONE_DAY, Quality.GOOD),
			new SampledValue(new FloatValue(13), 5 * ONE_DAY + 324, Quality.GOOD),
			new SampledValue(new FloatValue(14), 5 * ONE_DAY + 1232, Quality.GOOD)
	);


	@Test
	public void deleteBeforeWorks() throws IOException, DataRecorderException {
		final FendoDbConfiguration slotsCfg = FendoDbConfigurationBuilder.getInstance()
				.setTemporalUnit(ChronoUnit.DAYS)
				.build();
		try (final CloseableDataRecorder rec = factory.getInstance(testPath, slotsCfg)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = rec.createRecordedDataStorage("test", cfg);
			ts.insertValues(testValues);
			Assert.assertFalse(ts.isEmpty(Long.MIN_VALUE, 4 * ONE_DAY));
			Assert.assertTrue("Data deletion failed",rec.deleteDataBefore(Instant.ofEpochMilli(4 * ONE_DAY)));
			Assert.assertTrue("Data found although it has been deleted",ts.isEmpty(Long.MIN_VALUE, 4 * ONE_DAY));
		}
	}

	@Test
	public void deleteAfterWorks() throws IOException, DataRecorderException {
		final FendoDbConfiguration slotsCfg = FendoDbConfigurationBuilder.getInstance()
				.setTemporalUnit(ChronoUnit.DAYS)
				.build();
		try (final CloseableDataRecorder rec = factory.getInstance(testPath, slotsCfg)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = rec.createRecordedDataStorage("test", cfg);
			ts.insertValues(testValues);
			Assert.assertFalse(ts.isEmpty(4 * ONE_DAY, Long.MAX_VALUE));
			Assert.assertTrue("Data deletion failed",rec.deleteDataAfter(Instant.ofEpochMilli(4 * ONE_DAY)));
			Assert.assertTrue("Data found although it has been deleted",ts.isEmpty(4 * ONE_DAY, Long.MAX_VALUE));
		}
	}


}
