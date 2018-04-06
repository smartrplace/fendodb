/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
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
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.impl.SlotsDb;

public class RestartDbTest extends SlotsDbTest {
	
	protected SlotsDb createDb() {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		try {
			return new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null,config, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected SlotsDb createDb(final FendoDbConfiguration config) {
		try {
			return new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null,config, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void restartWorks() {
		try (final SlotsDb db = createDb()) {}
		try (final SlotsDb db2 = createDb()) {}
	}
	
	@Test
	public void timeseriesSurviveRestart() throws DataRecorderException {
		final String id = "test";
		try (final SlotsDb db = createDb()) {
			db.createRecordedDataStorage(id, null);
		}
		try (final SlotsDb db2 = createDb()) {
			Assert.assertTrue("Recorded data got lost",db2.getAllRecordedDataStorageIDs().contains(id));
		}
	}	
	
	@Test
	public void valuesSurviveRestart() throws DataRecorderException {
		final String id = "test";
		final List<SampledValue> list;
		try (final SlotsDb db = createDb()) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_CHANGED);
			final RecordedDataStorage storage = db.createRecordedDataStorage(id, cfg);
			final long t0 = 0;
			final long t1 = 33234;
			list = Arrays.asList(
				new SampledValue(new FloatValue(23F), t0, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), t1, Quality.GOOD)
			);
			storage.insertValues(list);
		}
		try (final SlotsDb db2 = createDb()) {
			final RecordedDataStorage storage2 = db2.getRecordedDataStorage(id);
			Assert.assertNotNull("Recorded data got lost", storage2);
			final List<SampledValue> values2  = storage2.getValues(Long.MIN_VALUE);
			Assert.assertEquals("Unexpected number of sampled values",list.size(), values2.size());
			for (SampledValue sv : list) {
				boolean found = false;
				for (SampledValue sv2 : values2) {
					if (sv2.getTimestamp() == sv.getTimestamp()) {
						found = true;
						Assert.assertEquals(sv.getValue().getFloatValue(), sv.getValue().getFloatValue(), 0.01F);
						break;
					}
				}
				Assert.assertTrue(found);
			}
		}
	}
	
	private void nonStandardUnitPersistenceWorks(final TemporalUnit unit) throws DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.setTemporalUnit(unit)
				.build();
		final String id = "test";
		final List<SampledValue> list;
		try (final SlotsDb db = createDb(config)) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_CHANGED);
			final RecordedDataStorage storage = db.createRecordedDataStorage(id, cfg);
			list = Arrays.asList(
				new SampledValue(new FloatValue(23F), 0, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 1, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 103, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 1007, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 1009, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 12432, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 845223, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 2003234, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 15303234, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 342492394, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 2032423234, Quality.GOOD),
				new SampledValue(new FloatValue(-43.34F), 20032343434L, Quality.GOOD)			
			);
			storage.insertValues(list);
		}
		try (final SlotsDb db2 = createDb(config)) {
			final RecordedDataStorage storage2 = db2.getRecordedDataStorage(id);
			Assert.assertNotNull("Recorded data got lost", storage2);
			final List<SampledValue> values2  = storage2.getValues(Long.MIN_VALUE);
			Assert.assertEquals("Unexpected number of sampled values",list.size(), values2.size());
			for (SampledValue sv : list) {
				boolean found = false;
				for (SampledValue sv2 : values2) {
					if (sv2.getTimestamp() == sv.getTimestamp()) {
						found = true;
						Assert.assertEquals(sv.getValue().getFloatValue(), sv.getValue().getFloatValue(), 0.01F);
						break;
					}
				}
				Assert.assertTrue(found);
			}
		}
	}
	
	@Test
	public void minutesWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.MINUTES);
	}
	
	@Test
	public void hoursWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.HOURS);
	}
	@Test
	public void daysWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.DAYS);
	}
	
	@Test
	public void weeksWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.WEEKS);
	}
	
	@Test
	public void monthsWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.MONTHS);
	}
	
	@Test
	public void yearsWork() throws DataRecorderException {
		nonStandardUnitPersistenceWorks(ChronoUnit.YEARS);
	}
	
	@Test
	public void nonExistentReturnsNull() throws IOException {
		final FendoDbFactory factory = createFactory();
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testPath)) {
			Assert.assertNull("Database returned, although it should not exist",recorder);
		} finally {
			closeFactory(factory);
		}
	}
	
	@Test
	public void closedDatabaseRemainsKnown() throws IOException {
		final FendoDbFactory factory = createFactory();
		try {
			try (final CloseableDataRecorder recorder = factory.getInstance(testPath)) {
			}
			try (final CloseableDataRecorder recorder = factory.getExistingInstance(testPath)) {
				Assert.assertNotNull(recorder);
			}
		} finally {
			closeFactory(factory);
		}
	}
	
	@Test
	public void closedDatabaseRemainsKnown2() throws IOException, InterruptedException {
		final FendoDbFactory factory = createFactory();
		try {
			try (final CloseableDataRecorder recorder = factory.getInstance(testPath)) {
			}
			Thread.sleep(2000);
			try (final CloseableDataRecorder recorder = factory.getExistingInstance(testPath)) {
				Assert.assertNotNull(recorder);
			}
		} finally {
			closeFactory(factory);
		}
	}
	
//	@Test
//	public void openingSameDatabaseTwiceFailsWithIO() throws Exception {
//		final SlotsDbFactory factory1 = createFactory();
//		final SlotsDbFactory factory2 = createFactory();
//		try (final CloseableDataRecorder recorder1 = factory1.getInstance(testPath)) {
//			try (final CloseableDataRecorder recorder2 = factory2.getInstance(testPath)) {
//				Assert.fail("Succeeded to open the same database instance twice...");
//			} catch (IOException expected) {}
//		}
//	}
//	
	
}
