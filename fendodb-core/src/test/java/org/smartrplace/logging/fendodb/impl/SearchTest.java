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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

public class SearchTest extends FactoryTest {
	
	private final void testSimpleSetup(final boolean filterByTagOrProperty, final boolean includeBothTags) throws DataRecorderException, IOException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final String tag0 = "test1";
		final String tag1 = "test2";
		final String val0 = "value1";
		final String val1 = "value2";
		final String id0 = "test1";
		final String id1 = "test2";
		try (final CloseableDataRecorder slots = factory.getInstance(testPath, config)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = slots.createRecordedDataStorage(id0, cfg);
			slots.createRecordedDataStorage(id1, cfg);
			ts0.setProperty(tag0, val0);
			ts0.setProperty(tag1, val1);
			Assert.assertTrue("Tag missing",ts0.getProperties().containsKey(tag0));
			Assert.assertTrue("Tag missing",ts0.getProperties().containsKey(tag1));
			Assert.assertEquals(val0, ts0.getFirstProperty(tag0));
			Assert.assertEquals(val1, ts0.getFirstProperty(tag1));
			final SearchFilterBuilder builder = SearchFilterBuilder.getInstance();
			if (filterByTagOrProperty) {
				builder.filterByTag(tag0);
				if (includeBothTags)
					builder.filterByTag(tag1);
			} else {
				builder.filterByProperty(tag0, val0, false);
				if (includeBothTags)
					builder.filterByProperty(tag1, val1, false);
			}
			final TimeSeriesMatcher filter = builder.build();
			final List<FendoTimeSeries> list = slots.findTimeSeries(filter);
			Assert.assertFalse("Search by tags returned an empty list",list.isEmpty());
			final FendoTimeSeries ts2 = list.stream().filter(ts -> ts.getPath().equals(id0)).findAny().orElse(null);
			Assert.assertNotNull("Search by tags misses the target time series",ts2);
			Assert.assertEquals("Search by tags returned too many results",1, list.size());
		}
	}
	
	@Test
	public void searchByTagsWorks0() throws Exception {
		testSimpleSetup(true, false);
	}
	
	@Test
	public void searchByTagsWorks1() throws Exception {
		testSimpleSetup(true, true);
	}
	

	@Test
	public void searchByPropsWorks0() throws Exception {
		testSimpleSetup(false, false);
	}
	
	@Test
	public void searchByPropsWorks1() throws Exception {
		testSimpleSetup(false, true);
	}
	
	@Test
	public void testMultipleProps() throws DataRecorderException, IOException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final String tag0 = "test1";
		final String tag1 = "test2";
		final String val0 = "value1";
		final String val1 = "value2";
		final String id0 = "test1";
		final String id1 = "test2";
		try (final CloseableDataRecorder slots = factory.getInstance(testPath, config)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = slots.createRecordedDataStorage(id0, cfg);
			slots.createRecordedDataStorage(id1, cfg);
			ts0.setProperty(tag0, val0);
			ts0.setProperty(tag1, val1);
			Assert.assertTrue("Tag missing",ts0.getProperties().containsKey(tag0));
			Assert.assertTrue("Tag missing",ts0.getProperties().containsKey(tag1));
			
			Assert.assertEquals(val0, ts0.getFirstProperty(tag0));
			Assert.assertEquals(val1, ts0.getFirstProperty(tag1));
			final SearchFilterBuilder builder = SearchFilterBuilder.getInstance();
			builder.filterByPropertyMultiValue(tag0, Arrays.asList(val0, "someDummyValue"), false);
			final TimeSeriesMatcher filter = builder.build();
			final List<FendoTimeSeries> list = slots.findTimeSeries(filter);
			Assert.assertFalse("Search by props returned an empty list",list.isEmpty());
			final FendoTimeSeries ts2 = list.stream().filter(ts -> ts.getPath().equals(id0)).findAny().orElse(null);
			Assert.assertNotNull("Search by props misses the target time series",ts2);
			Assert.assertEquals("Search by props returned too many results",1, list.size());
		}
	}
	
}
