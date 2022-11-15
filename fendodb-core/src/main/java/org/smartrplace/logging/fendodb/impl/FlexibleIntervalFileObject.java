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
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.impl.FendoCache.FendoInstanceCache;

public class FlexibleIntervalFileObject extends FileObject {

	private long lastTimestamp;
	private static final long HEADERSIZE = 16;
    private static final int DATASETSIZE = (Long.SIZE + Double.SIZE + Byte.SIZE) / Byte.SIZE;
    
    private static final boolean FLUSH_ON_APPEND = Boolean.getBoolean("org.smartrplace.fendodb.flush");
	
	protected FlexibleIntervalFileObject(Path file, FendoInstanceCache cache) throws IOException {
		super(file, cache);
		lastTimestamp = startTimeStamp;
	}
    
	protected FlexibleIntervalFileObject(File file, FendoInstanceCache cache) throws IOException {
		super(file, cache);
		lastTimestamp = startTimeStamp;
	}

	protected FlexibleIntervalFileObject(String fileName, FendoInstanceCache cache) throws IOException {
		super(fileName, cache);
		lastTimestamp = startTimeStamp;
	}

	@Override
	void readHeader(DataInputStream dis) throws IOException {
		startTimeStamp = dis.readLong();
		storagePeriod = dis.readLong(); /* is -1 for disabled storagePeriod */
		lastTimestamp = startTimeStamp;
		// line below should be obsolete, since flexible interval needs no rounded timestamp
		//startTimeStamp = FileObjectProxy.getRoundedTimestamp(startTimeStamp, storagePeriod);
	}

    @Override
    protected void enableOutput() throws IOException {
        super.enableOutput();
        long len = Files.size(dataFile);
        long trashLength = (len - HEADERSIZE) % 17;
        if (trashLength != 0 && len > 16) {
            long offset = ((len - HEADERSIZE) / 17) * 17 + HEADERSIZE;
            logger.warn("File {} has bad length ({}), will append starting at previous good dataset offset {}",
                    dataFile, len, offset);
			try (SeekableByteChannel sbc = Files.newByteChannel(dataFile, StandardOpenOption.WRITE)) {
				sbc.truncate(offset);
			}
            length = offset;
        }
    }

	@Override
	public void append(double value, long timestamp, byte flag) throws IOException {
		// long writePosition = length;
		if (!canWrite) {
			enableOutput();
		}
		if (timestamp > lastTimestamp) {
			ByteBuffer buf = ByteBuffer.allocate(17);
			buf.putLong(timestamp);
			buf.putDouble(value);
			buf.put(flag);
			buf.rewind();
			//channel.write(buf);
			synchronized(this) {
				channel.position(length).write(buf);
			}
			lastTimestamp = timestamp;
			length += 17;
			
            if (FLUSH_ON_APPEND) {
				if (channel instanceof FileChannel) {
					((FileChannel)channel).force(false);
				}
            }
		}

	}

	@Override
	protected long getTimestampForLatestValueInternal() {
		// FIXME: this won't work ... if read(String, long, long) is invoked a new
		// FileObject is created and lastTimestamp is set to startTimeStamp ...
		// return lastTimestamp;

		// this is only a quickfix so that it works... @author of this class: if there
		// is a better solution pls fix this... otherwise delete all comments in here
		// and lets stick to this solution for now:
		int dataSetCount = getDataSetCountInternal();
		if (dataSetCount > 1) {
			try {
				if (!canRead) {
					enableInput();
				}
				ByteBuffer bb = ByteBuffer.allocate(8);
				synchronized (this) {
					channel.position((dataSetCount - 1) * DATASETSIZE + HEADERSIZE);					
					channel.read(bb);
				}
				bb.flip();
				long l = bb.getLong();
				return l;
			} catch (IOException | NullPointerException e) {
				logger.error(e.getMessage(), e);
				// FIXME return negative value to signalize error? for now simply
				// return startTimeStamp ...
			}
		}
		return startTimeStamp;
	}

	@Override
	protected List<SampledValue> readInternal(long start, long end) throws IOException {
		final List<SampledValue> toReturn = new ArrayList<>(getDataSetCount());
		if (!canRead) {
			enableInput();
		}

		long startpos = HEADERSIZE;
		final byte[] b = new byte[(int) (length - HEADERSIZE)];
		ByteBuffer bb = ByteBuffer.wrap(b);
		synchronized (this) {
			channel.position(startpos).read(bb);
		}
		// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
		// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
		((Buffer) bb).rewind();

		for (int i = 0; i < getDataSetCountInternal(); i++) {
			long timestamp = bb.getLong();
			if (timestamp >= start && timestamp <= end) {
				double d = bb.getDouble();
				Quality s = Quality.getQuality(bb.get());
				if (!Double.isNaN(d)) {
					toReturn.add(new SampledValue(DoubleValues.of(d), timestamp, s));
				}
			} else {
				bb.position(bb.position() + 9);
			}

		}

		// TODO iterate through file and extract the required values.
		return toReturn;
	}

	@Override
	protected List<SampledValue> readFullyInternal() throws IOException {
//		List<SampledValue> toReturn = new Vector<SampledValue>();
		final List<SampledValue> toReturn = new ArrayList<>(getDataSetCountInternal());
		if (!canRead) {
			enableInput();
		}
		long startpos = HEADERSIZE;
		final byte[] b = new byte[(int) (length - HEADERSIZE)];
		ByteBuffer bb = ByteBuffer.wrap(b);
		synchronized (this) {
			channel.position(startpos).read(bb);
		}
		// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
		// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
		((Buffer) bb).rewind();
		final int countOfDataSets = getDataSetCountInternal();
		for (int i = 0; i < countOfDataSets; i++) {
			long timestamp = bb.getLong();
			double d = bb.getDouble();
			Quality s = Quality.getQuality(bb.get());

			if (!Double.isNaN(d)) {
				toReturn.add(new SampledValue(DoubleValues.of(d), timestamp, s));
			}
		}
		return toReturn;
	}

