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

import java.io.IOException;
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
import org.smartrplace.logging.fendodb.impl.SlotsDb;

/**
 *
 */
public class ConfigurationTest extends FactoryTest {

	@Test(expected=SecurityException.class)
	public void readOnlyWorks() throws Exception {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setReadOnlyMode(true)
			.build();
		try (final CloseableDataRecorder slots = factory.getInstance(testPath, config)) {
			Assert.assertTrue(slots.getConfiguration().isReadOnlyMode());
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			slots.createRecordedDataStorage("test", cfg);
		}
	}

	@Test(expected=SecurityException.class)
	public void readOnlyWorks2() throws IOException, DataRecorderException {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
			.setReadOnlyMode(false)
			.build();
		final String path;
		try (final SlotsDb slots = new SlotsDb(testPath, null, config, null)) {
			Assert.assertFalse(slots.getConfiguration().isReadOnlyMode());
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final RecordedDataStorage rds = slots.createRecordedDataStorage("test", cfg);
			path = rds.getPath();
		}
		final FendoDbConfiguration config2 = FendoDbConfigurationBuilder.getInstance()
			.setReadOnlyMode(true)
			.build();
		try (final SlotsDb slots = new SlotsDb(testPath, null, config2, null)) {
			Assert.assertTrue(slots.getConfiguration().isReadOnlyMode());
			final RecordedDataStorage rds = slots.getRecordedDataStorage(path);
			Assert.assertNotNull(rds);
			rds.insertValue(new SampledValue(new FloatValue(23), 123, Quality.GOOD));
		}
	}

}
