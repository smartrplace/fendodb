package org.smartrplace.logging.fendodb.impl;

import org.junit.Assert;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.recordeddata.RecordedDataStorage;

public class SizeTest extends DbTest {

	private static void assertSize(final int expected, final RecordedDataStorage storage) {
		Assert.assertEquals("Unexpected size", expected, storage.size());
		Assert.assertEquals("Unexpected size", expected, storage.getValues(Long.MIN_VALUE).size());
	}
	
	private static void assertSize(final int expected, final RecordedDataStorage storage, final long start, final long end) {
		Assert.assertEquals("Unexpected size", expected, storage.size(start, end));
		Assert.assertEquals("Unexpected size", expected, storage.getValues(start, end).size());
	}
	
	@Test
	public void sizeWorksFixedInterval0() throws DataRecorderException {
		RecordedDataConfiguration conf = new RecordedDataConfiguration();
		conf.setFixedInterval(1000);
		conf.setStorageType(StorageType.FIXED_INTERVAL);
		RecordedDataStorage storage = sdb.createRecordedDataStorage("sizeTest0", conf);
		assertSize(0, storage);
		final SampledValue value = new SampledValue(new FloatValue(12.4F), ONE_DAY/2, Quality.GOOD);
		storage.insertValue(value);
		assertSize(1, storage);
		assertSize(1, storage, 0, ONE_DAY);
	}
	
	@Test
	public void sizeWorksFlexible0() throws DataRecorderException {
		RecordedDataConfiguration conf = new RecordedDataConfiguration();
		conf.setStorageType(StorageType.ON_VALUE_UPDATE);
		RecordedDataStorage storage = sdb.createRecordedDataStorage("sizeTest1", conf);
		assertSize(0, storage);
		final SampledValue value = new SampledValue(new FloatValue(12.4F), ONE_DAY/2, Quality.GOOD);
		storage.insertValue(value);
		assertSize(1, storage);
		assertSize(1, storage, 0, ONE_DAY);
		assertSize(1, storage, ONE_DAY/2-1, ONE_DAY/2 + 1);
	}
	
	@Test
	public void sizeWorksFlexible1() throws DataRecorderException {
		RecordedDataConfiguration conf = new RecordedDataConfiguration();
		conf.setStorageType(StorageType.ON_VALUE_UPDATE);
		RecordedDataStorage storage = sdb.createRecordedDataStorage("sizeTest2", conf);
		assertSize(0, storage);
		final SampledValue value = new SampledValue(new FloatValue(12.4F), ONE_DAY/2, Quality.GOOD);
		storage.insertValue(value);
		final SampledValue value2 = new SampledValue(new FloatValue(16.2F), 3 * ONE_DAY/2, Quality.GOOD);
		storage.insertValue(value2);
		assertSize(2, storage);
		assertSize(2, storage, 0, 2 * ONE_DAY);
		assertSize(1, storage, ONE_DAY, 2 * ONE_DAY);
	}
	
	@Test
	public void sizeWorksFlexible2() throws DataRecorderException {
		RecordedDataConfiguration conf = new RecordedDataConfiguration();
		conf.setStorageType(StorageType.ON_VALUE_UPDATE);
		RecordedDataStorage storage = sdb.createRecordedDataStorage("sizeTest3", conf);
		assertSize(0, storage);
		final SampledValue value = new SampledValue(new FloatValue(12.4F), ONE_DAY/4, Quality.GOOD);
		storage.insertValue(value);
		final SampledValue value2 = new SampledValue(new FloatValue(16.2F), 3 * ONE_DAY/4, Quality.GOOD);
		storage.insertValue(value2);
		assertSize(1, storage, ONE_DAY / 2, 3 * ONE_DAY/4 + 1);
	}
	
	
}