	@Override
	public SampledValue read(long timestamp) throws IOException {
		// TODO Search the special value
		if (!canRead) {
			enableInput();
		}

		long startpos = HEADERSIZE;
		final byte[] b = new byte[(int) (length - HEADERSIZE)];
		ByteBuffer bb = ByteBuffer.wrap(b);
		int read;
		synchronized (this) {
			read = channel.position(startpos).read(bb);
		}
		// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
		// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
		((Buffer) bb).flip();
		final int countOfDataSets = getDataSetCountInternal();
		for (int i = 0; i < countOfDataSets; i++) {
			long timestamp2 = bb.getLong();
			double d = bb.getDouble();
			Quality s = Quality.getQuality(bb.get());
			if (timestamp < timestamp2) {
				//System.out.printf("%s read(%d) = null, bytes read=%d%n", Thread.currentThread().getName(), timestamp, read);
				return null;
			}
			if (!Double.isNaN(d) && timestamp == timestamp2) {
				//System.out.printf("%s read(%d) = %f, bytes read=%d%n", Thread.currentThread().getName(), timestamp, d, read);
				return new SampledValue(DoubleValues.of(d), timestamp2, s);
			}
		}
		//System.out.printf("%s read(%d) = null, bytes read=%d%n", Thread.currentThread().getName(), timestamp, read);
		return null;
	}

	@Override
	public long getStoringPeriod() {
		return -1;
	}

	@Override
	public SampledValue readNextValue(long timestamp) throws IOException {
		if (!canRead) {
			enableInput();
		}
		long startpos = HEADERSIZE;
		ByteBuffer bb = ByteBuffer.allocate((int) (length - HEADERSIZE));
		final int countOfDataSets = getDataSetCountInternal();

		synchronized (this) {
			channel.position(startpos).read(bb);
		}
		// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
		// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
		((Buffer) bb).flip();

		for (int i = 0; i < countOfDataSets; i++) {
			long nextTimeStamp = bb.getLong();
			double d = bb.getDouble();
			Quality s = Quality.getQuality(bb.get());
			if (!Double.isNaN(d) && timestamp <= nextTimeStamp) {
				//System.out.printf("%s readNextValue(%d) = %f, file data size=%d, countOfDataSets=%d, data read=%d%n",
						//Thread.currentThread().getName(), timestamp, d, length-HEADERSIZE, countOfDataSets, read);
				return new SampledValue(DoubleValues.of(d), nextTimeStamp, s);
			}
		}
		//System.out.printf("%s readNextValue(%d) = null, file data size=%d, countOfDataSets=%d, data read=%d%n",
				//Thread.currentThread().getName(), timestamp, length-HEADERSIZE, countOfDataSets, read);
		return null;
	}

	@Override
	public SampledValue readPreviousValue(long timestamp) throws IOException {
		if (!canRead) {
			enableInput();
		}
		synchronized (this) {
			try {
				SeekableByteChannel in = channel.position(HEADERSIZE);
				ByteBuffer dsbuf = ByteBuffer.allocate(17);
				final int countOfDataSets = getDataSetCountInternal();
				long tcand = Long.MIN_VALUE;
				double dcand = Double.NaN;
				Quality qcand = null;
				for (int i = 0; i < countOfDataSets; i++) {
					int read = in.read(dsbuf);
					dsbuf.rewind();
					//assert read == 16;
					long timestamp2 = dsbuf.getLong();
					double d = dsbuf.getDouble();
					Quality s = Quality.getQuality(dsbuf.get());
					if (!Double.isNaN(d) && timestamp >= timestamp2) {
						tcand = timestamp2;
						dcand = d;
						qcand = s;
						//				candidate = new SampledValue(DoubleValues.of(d), timestamp2, s);
					} else if (timestamp < timestamp2) {
						break;
					}
					dsbuf.rewind();
				}
				if (!Double.isNaN(dcand)) {
					return new SampledValue(DoubleValues.of(dcand), tcand, qcand);
				}
			} catch (RuntimeException re) {
				throw new IOException("bug or corrupted file " + dataFile, re);
			}
		}
		return null;
	}

	@Override
	protected int getDataSetCountInternal() {
		return (int) ((length - HEADERSIZE) / DATASETSIZE);
	}

	@Override
	protected int getDataSetCountInternal(long start, long end) throws IOException {
		long fileEnd = getTimestampForLatestValueInternal();
		if (start <= startTimeStamp && end >= fileEnd)
			return getDataSetCountInternal();
		else if (start > fileEnd || end < startTimeStamp)
			return 0;
		if (!canRead) {
			enableInput();
		}
		long startpos = HEADERSIZE;
		final byte[] b = new byte[(int) (length - HEADERSIZE)];
		ByteBuffer bb = ByteBuffer.wrap(b);
		synchronized (this) {
			channel.position(startpos).read(bb);
		}
		// casting is a hack to avoid incompatibility when building this on Java 9 and run on Java 8
		// ByteBuffer#rewind used to return a Buffer in Jdk8, but from Java 9 on returns a ByteBuffer
		((Buffer) bb).rewind();
		int cnt = 0;
		final int countOfDataSets = getDataSetCountInternal();
		for (int i = 0; i < countOfDataSets; i++) {
			long timestamp2 = bb.getLong();
			double d = bb.getDouble();
			if (timestamp2 > end)
				return cnt;
			if (!Double.isNaN(d) && timestamp2 >= start) {
				cnt++;
			}
			bb.get();
		}
		
		return cnt;
	}

}
