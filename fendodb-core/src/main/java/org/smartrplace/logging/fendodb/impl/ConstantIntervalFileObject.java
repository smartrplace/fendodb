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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.impl.FendoCache.FendoInstanceCache;

public class ConstantIntervalFileObject extends FileObject {

	protected ConstantIntervalFileObject(File file, FendoInstanceCache cache) throws IOException {
		super(file, cache);
	}

	protected ConstantIntervalFileObject(String fileName, FendoInstanceCache cache) throws IOException {
		super(fileName, cache);
	}

	/**
	 * @return step frequency in seconds
	 */
	@Override
	public long getStoringPeriod() {
		return storagePeriod;
	}

	@Override
	protected void readHeader(DataInputStream dis) throws IOException {
		startTimeStamp = dis.readLong();
		storagePeriod = dis.readLong();
		startTimeStamp = FileObjectProxy.getRoundedTimestamp(startTimeStamp, storagePeriod);
	}

	@Override
	public void append(double value, long timestamp, byte flag) throws IOException {
		long writePosition = getBytePosition(timestamp);
		if (writePosition == length) {
			/*
			 * value for this timeslot has not been saved yet "AND" some value has been stored in last timeslot
			 */
			if (!canWrite) {
				enableOutput();
			}

			dos.writeDouble(value);
			dos.writeByte(flag);
			length += 9;
		}
		else {
			if (length > writePosition) {
				/*
				 * value has already been stored for this timeslot -> handle? AVERAGE, MIN, MAX, LAST speichern?!
				 */
			}
			else {
				/*
				 * there are missing some values missing -> fill up with NaN!
				 */
				if (!canWrite) {
					enableOutput();
				}
				long rowsToFillWithNan = (writePosition - length) / 9;// TODO:
				// stimmt
				// Berechnung?
				for (int i = 0; i < rowsToFillWithNan; i++) {
					dos.writeDouble(Double.NaN); // TODO: festlegen welcher Wert
					// undefined sein soll NaN
					// ok?
					dos.writeByte(Quality.BAD.getQuality()); // TODO:
					// festlegen
					// welcher Wert
					// undefined sein
					// soll 00 ok?
					length += 9;
				}
				dos.writeDouble(value);
				dos.writeByte(flag);
				length += 9;
			}
		}
		/*
		 * close(); OutputStreams will not be closed or flushed. Data will be written to disk after calling flush()
		 * method.
		 */
	}

	@Override
	public long getTimestampForLatestValue() {
		return startTimeStamp + (((length - 16) / 9) - 1) * storagePeriod;
	}

	/**
	 * calculates the position in a file for a certain timestamp
	 *
	 * @param timestamp
	 * @return position
	 */
	private long getBytePosition(long timestamp) {
		if (timestamp >= startTimeStamp) {

			/*
			 * get position for timestamp 117 000: 117 000 - 100 000 = 17 000 17 * 000 / 5 000 = 3.4 Math.round(3.4) = 3
			 * 3*(8+1) = 27 27 + 16 = 43 = position to store to!
			 */
			// long pos = (Math.round((double) (timestamp - startTimeStamp) /
			// storagePeriod) * 9) + 16; /* slower */

			double pos = (double) (timestamp - startTimeStamp) / storagePeriod;
			if (pos % 1 != 0) { /* faster */
				pos = Math.round(pos);
			}
			return (long) (pos * 9 + 16);
		}
		else {
			throw new IllegalArgumentException("Requested timestamp is not in file: timestamp: " + timestamp
					+ "; startTimeStamp: " + startTimeStamp);
		}
	}

	/*
	 * Calculates the closest timestamp to wanted timestamp getByteposition does a similar thing (Math.round()), for
	 * byte position.
	 */
	private long getClosestTimestamp(long timestamp) {
		// return Math.round((double) (timestamp -
		// startTimeStamp)/storagePeriod)*storagePeriod+startTimeStamp; /*
		// slower */

		double ts = (double) (timestamp - startTimeStamp) / storagePeriod;
		if (ts % 1 != 0) {
			ts = Math.round(ts);
		}
		return (long) ts * storagePeriod + startTimeStamp;
	}

