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

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;

public class UpdateTest extends FactoryTest {
	
	private static class UpdateListener implements FendoDbFactory.SlotsDbListener {
		
		private volatile DataRecorderReference newInstance;
		private final CloseableDataRecorder instance;
		private volatile CountDownLatch startedLatch = new CountDownLatch(1);
		private volatile CountDownLatch doubleStartedLatch = new CountDownLatch(2);
		private volatile CountDownLatch stoppedLatch = new CountDownLatch(1);
		
		public UpdateListener(CloseableDataRecorder instance) {
			this.instance = instance;
		}
		
		public void reset() {
			this.startedLatch = new CountDownLatch(1);
			this.doubleStartedLatch = new CountDownLatch(2);
			this.stoppedLatch = new CountDownLatch(1);
		}

		@Override
		public void databaseStarted(DataRecorderReference ref) {
			if (!instance.getPath().equals(ref.getPath())) {
				return;
			}
			newInstance = ref;
			startedLatch.countDown();
			doubleStartedLatch.countDown();
		}

		@Override
		public void databaseClosed(DataRecorderReference ref) {
			if (!instance.getPath().equals(ref.getPath()))
				return;
			newInstance = null;
			stoppedLatch.countDown();
		}
		
		void assertStarted(long timeout, TimeUnit unit) throws InterruptedException {
			Assert.assertTrue("SlotsDb instance did not start",startedLatch.await(timeout, unit));
			Assert.assertFalse("Too many available callbacks for database", doubleStartedLatch.await(1, TimeUnit.SECONDS));
		}
		
		void assertStopped(long timeout, TimeUnit unit) throws InterruptedException {
			Assert.assertTrue("SlotsDb instance did not stop",stoppedLatch.await(timeout, unit));
		}
		
	}

	@SuppressWarnings("resource")
	private void updateWorks(
			final FendoDbConfiguration initialConfig,
			final FendoDbConfiguration updatedConfig) throws Exception {
		final int nrTimeseries = 5;
		final UpdateListener listener;
		final Map<String, Integer> nrOfDataPoints;
		CloseableDataRecorder initialCopy = null;
		try (final CloseableDataRecorder slotsInitial= factory.getInstance(testPath, initialConfig)) {
			initialCopy = slotsInitial;
			Assert.assertNotNull(slotsInitial);
			TestUtils.createAndFillRandomSlotsData(slotsInitial,nrTimeseries, 1000, 1000 + 70 * ONE_DAY, 1000);
			// add some more data
			slotsInitial.getAllRecordedDataStorageIDs().stream()
				.map(id -> slotsInitial.getRecordedDataStorage(id))
				.forEach(ts -> {
					try {
						TestUtils.generateRandomData(ts, 80 * ONE_DAY, Long.MAX_VALUE, ONE_DAY);
					} catch (DataRecorderException e) {
						throw new RuntimeException(e);
					}
					
				});
			Assert.assertEquals("Unexpected nr of timeseries in SlotsDb",nrTimeseries, slotsInitial.getAllRecordedDataStorageIDs().size());
			nrOfDataPoints = slotsInitial.getAllTimeSeries().stream()
					.collect(Collectors.toMap(ts -> ts.getPath(), ts -> ts.size()));
			listener = new UpdateListener(slotsInitial);

			factory.addDatabaseListener(listener);
			listener.assertStarted(10, TimeUnit.SECONDS);
			listener.reset();
			try {
			slotsInitial.updateConfiguration(updatedConfig);
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}
		Assert.assertFalse("Database still active",initialCopy.isActive());
		Thread.sleep(2000);
		listener.assertStopped(5, TimeUnit.SECONDS);
		listener.assertStarted(10, TimeUnit.SECONDS); // 1 minute?
		try (final CloseableDataRecorder newRecorder = listener.newInstance.getDataRecorder()) {
			Assert.assertNotNull("New SlotsDb instance is null",newRecorder);
			Assert.assertEquals("Unexpected new SlotsDb configuration",	updatedConfig, newRecorder.getConfiguration());
			Assert.assertEquals("Unexpected nr of timeseries in SlotsDb", nrTimeseries, newRecorder.getAllRecordedDataStorageIDs().size());
			for (Map.Entry<String, Integer> entry : nrOfDataPoints.entrySet()) {
				final RecordedData rd = newRecorder.getRecordedDataStorage(entry.getKey());
				Assert.assertNotNull("Recorded data got lost in update: " + entry.getKey(),rd);
				Assert.assertEquals("Recorded data size after update does not match previous size",entry.getValue().intValue(), rd.size());
			}
		}
	}
	
	@Test
	public void tempUnitUpdateWorks() throws Exception {
		final FendoDbConfiguration initialConfig = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.setTemporalUnit(ChronoUnit.MINUTES)
				.build();
		final FendoDbConfiguration newConfig = FendoDbConfigurationBuilder.getInstance(initialConfig)
				.setTemporalUnit(ChronoUnit.MONTHS)
				.build();		
		updateWorks(initialConfig, newConfig);
	}
	
	@Test
	public void compatModeUpdateWorks() throws Exception {
		final FendoDbConfiguration initialConfig = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.setUseCompatibilityMode(true)
				.build();
		final FendoDbConfiguration newConfig = FendoDbConfigurationBuilder.getInstance(initialConfig)
				.setUseCompatibilityMode(false)
				.build();		
		updateWorks(initialConfig, newConfig);
	}
	
	@Test
	public void maxSizeUpdateWorks() throws Exception {
		final FendoDbConfiguration initialConfig = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.setMaxDatabaseSize(100)
				.build();
		final FendoDbConfiguration newConfig = FendoDbConfigurationBuilder.getInstance(initialConfig)
				.setMaxDatabaseSize(101)
				.build();		
		updateWorks(initialConfig, newConfig);
	}
	
}
