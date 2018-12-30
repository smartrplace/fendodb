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
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.impl.SlotsDb;

public class DbTest extends FactoryTest {

	protected volatile CloseableDataRecorder sdb;

	@Before
	public void setupDb() {
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setFlushPeriod(0)
				.build();
		try {
			sdb = factory.getInstance(Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER), config);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@After
	public void closeDb() {
		try {
			sdb.close();
		} catch (Exception e) {}
	}


	protected static Value[] createValues(float[] values) {
		Value[] v = new Value[values.length];
		for (int i=0;i<values.length;i++) {
			v[i] = new FloatValue(values[i]);
		}
		return v;
	}

	protected static void addValues(RecordedDataStorage rds, long[] timestamps, Value[] values) throws DataRecorderException {
		if (timestamps.length != values.length)
			throw new IllegalArgumentException("Number of timestamps must match number of values, got " + timestamps.length + " and " + values.length);
		List<SampledValue> svs = new ArrayList<>();
		long lastT = Long.MIN_VALUE;
		long t;
		for (int i=0;i<timestamps.length;i++) {
			t = timestamps[i];
			if (i > 0 && t <= lastT)
				throw new IllegalArgumentException("Timestamps not ordered chronologically");
			lastT = t;
			svs.add(new SampledValue(values[i],t, Quality.GOOD));
		}
		rds.insertValues(svs);
	}

}