	@Override
	public SampledValue read(long timestamp) throws IOException {

		if ((timestamp - startTimeStamp) % storagePeriod == 0) {
			if (timestamp >= startTimeStamp && timestamp <= getTimestampForLatestValueInternal()) {
				if (!canRead) {
					enableInput();
				}
				synchronized (this) {
					fis.getChannel().position(getBytePosition(timestamp));
					Double toReturn = dis.readDouble();
					if (!Double.isNaN(toReturn)) {
						try {
							return new SampledValue(DoubleValues.of(toReturn), timestamp, Quality.getQuality(dis.readByte()));
						} catch(EOFException e) {
							logger.error("Caught EOFException reading from {}", dataFile.getPath(), e);
							return null;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Returns a List of Value Objects containing the measured Values between provided start and end timestamp
	 *
	 * @param start
	 * @param end
	 * @return
	 * @throws IOException
	 */
	@Override
	protected List<SampledValue> readInternal(long start, long end) throws IOException {
		start = getClosestTimestamp(start); // round to: startTimestamp +
		// n*stepIntervall
		long endRounded = getClosestTimestamp(end); // round to: startTimestamp +
		// n*stepIntervall

//		List<SampledValue> toReturn = new Vector<>();
		final List<SampledValue> toReturn = new ArrayList<>();

		if (start < end) {
			if (start < startTimeStamp) {
				// of this file.
				start = startTimeStamp;
			}
			if (endRounded > getTimestampForLatestValueInternal()) {
				endRounded = getTimestampForLatestValueInternal();
			}

			if (!canRead) {
				enableInput();
			}
			long timestampcounter = start;
			long startPos = getBytePosition(start);
			long endPos = getBytePosition(endRounded);
			final byte[] b = new byte[(int) (endPos - startPos) + 9];
			synchronized (this) {
				fis.getChannel().position(startPos);
				dis.read(b, 0, b.length);
			}
			ByteBuffer bb = ByteBuffer.wrap(b);
			// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
			// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
			((Buffer) bb).rewind();

			for (int i = 0; i <= (endPos - startPos) / 9; i++) {
				double d = bb.getDouble();
				Quality s = Quality.getQuality(bb.get());
				if (!Double.isNaN(d)) {
					if (timestampcounter <= end) {
						toReturn.add(new SampledValue(DoubleValues.of(d), timestampcounter, s));
					}
				}
				timestampcounter += storagePeriod;
			}

		}
		else if (start == end) {
			toReturn.add(read(start));
			toReturn.removeAll(Collections.singleton(null)); // ?
		}
		return toReturn; // Always return a list -> might be empty -> never is
		// null, to avoid NP's
	}

	@Override
	protected List<SampledValue> readFullyInternal() throws IOException {
		return readInternal(startTimeStamp, getTimestampForLatestValueInternal());
	}

	@Override
	public SampledValue readNextValue(long timestamp) throws IOException {
		// Calculate next Value, round Timestamp to next Value
		timestamp = timestamp + (storagePeriod - ((timestamp - startTimeStamp) % storagePeriod));
		long startPos = getBytePosition(timestamp);
		long endPos = getBytePosition(getTimestampForLatestValueInternal());
		for (int i = 0; i <= (endPos - startPos) / 9; i++) {
			if (timestamp >= startTimeStamp && timestamp <= getTimestampForLatestValueInternal()) {
				if (!canRead) {
					enableInput();
				}
				synchronized (this) {
					fis.getChannel().position(getBytePosition(timestamp));
					Double toReturn = dis.readDouble();
					if (!Double.isNaN(toReturn)) {
						return new SampledValue(DoubleValues.of(toReturn), timestamp, Quality.getQuality(dis.readByte()));
					}
				}
				timestamp += storagePeriod;
			}
		}
		return null;
	}

	@Override
	public SampledValue readPreviousValue(long timestamp) throws IOException {
		// Calculate next Value, round Timestamp to next Value
		timestamp = timestamp + (storagePeriod - ((timestamp - startTimeStamp) % storagePeriod)); // what if storagePeriod changes?
		long startPos = getBytePosition(startTimeStamp);
		long endPos = getBytePosition(timestamp);
		for (int i = 0; i <= (endPos - startPos) / 9; i++) {
			if (timestamp >= startTimeStamp && timestamp <= getTimestampForLatestValueInternal()) {
				if (!canRead) {
					enableInput();
				}
				synchronized (this) {
					fis.getChannel().position(getBytePosition(timestamp));
					Double toReturn = dis.readDouble();
					if (!Double.isNaN(toReturn)) {
						return new SampledValue(DoubleValues.of(toReturn), timestamp, Quality.getQuality(dis.readByte()));
					}
				}
				timestamp -= storagePeriod;
			}
		}
		return null;
	}

    @Override
    public int getDataSetCount() {
    	return (int) ((length - 16) / 9);
    }

	@Override
	public int getDataSetCount(long start, long end) {
		long fileEnd = getTimestampForLatestValueInternal();
		if (start <= startTimeStamp && end >= fileEnd)
			return getDataSetCountInternal();
		else if (start > fileEnd || end < startTimeStamp)
			return 0;
		long startPos = 16;
		long endPos = length;
		if (start > startTimeStamp)
			startPos = getBytePosition(start);
		if (end < fileEnd)
			endPos = getBytePosition(end);
    	return (int) (endPos - startPos)/ 9;

    }

	/*
	 * Methods not required; internal methods not requiring file access
	 */

	@Override
	protected int getDataSetCountInternal() {
		return getDataSetCount();
	}

	@Override
	protected int getDataSetCountInternal(long start, long end) throws IOException {
		return getDataSetCount();
	}

	@Override
	protected long getTimestampForLatestValueInternal() {
		return getTimestampForLatestValue();
	}

}