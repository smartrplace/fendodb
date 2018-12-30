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
package org.smartrplace.logging.fendodb.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfiguration;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfigurationBuilder;

/**
 * Utility methods for the SlotsDb database, in particular for serialization and to create CSV dumps. 
 */
public class FendoDbTools {
	
	/**
	 * Serialize a time series. Does not close the writer.
	 * @param writer
	 * @param timeSeries
	 * @param config
	 * @return number of serialized data points
	 * @throws IOException
	 */
	public static int serialize(final Writer writer, final ReadOnlyTimeSeries timeSeries, final SerializationConfiguration config) throws IOException {
		return SerializerImpl.write(timeSeries, config, writer);
	}
	
	/**
	 * Serialize a time series.
	 * @param timeSeries
	 * @param config
	 * @return
	 * @throws IOException
	 */
	public static String serialize(final ReadOnlyTimeSeries timeSeries, final SerializationConfiguration config) throws IOException {
		final StringWriter writer = new StringWriter();
		serialize(writer, timeSeries, config);
		return writer.toString();
	}

	/**
	 * Create a database dump of the provided SlotsDb instance.
	 * @param instance
	 * @param path
	 * 		the target path, where the dump shall be created. Must be either an empty directory, or a non-existing path.
	 * @param configuration
	 * @throws IOException 
	 * @throws IllegalStateException
	 * 		if the passed path exists and is not an empty directory
	 * @throws NullPointerException 
	 * 		if instance or out are null
	 * @throws SecurityException
	 *  	if Java security is active, and the caller does not have the appropriate {@link FendoDbPermission#ADMIN admin permission}.
	 */
	public static void dump(final CloseableDataRecorder instance, final Path path, DumpConfiguration configuration) throws IOException {
		Objects.requireNonNull(instance);
		Objects.requireNonNull(path);
		if (Files.exists(path)) {
			if (!configuration.doZip()) {
				if (!Files.isDirectory(path))
					throw new IllegalStateException("File " + path + " exists and is not a directory");
				if (!isEmpty(path))
					throw new IllegalStateException("The directory " + path + " is non-empty");
			} else {
				throw new IllegalStateException("File " + path + " already exists");
			}
		}
		if (configuration == null)
			configuration = DumpConfigurationBuilder.getInstance()
				.setMaxNrValues(Integer.MAX_VALUE)
				.build(); // default config
		final Lock lock = instance.getDbLock();
		lock.lock();
		try {
			DumpImpl.dump(instance, path, configuration);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Create a database dump of the provided SlotsDb instance.
	 * @param instance
	 * @param output
	 * @param configuration
	 * @throws IOException 
	 * @throws IllegalStateException
	 * 		if the passed path exists and is not an empty directory
	 * @throws NullPointerException 
	 * 		if instance or out are null
	 * @throws SecurityException
	 *  	if Java security is active, and the caller does not have the appropriate {@link FendoDbPermission#ADMIN admin permission}.
	 */
	public static void dump(final CloseableDataRecorder instance, final OutputStream output, DumpConfiguration configuration) throws IOException {
		Objects.requireNonNull(instance);
		Objects.requireNonNull(output);
		if (configuration == null)
			configuration = DumpConfigurationBuilder.getInstance()
				.setMaxNrValues(Integer.MAX_VALUE)
				.build(); // default config
		final Lock lock = instance.getDbLock();
		lock.lock();
		try {
			DumpImpl.dump(instance, output, configuration);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Create a CSV dump of the database, zip it, and write it to the output stream.
	 * If the output stream corresponds to a file, then this is equivalent to 
	 * {@link #dump(CloseableDataRecorder, Path, DumpConfiguration)}
	 * with {@link DumpConfiguration#doZip()} = true.
	 * @param instance
	 * @param out
	 * 		the output stream to which the zip file will be written
	 * @param configuration
	 * @throws IOException 
	 * @throws NullPointerException 
	 * 		if instance or out are null
	 * @throws SecurityException
	 *  	if Java security is active, and the caller does not have the appropriate {@link FendoDbPermission#ADMIN admin permission}.
	 */
	public static void zippedDump(final CloseableDataRecorder instance, final OutputStream out, DumpConfiguration configuration) throws IOException {
		Objects.requireNonNull(instance);
		Objects.requireNonNull(out);
		if (configuration == null)
			configuration = DumpConfigurationBuilder.getInstance()
				.setMaxNrValues(Integer.MAX_VALUE)
				.setDoZip(true)
				.build(); // default config
		final Lock lock = instance.getDbLock();
		lock.lock();
		try {
			DumpImpl.zip(instance, out, configuration);
		} finally {
			lock.unlock();
		}
	}
	
	private static boolean isEmpty(final Path dir) throws IOException {
		try (final Stream<Path> stream = Files.list(dir)) {
			return !stream.findAny().isPresent();
		}
	}
	
	
}
