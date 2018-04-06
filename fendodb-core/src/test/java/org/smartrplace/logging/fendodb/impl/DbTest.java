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
