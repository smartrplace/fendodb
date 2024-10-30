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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.impl.SlotsDb;

public class AppendValuesTest extends SlotsDbTest {

	private final static long FLUSH_PERIOD = 50;
	// DATASET1 fits in a single day-folder, whereas DATASET2 requires two or so
	private final static List<SampledValue> DATASET1 = Arrays.asList(
		new SampledValue(new FloatValue(-3), 0, Quality.GOOD),
		new SampledValue(new FloatValue(23), 5, Quality.GOOD),
		new SampledValue(new FloatValue(23.3F), 102, Quality.GOOD),
		new SampledValue(new FloatValue(12.1F), 1002, Quality.GOOD)
	);
	private final static List<SampledValue> DATASET2 = Arrays.asList(
		new SampledValue(new FloatValue(-3), 0, Quality.GOOD),
		new SampledValue(new FloatValue(23), 5, Quality.GOOD),
		new SampledValue(new FloatValue(23.3F), 102, Quality.GOOD),
		new SampledValue(new FloatValue(12.1F), 1002, Quality.GOOD),
		new SampledValue(new FloatValue(0), 102335, Quality.GOOD),
		new SampledValue(new FloatValue(17), 123001332, Quality.GOOD),
		new SampledValue(new FloatValue(-2), 123002331, Quality.GOOD),
		new SampledValue(new FloatValue(0), 125001322, Quality.GOOD)
	);

