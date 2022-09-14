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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.impl.FendoCache.FendoInstanceCache;

public abstract class FileObject {

	protected Logger logger = LoggerFactory.getLogger(getClass());
	protected long startTimeStamp = Long.MIN_VALUE; // byte 0-7 in file (cached)
	protected long storagePeriod; // byte 8-15 in file (cached)
	protected final Path dataFile;
	protected volatile SeekableByteChannel channel;
	protected boolean canWrite;
	protected volatile boolean canRead;

	private final FendoInstanceCache cache;

	/*
	 * File length will be cached to avoid system calls and improve I/O Performance
	 */
	protected long length = 0;

	public FileObject(String filename, FendoInstanceCache cache) throws IOException {
		this.cache = cache;
		canWrite = false;
		canRead = false;
		dataFile = getFileForName(filename);
		length = Files.exists(dataFile) ? Files.size(dataFile) : 0;
		if (Files.exists(dataFile) && length >= 16) {
			/*
			 * File already exists -> get file Header (startTime and step-frequency) TODO: compare to starttime and
			 * frequency in constructor! new file needed? update to file-array!
			 */
			enableInput();
			synchronized(dataFile) {
				try (InputStream fis = Files.newInputStream(dataFile);  DataInputStream dis = new DataInputStream(fis)) {
					readHeader(dis);
				}
			}
		}
	}

	private Path getFileForName(String filename) {
		Path f = Paths.get(filename);
		String label = f.getName(f.getNameCount() - 2).toString();
		//FIXME only works for ogema labels
		if (label.length() > 255) {
			Path base = f.getParent().getParent();
			if (label.contains("%")) {
				String decodedLabel;
				try {
					decodedLabel = URLDecoder.decode(label, "UTF-8");
					if (decodedLabel.contains("/")) {
						return base.resolve(decodedLabel).resolve(f.getFileName());
					}
				} catch (UnsupportedEncodingException ex) {
				}
			}
		}
		return f;
	}

	/**
	 * Read 16 bytes of File Header.
	 *
	 * @param dis2
	 */
	abstract void readHeader(DataInputStream dis) throws IOException;
	
	public FileObject(File file, FendoInstanceCache cache) throws IOException {
		this(file.toPath(), cache);
	}

	public FileObject(Path file, FendoInstanceCache cache) throws IOException {
		this.cache = cache;
		canWrite = false;
		canRead = false;
		dataFile = file;
		length = Files.size(dataFile);
		if (Files.exists(dataFile) && length >= 16) {
			/*
			 * File already exists -> get file Header (startTime and step-frequency)
			 */
			try ( InputStream fis = Files.newInputStream(dataFile);  DataInputStream dis = new DataInputStream(fis)) {
				readHeader(dis);
			}
		}
	}

	/*
	 * Requires the SlotsDbStorage write lock to be held.
	 */
	protected void enableOutput() throws IOException {
		/*
		 * enabling output
		 */
		if (canWrite) {
			return;
		}
		synchronized(dataFile) {
			if (canWrite) {
				return;
			}
			if (channel != null) {
				channel.close();
			}
			channel = Files.newByteChannel(dataFile, StandardOpenOption.APPEND);
					//Files.newByteChannel(dataFile, StandardOpenOption.WRITE);
			canWrite = true;
			canRead = false;
		}
	}

	/*
	 * Requires the SlotsDbStorage read lock to be held.
	 */
	protected final void enableInput() throws IOException {
		if (canRead) {
			return;
		}
		synchronized (dataFile) {
			if (canRead) {
				return;
			}
			if (channel != null) {
				channel.close();
			}
			cache.invalidate();
			assert cache.getCache() == null : "Invalidated cache is still alive";
			channel = Files.newByteChannel(dataFile, StandardOpenOption.READ);
			canWrite = false;
			canRead = true;
		}
	}

	/**
	 * creates the file, if it doesn't exist.
	 *
	 * @param startTimeStamp for file header
	 */
	synchronized void createFileAndHeader(long startTimeStamp, long stepIntervall) throws IOException {
		if (!Files.exists(dataFile) || length < 16) {
			Files.createDirectories(dataFile.getParent());
			if (Files.exists(dataFile) && length < 16) {
				Files.delete(dataFile); // file corrupted (header shorter that 16 bytes)
			}
			Files.createFile(dataFile);

			//OLD 
			//this.startTimeStamp = FileObjectProxy.getRoundedTimestamp(startTimeStamp, stepIntervall);
			this.startTimeStamp = startTimeStamp;

			this.storagePeriod = stepIntervall;

			/*
			 * Do not close Output streams, because after writing the header -> data will follow!
			 */
			enableOutput();
			ByteBuffer header = ByteBuffer.allocate(16);
			header.putLong(this.startTimeStamp);
			header.putLong(stepIntervall);
			header.rewind();
			channel.write(header);
			length += 16;
			/* wrote 2*8 Bytes */
		}
	}

