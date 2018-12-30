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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

public class PropertiesPersistenceTest extends FactoryTest {

	@Test
	public void tagsPersistenceWorks() throws Exception {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setFlushPeriod(1000)
			.build();
		final String tag0 = "test1";
		final String tag1 = "test2";
		final String val0 = "value1";
		final String val1 = "value2";
		final String id = "test";
		try (final CloseableDataRecorder slots = factory.getInstance(testPath, config)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = slots.createRecordedDataStorage(id, cfg);
			slots.createRecordedDataStorage("dummy", cfg);
			ts.setProperty(tag0, val0);
			ts.setProperty(tag1, val1);
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag0));
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag1));
			Assert.assertEquals(val0, ts.getFirstProperty(tag0));
			Assert.assertEquals(val1, ts.getFirstProperty(tag1));
		}
		restartFactory();
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			final List<FendoTimeSeries> list = slots.getAllTimeSeries();
			Assert.assertFalse(list.isEmpty());
			final FendoTimeSeries ts = list.stream().filter(ts0 -> ts0.getPath().equals(id)).findAny().orElse(null);
			Assert.assertNotNull(ts);
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag0));
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag1));
			Assert.assertEquals(val0, ts.getFirstProperty(tag0));
			Assert.assertEquals(val1, ts.getFirstProperty(tag1));
			final TimeSeriesMatcher filter = SearchFilterBuilder.getInstance()
					.filterByTag(tag1)
					.build();
			final List<FendoTimeSeries> list2 = slots.findTimeSeries(filter);
			Assert.assertFalse("Search by tags returned an empty list",list2.isEmpty());
			final FendoTimeSeries ts2 = list.stream().filter(ts0 -> ts0.getPath().equals(id)).findAny().orElse(null);
			Assert.assertNotNull("Search by tags misses the target time series",ts2);
			Assert.assertEquals("Search by tags returned too many results",1, list2.size());
		}
	}

	@Test
	public void copyingPropertiesWorks() throws Exception {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setFlushPeriod(5000)
			.build();
		final String tag0 = "test1";
		final String tag1 = "test2";
		final String val0 = "value1";
		final String val1 = "value2";
		final String id = "test";
		final Path path2 = Paths.get("testPath2");
		try (final CloseableDataRecorder slots = factory.getInstance(testPath, config)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = slots.createRecordedDataStorage(id, cfg);
			slots.createRecordedDataStorage("dummy", cfg);
			ts.setProperty(tag0, val0);
			ts.setProperty(tag1, val1);
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag0));
			Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag1));
			Assert.assertEquals(val0, ts.getFirstProperty(tag0));
			Assert.assertEquals(val1, ts.getFirstProperty(tag1));

		}
		Thread.sleep(100);
		closeFactory();
		if (Files.exists(path2))
			FileUtils.deleteDirectory(path2.toFile());
		startFactory();
		// closing the database once ensures that all data is flushed to disk, hence we need to reopen it here
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			try (final CloseableDataRecorder slots2 = slots.copy(path2, config).getDataRecorder()) {
				Assert.assertNotNull(slots2);
				final FendoTimeSeries ts = slots2.getRecordedDataStorage(id);
				Assert.assertNotNull(ts);
				Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag0));
				Assert.assertTrue("Tag missing",ts.getProperties().containsKey(tag1));
				Assert.assertEquals(val0, ts.getFirstProperty(tag0));
				Assert.assertEquals(val1, ts.getFirstProperty(tag1));
			}
		} finally {
			Thread.sleep(100);
			closeFactory();
			FileUtils.deleteDirectory(path2.toFile());
		}
	}

}