	private static void addingValuesWorks(final List<SampledValue> values, final StorageType storageType, final boolean flushImmediately) throws DataRecorderException, IOException, InterruptedException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setFlushPeriod(flushImmediately ? 0 : FLUSH_PERIOD)
			.build();
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(storageType);
			if (storageType == StorageType.FIXED_INTERVAL)
				cfg.setFixedInterval(10000);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg);
			final List<String> ids = instance.getAllRecordedDataStorageIDs();
			Assert.assertEquals(1, ids.size());
			Assert.assertEquals(data.getPath(), ids.get(0));
			Assert.assertTrue(data.isEmpty());
			data.insertValues(values);
			if (!flushImmediately)
				Thread.sleep(3 * FLUSH_PERIOD);
			final RecordedDataStorage data2 = instance.getRecordedDataStorage(data.getPath());
			Assert.assertNotNull(data2);
			Assert.assertFalse(data2.isEmpty());
			if (storageType != StorageType.FIXED_INTERVAL) {
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.getValues(Long.MIN_VALUE).size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.getValues(-1, Long.MAX_VALUE).size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.size(-1, Long.MAX_VALUE));
			} else {
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.getValues(Long.MIN_VALUE).size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.getValues(-1, Long.MAX_VALUE).size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.size(-1, Long.MAX_VALUE) > 0);
			}
			int itSize = 0;
			final Iterator<SampledValue> it = data.iterator();
			while (it.hasNext()) {
				it.next();
				itSize++;
			}
			if (storageType != StorageType.FIXED_INTERVAL)
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(),itSize);
			else
				Assert.assertTrue("Unexpected number of data points in recorded data.", itSize > 0);
		}
	}

	@Test
	public void addingValuesWorks0() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET1, StorageType.ON_VALUE_UPDATE, true);
	}

	@Test
	public void addingValuesWorks1() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET2, StorageType.ON_VALUE_UPDATE, true);
	}

	public void addingValuesWorks3() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET1, StorageType.FIXED_INTERVAL, true);
	}

	@Test
	public void addingValuesWorks4() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET2, StorageType.FIXED_INTERVAL, true);
	}

	@Test
	public void addingValuesWorks5() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET1, StorageType.ON_VALUE_UPDATE, false);
	}

	@Test
	public void addingValuesWorks6() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET2, StorageType.ON_VALUE_UPDATE, false);
	}

	public void addingValuesWorks7() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET1, StorageType.FIXED_INTERVAL, false);
	}

	@Test
	public void addingValuesWorks8() throws DataRecorderException, IOException, InterruptedException {
		addingValuesWorks(DATASET2, StorageType.FIXED_INTERVAL, false);
	}

	private static void addingValuesWithRestartWorks(final List<SampledValue> values, final StorageType storageType, final boolean flushImmediately) throws DataRecorderException, IOException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setFlushPeriod(flushImmediately ? 0 : FLUSH_PERIOD)
			.build();
		final String path;
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(storageType);
			if (storageType == StorageType.FIXED_INTERVAL)
				cfg.setFixedInterval(10000);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg);
			path = data.getPath();
			Assert.assertTrue(data.isEmpty());
			data.insertValues(values);
			Assert.assertFalse(data.isEmpty());
			try {
				// no need to wait for flush here, since closing the db must lead to a flush
				Thread.sleep(1000);
			} catch (InterruptedException ex) {
			}
		}
		try (final SlotsDb instance2 = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, null, null)) {
			final List<String> ids = instance2.getAllRecordedDataStorageIDs();
			Assert.assertEquals(1, ids.size());
			Assert.assertEquals(path, ids.get(0));
			final RecordedDataStorage data = instance2.getRecordedDataStorage(path);
			Assert.assertNotNull(data);
			Assert.assertFalse("Log data not found",data.isEmpty());
			if (storageType != StorageType.FIXED_INTERVAL) {
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.getValues(Long.MIN_VALUE).size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.getValues(-1, Long.MAX_VALUE).size());
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(), data.size(-1, Long.MAX_VALUE));
			} else {
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.getValues(Long.MIN_VALUE).size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.getValues(-1, Long.MAX_VALUE).size() > 0);
				Assert.assertTrue("Unexpected number of data points in recorded data.", data.size(-1, Long.MAX_VALUE) > 0);
			}
			int itSize = 0;
			final Iterator<SampledValue> it = data.iterator();
			while (it.hasNext()) {
				it.next();
				itSize++;
			}
			if (storageType != StorageType.FIXED_INTERVAL)
				Assert.assertEquals("Unexpected number of data points in recorded data.", values.size(),itSize);
			else
				Assert.assertTrue("Unexpected number of data points in recorded data.", itSize > 0);
		}
	}

	@Test
	public void addingValuesWithRestartWorks0() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET1, StorageType.ON_VALUE_UPDATE, true);
	}

	@Test
	public void addingValuesWithRestartWorks1() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET2, StorageType.ON_VALUE_UPDATE, true);
	}

	@Test
	public void addingValuesWithRestartWorks3() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET1, StorageType.FIXED_INTERVAL, true);
	}

	@Test
	public void addingValuesWithRestartWorks4() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET2, StorageType.FIXED_INTERVAL, true);
	}

	@Test
	public void addingValuesWithRestartWorks5() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET1, StorageType.ON_VALUE_UPDATE, false);
	}

	@Test
	public void addingValuesWithRestartWorks6() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET2, StorageType.ON_VALUE_UPDATE, false);
	}

	@Test
	public void addingValuesWithRestartWorks7() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET1, StorageType.FIXED_INTERVAL, false);
	}

	@Test
	public void addingValuesWithRestartWorks8() throws DataRecorderException, IOException {
		addingValuesWithRestartWorks(DATASET2, StorageType.FIXED_INTERVAL, false);
	}

	private static void iteratorWorks(final List<SampledValue> values) throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setFlushPeriod(0)
			.setUseCompatibilityMode(true)
			.build();
		final String path;
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg);
			path = data.getPath();
			Assert.assertTrue(data.isEmpty());
			data.insertValues(values);
		}
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataStorage data = instance.getRecordedDataStorage(path);
			Assert.assertFalse(data.isEmpty());
			final List<Long> timestamps = new ArrayList<>(values.size());
			final Iterator<SampledValue> it = data.iterator(values.get(0).getTimestamp(), values.get(values.size()-1).getTimestamp()+1);
			while (it.hasNext()) {
				final SampledValue sv = it.next();
				final long t = sv.getTimestamp();
				SampledValue corresponding = null;
				for (SampledValue sv0 : values) {
					if (sv0.getTimestamp() == t) {
						corresponding = sv0;
						timestamps.add(t);
						break;
					}
				}
				Assert.assertNotNull("Iterator returned additional point: " + sv,corresponding);
			}
			Assert.assertEquals("Iterator returned too few data points", values.size(), timestamps.size());
		}
	}

	@Test
	public void iteratorWorks0() throws IOException, DataRecorderException {
		iteratorWorks(Arrays.asList(
			new SampledValue(new FloatValue(12), ONE_DAY * 100 + 26, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 100 + 234, Quality.GOOD)
		));
	}

	@Test
	public void iteratorWorks1() throws IOException, DataRecorderException {
		iteratorWorks(Arrays.asList(
			new SampledValue(new FloatValue(12), ONE_DAY * 100 + 26, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 100 + 234, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 101, Quality.GOOD)
		));
	}

	@Test
	public void iteratorWorks2() throws IOException, DataRecorderException {
		iteratorWorks(Arrays.asList(
			new SampledValue(new FloatValue(12), ONE_DAY * 100 + 26, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 100 + 234, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 102 + 12, Quality.GOOD),
			new SampledValue(new FloatValue(12.5F), ONE_DAY * 102 + 122, Quality.GOOD)
		));
	}
	
	@Test
	public void pastTimestampsAreIgnored() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), 200, Quality.GOOD);
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), 100, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			data.insertValue(sv2);
			Assert.assertEquals("Unexpected data size after inserting two points at the same timestamp", 1, data.getValues(Long.MIN_VALUE).size());
			Assert.assertEquals("Data point has unexpected value", sv1.getValue().getIntegerValue(), 
						data.getValues(Long.MIN_VALUE).get(0).getValue().getIntegerValue());
		}
	}
	
	private void sameTimestampValueIsIgnored(final RecordedDataConfiguration cfg1) throws IOException, DataRecorderException {
		this.sameTimestampValueIsIgnored(cfg1, null);
	}
	
	private void sameTimestampValueIsIgnored(final RecordedDataConfiguration cfg1, final RecordedDataConfiguration cfg2) throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final long t = 100;
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), t, Quality.GOOD);
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), t, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg1);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			if (cfg2 != null)
				data.setConfiguration(cfg2);
			data.insertValue(sv2);
			Assert.assertEquals("Unexpected data size after inserting two points at the same timestamp: " +
					data.getValues(Long.MIN_VALUE).stream().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
					1, data.getValues(Long.MIN_VALUE).size());
			Assert.assertEquals("Data point has unexpected value", sv1.getValue().getIntegerValue(), 
						data.getValues(Long.MIN_VALUE).get(0).getValue().getIntegerValue());
			final Iterator<SampledValue> it = data.iterator();
			Assert.assertTrue(it.hasNext());
			it.next();
			Assert.assertFalse("Iterator has unexpected number of data points", it.hasNext());
		}
	}

	@Test
	public void sameTimestampIsIgnoredOnValueUpdate() throws IOException, DataRecorderException {
		final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
		cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
		this.sameTimestampValueIsIgnored(cfg);
	}
	
	@Test
	public void sameTimestampIsIgnoredFixedInterval1() throws IOException, DataRecorderException {
		final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
		cfg.setStorageType(StorageType.FIXED_INTERVAL);
		cfg.setFixedInterval(100);
		this.sameTimestampValueIsIgnored(cfg);
	}
	
	@Test
	public void sameTimestampIsIgnoredFixedInterval2() throws IOException, DataRecorderException {
		final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
		cfg.setStorageType(StorageType.FIXED_INTERVAL);
		cfg.setFixedInterval(67);
		this.sameTimestampValueIsIgnored(cfg);
	}
	
	@Test
	public void sameTimestampIsIgnoredTwoConfigs1() throws IOException, DataRecorderException {
		final RecordedDataConfiguration cfg1 = new RecordedDataConfiguration();
		cfg1.setStorageType(StorageType.ON_VALUE_UPDATE);
		final RecordedDataConfiguration cfg2 = new RecordedDataConfiguration();
		cfg2.setStorageType(StorageType.FIXED_INTERVAL);
		cfg2.setFixedInterval(100);
		this.sameTimestampValueIsIgnored(cfg1, cfg2);
	}
	
	@Test
	public void sameTimestampIsIgnoredFixedInterval3() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), 51, Quality.GOOD);
		 // will be stored under timestamp 100 due to fixed interval mode with interval 100
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), 149, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.FIXED_INTERVAL);
			cfg.setFixedInterval(100);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			data.insertValue(sv2);
			final List<SampledValue> values = data.getValues(Long.MIN_VALUE);
			Assert.assertTrue("Timestamp ordering invalid: " + 
					values.stream().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
					values.size() <= 1 || values.get(0).getTimestamp() < values.get(1).getTimestamp());
			final Iterator<SampledValue> it = data.iterator();
			Assert.assertTrue(it.hasNext());
			final SampledValue first = it.next();
			if (it.hasNext()) {
				final SampledValue second = it.next();
				Assert.assertTrue("Iterator timestamp ordering invalid: " + 
						Stream.<SampledValue> builder().add(first).add(second).build().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
						second.getTimestamp() > first.getTimestamp());
			}
		}
	}
	
	@Test
	public void sameTimestampIsIgnoredTwoConfigs2() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), 100, Quality.GOOD);
		 // will be stored under timestamp 100 due to fixed interval mode with interval 100
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), 149, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg1 = new RecordedDataConfiguration();
			cfg1.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataConfiguration cfg2 = new RecordedDataConfiguration();
			cfg2.setStorageType(StorageType.FIXED_INTERVAL);
			cfg2.setFixedInterval(100);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg1);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			if (cfg2 != null)
				data.setConfiguration(cfg2);
			data.insertValue(sv2);
			final List<SampledValue> values = data.getValues(Long.MIN_VALUE);
			Assert.assertTrue("Timestamp ordering invalid: " + 
					values.stream().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
					values.size() <= 1 || values.get(0).getTimestamp() < values.get(1).getTimestamp());
			final Iterator<SampledValue> it = data.iterator();
			Assert.assertTrue(it.hasNext());
			final SampledValue first = it.next();
			if (it.hasNext()) {
				final SampledValue second = it.next();
				Assert.assertTrue("Iterator timestamp ordering invalid: " + 
						Stream.<SampledValue> builder().add(first).add(second).build().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
						second.getTimestamp() > first.getTimestamp());
			}
		}
	}
	
	@Test
	public void sameTimestampIsIgnoredTwoConfigs3() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), 51, Quality.GOOD);
		 // will be stored under timestamp 100 due to fixed interval mode with interval 100
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), 100, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg2 = new RecordedDataConfiguration();
			cfg2.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataConfiguration cfg1 = new RecordedDataConfiguration();
			cfg1.setStorageType(StorageType.FIXED_INTERVAL);
			cfg1.setFixedInterval(100);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg1);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			if (cfg2 != null)
				data.setConfiguration(cfg2);
			data.insertValue(sv2);
			final List<SampledValue> values = data.getValues(Long.MIN_VALUE);
			Assert.assertTrue("Timestamp ordering invalid: " + 
					values.stream().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
					values.size() <= 1 || values.get(0).getTimestamp() < values.get(1).getTimestamp());
			final Iterator<SampledValue> it = data.iterator();
			Assert.assertTrue(it.hasNext());
			final SampledValue first = it.next();
			if (it.hasNext()) {
				final SampledValue second = it.next();
				Assert.assertTrue("Iterator timestamp ordering invalid: " + 
						Stream.<SampledValue> builder().add(first).add(second).build().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
						second.getTimestamp() > first.getTimestamp());
			}
		}
	}
	
	@Test
	public void previousTimestampIsIgnoredTwoConfigs() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		final SampledValue sv1 = new SampledValue(new IntegerValue(17), 101, Quality.GOOD);
		 // will be stored under timestamp 100 due to fixed interval mode with interval 100
		final SampledValue sv2 = new SampledValue(new IntegerValue(13), 149, Quality.GOOD);
		try (final SlotsDb instance = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null, config, null)) {
			final RecordedDataConfiguration cfg1 = new RecordedDataConfiguration();
			cfg1.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataConfiguration cfg2 = new RecordedDataConfiguration();
			cfg2.setStorageType(StorageType.FIXED_INTERVAL);
			cfg2.setFixedInterval(100);
			final RecordedDataStorage data = instance.createRecordedDataStorage("test", cfg1);
			Assert.assertTrue(data.isEmpty());
			data.insertValue(sv1);
			if (cfg2 != null)
				data.setConfiguration(cfg2);
			data.insertValue(sv2);
			final List<SampledValue> values = data.getValues(Long.MIN_VALUE);
			Assert.assertTrue("Timestamp ordering invalid: " + 
					values.stream().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
					values.size() <= 1 || values.get(0).getTimestamp() < values.get(1).getTimestamp());
			final Iterator<SampledValue> it = data.iterator();
			Assert.assertTrue(it.hasNext());
			final SampledValue first = it.next();
			if (it.hasNext()) {
				final SampledValue second = it.next();
				Assert.assertTrue("Iterator timestamp ordering invalid: " + 
						Stream.<SampledValue> builder().add(first).add(second).build().map(sv -> "(" + sv.getTimestamp() + ", " + sv.getValue().getDoubleValue() + ")").collect(Collectors.toList()), 
						second.getTimestamp() > first.getTimestamp());
			}
		}
	}

}