	public List<SampledValue> readFully() throws IOException {
		List<SampledValue> values = cache.getCache();
		if (values != null) {
			return values;
		}
		values = readFullyInternal();
		// store until next write access
		cache.cache(Collections.unmodifiableList(values));
		return values;
	}

	public List<SampledValue> read(long start, long end) throws IOException {
		if (start <= startTimeStamp && end >= getTimestampForLatestValue()) {
			return readFully(); // caches values
		}
		final List<SampledValue> values = cache.getCache();
		if (values != null) {
			final List<SampledValue> copy = new ArrayList<>(values.size());
			for (final SampledValue sv : values) {
				final long t = sv.getTimestamp();
				if (t < start) {
					continue;
				}
				if (t > end) {
					break;
				}
				copy.add(sv);
			}
			return copy;
		}
		return readInternal(start, end);
	}

	;


	public int getDataSetCount() {
		final List<SampledValue> values = cache.getCache();
		if (values != null) {
			return values.size();
		}
		return getDataSetCountInternal();
	}

	;
	
	public int getDataSetCount(long start, long end) throws IOException {
		final List<SampledValue> values = cache.getCache();
		if (values != null) {
			if (start <= startTimeStamp && !values.isEmpty()) {
				long endTimeStamp = values.get(values.size() - 1).getTimestamp();
				if (end >= endTimeStamp) {
					return values.size();
				}
			}
			int cnt = 0;
			for (final SampledValue sv : values) {
				final long t = sv.getTimestamp();
				if (t < start) {
					continue;
				}
				if (t > end) {
					break;
				}
				cnt++;
			}
			return cnt;
		}
		return getDataSetCountInternal(start, end);
	}

	;


	public long getTimestampForLatestValue() {
		final List<SampledValue> values = cache.getCache();
		if (values != null && !values.isEmpty()) {
			return values.get(values.size() - 1).getTimestamp();
		}
		return getTimestampForLatestValueInternal();
	}

	;

	public abstract void append(double value, long timestamp, byte flag) throws IOException;

	protected abstract List<SampledValue> readInternal(long start, long end) throws IOException;

	protected abstract List<SampledValue> readFullyInternal() throws IOException;

	public abstract SampledValue read(long timestamp) throws IOException;

	public abstract SampledValue readNextValue(long timestamp) throws IOException;

	public abstract SampledValue readPreviousValue(long timestamp) throws IOException;

	protected abstract long getTimestampForLatestValueInternal();

	protected abstract int getDataSetCountInternal();

	protected abstract int getDataSetCountInternal(long start, long end) throws IOException;

	public abstract long getStoringPeriod();

	/**
	 * Closes and Flushes underlying Input- and OutputStreams. Requires the
	 * SlotsDbStorage write lock to be held.
	 *
	 * @throws IOException
	 */
	public void close() throws IOException {
		if (canWrite) {
			cache.invalidate();
			assert cache.getCache() == null : "Invalidated cache is still alive";
		}
		synchronized (dataFile) {
			if (channel != null) {
				channel.close();
				channel = null;
			}
		}
		canRead = false;
		canWrite = false;
	}

	/**
	 * Flushes the underlying Data Streams.
	 *
	 * @throws IOException
	 */
	public void flush() throws IOException {
		if (channel != null) {
			cache.invalidate();
			assert cache.getCache() == null : "Invalidated cache is still alive";
		}
	}

	/**
	 * Return the Timestamp of the first stored Value in this File.
	 */
	public long getStartTimeStamp() {
		return startTimeStamp;
	}

	public static FileObject getFileObject(String fileName, FendoInstanceCache cache) throws IOException {
		if (fileName.startsWith("c")) {
			return new ConstantIntervalFileObject(fileName, cache);
		} else if (fileName.startsWith("f")) {
			return new FlexibleIntervalFileObject(fileName, cache);
		} else {
			throw new IOException("Invalid filename for SlotsDB-File");
		}
	}

	public static FileObject getFileObject(File file, FendoInstanceCache cache) throws IOException {
		if (file.getName().startsWith("c")) {
			return new ConstantIntervalFileObject(file, cache);
		} else if (file.getName().startsWith("f")) {
			return new FlexibleIntervalFileObject(file, cache);
		} else {
			throw new IOException("Invalid file for SlotsDB-File. Invalid filename.");
		}
	}
	
	public static FileObject getFileObject(Path file, FendoInstanceCache cache) throws IOException {
		if (file.getFileName().toString().startsWith("c")) {
			return new ConstantIntervalFileObject(file, cache);
		} else if (file.getFileName().toString().startsWith("f")) {
			return new FlexibleIntervalFileObject(file, cache);
		} else {
			throw new IOException("Invalid file for SlotsDB-File. Invalid filename.");
		}
	}

	@Override
	public String toString() {
		return String.format("FileObject %s, read=%b, write=%b, start=%d", dataFile, canRead, canWrite, startTimeStamp);
	}
	
}
