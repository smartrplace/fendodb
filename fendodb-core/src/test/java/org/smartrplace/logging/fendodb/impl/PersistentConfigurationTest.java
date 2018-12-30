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
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.impl.SlotsDb;

public class PersistentConfigurationTest extends DbTest {

	private static final String TEST_ID_1 = "test_id_1";
	private static final String TEST_ID_2 = "test_id_2";

	private static final long INTERVAL_1 = 1000;

	private static final long INTERVAL_2 = 2000;
	private static final long INTERVAL_2_UPDATED = 5000;

	@Before
	public void setup() throws DataRecorderException, IOException {

		RecordedDataConfiguration conf = new RecordedDataConfiguration();
		conf.setFixedInterval(INTERVAL_1);
		conf.setStorageType(StorageType.FIXED_INTERVAL);
		RecordedDataStorage rds1 = sdb.createRecordedDataStorage(TEST_ID_1, conf);

		RecordedDataConfiguration conf2 = new RecordedDataConfiguration();
		conf2.setFixedInterval(INTERVAL_2);
		conf2.setStorageType(StorageType.FIXED_INTERVAL);
		RecordedDataStorage rds2 = sdb.createRecordedDataStorage(TEST_ID_2, conf2);

	}

	/**
	 * Test behaviour with wrong arguments
	 * @throws IOException
	 */
	@Test
	public void testGetAllRecordedDataStorageIDs() throws DataRecorderException, IOException {

		List<String> ids = sdb.getAllRecordedDataStorageIDs();

		if (ids.size() != 2) {
			Assert.assertTrue(false);
		}

		Assert.assertTrue(ids.contains(TEST_ID_1));
		Assert.assertTrue(ids.contains(TEST_ID_2));
	}

	@Test
	public void testGetRecordedDataStorage() throws IOException {
		RecordedDataStorage rds = sdb.getRecordedDataStorage(TEST_ID_1);
		RecordedDataConfiguration rdc = rds.getConfiguration();
		Assert.assertEquals(rdc.getFixedInterval(), INTERVAL_1);
	}

	@Test
	public void testUpdateConfiguration() throws DataRecorderException, IOException {
		// update config
		try {
			RecordedDataStorage rds = sdb.getRecordedDataStorage(TEST_ID_2);
			RecordedDataConfiguration rdc = rds.getConfiguration();
			rdc.setFixedInterval(INTERVAL_2_UPDATED);
			rds.update(rdc);
		} finally {
			closeFactory(factory);
			factory = createFactory();
		}
		//read back config
		try (SlotsDb sdb2 = new SlotsDb(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), null,null,null)) {
			RecordedDataStorage rds2 = sdb2.getRecordedDataStorage(TEST_ID_2);
			RecordedDataConfiguration rdc2 = rds2.getConfiguration();
			Assert.assertEquals(rdc2.getFixedInterval(), INTERVAL_2_UPDATED);
		}
	}

}
