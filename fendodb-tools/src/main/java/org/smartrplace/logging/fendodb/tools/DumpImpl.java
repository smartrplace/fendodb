/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.tools;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfiguration;

class DumpImpl {

	static void dump(final CloseableDataRecorder instance, final Path path, final DumpConfiguration config) throws IOException {
		final Stream<FendoTimeSeries> timeSeriesStream = getTimeSeries(instance, config);
		try {
			if (config.doZip()) {
				try (final OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
					zip(timeSeriesStream, config, out);
				}
			} else {
				Files.createDirectories(path);
				timeSeriesStream.forEach(ts -> write(path, ts, config));
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	static void zip(final CloseableDataRecorder instance, final OutputStream out, final DumpConfiguration config) throws IOException {
		final Stream<FendoTimeSeries> timeSeriesStream = getTimeSeries(instance, config);
		try {
			zip(timeSeriesStream, config, out);
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static void zip(final Stream<FendoTimeSeries> stream, final DumpConfiguration config, final OutputStream out) throws IOException {
		final char delimiter = config.getDelimiter();
		final CSVFormat format = CSVFormat.newFormat(delimiter).withTrailingDelimiter(false).withRecordSeparator('\n');
		try (final ZipOutputStream zout = new ZipOutputStream(out, StandardCharsets.UTF_8);) {
 			stream.forEach(ts -> {
				try {
					final ZipEntry entry = new ZipEntry(URLEncoder.encode(ts.getPath() + ".csv", StandardCharsets.UTF_8.toString()));
					zout.putNextEntry(entry);
					final CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(zout, StandardCharsets.UTF_8), format);
					SerializerImpl.write(ts, config, printer);
					printer.flush();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	private final static Stream<FendoTimeSeries> getTimeSeries(final CloseableDataRecorder instance, final DumpConfiguration config) {
		Stream<FendoTimeSeries> timeSeriesStream = instance.getAllTimeSeries().stream();
		final Collection<String> excludedIds = config.getExcludedIds();
		if (excludedIds != null) {
			final boolean ignoreCase = config.isIgnoreCaseExcludes();
			final boolean isRegexp = config.isRegexpExcludes();
			timeSeriesStream = timeSeriesStream.filter(ts -> !matches(ts.getPath(), excludedIds, ignoreCase, isRegexp));
		}
		final Collection<String> includedIds = config.getIncludedIds();
		if (includedIds != null) {
			final boolean ignoreCase = config.isIgnoreCaseIncludes();
			final boolean isRegexp = config.isRegexpIncludes();
			timeSeriesStream = timeSeriesStream.filter(ts -> matches(ts.getPath(), includedIds, ignoreCase, isRegexp));
		}
		final TimeSeriesMatcher filter = config.getFilter();
		if (filter != null)
			timeSeriesStream = timeSeriesStream.filter(ts -> filter.matches(ts));
		return timeSeriesStream;
	}

//	private final static boolean matches(final Collection<String> tags, final Collection<String> set) {
//		return tags.stream().filter(tag -> set.contains(tag.toLowerCase())).findAny().isPresent();
//	}

	// TODO implement regexp matching
	private final static boolean matches(final String id, final Collection<String> set, final boolean ignoreCase, final boolean isRegexp) {
		Stream<String> stream = set.stream();
		if (ignoreCase)
			stream = stream.filter(ts -> id.equalsIgnoreCase(ts));
		else
			stream = stream.filter(ts -> id.equals(ts));
		return stream.findAny().isPresent();
	}

	private final static void write(final Path base, final RecordedDataStorage timeSeries, final DumpConfiguration config) {
		final String fileName;
		try {
			fileName = URLEncoder.encode(timeSeries.getPath(), StandardCharsets.UTF_8.toString()) + ".csv";
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		final Path target = base.resolve(fileName);
		final char delimiter = config.getDelimiter();
		// TODO implement JSON and XML serialization
		final CSVFormat format = CSVFormat.newFormat(delimiter).withTrailingDelimiter(false).withRecordSeparator('\n');
		System.out.println("Writing time series " + timeSeries.getPath());
		try (final BufferedWriter writer = Files.newBufferedWriter(target); final CSVPrinter printer = new CSVPrinter(writer, format);) {
			SerializerImpl.write(timeSeries, config, printer);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
