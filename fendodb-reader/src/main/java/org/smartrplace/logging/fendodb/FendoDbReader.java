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
package org.smartrplace.logging.fendodb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.smartrplace.logging.fendodb.impl.SlotsDbFactoryImpl;
import org.smartrplace.logging.fendodb.tools.FendoDbTools;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;
import org.smartrplace.logging.fendodb.tools.dump.DumpConfigurationBuilder;

public class FendoDbReader {

	private final static DateTimeFormatter formatter = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd")
			.optionalStart()
				.appendPattern("'T'HH")
				.optionalStart()
					.appendPattern(":mm")
					.optionalStart()
						.appendPattern(":ss")
						.optionalStart()
							.appendPattern(":SSS")
						.optionalEnd()
					.optionalEnd()
				.optionalEnd()
			.optionalEnd()
		.toFormatter(Locale.ENGLISH);
	private final static ZoneId zone = ZoneId.of("Z");

	public static void main(String[] args) throws IOException {
		checkArguments(args);
		final FendoDbFactory factory = new SlotsDbFactoryImpl();
		((SlotsDbFactoryImpl) factory).activate(null);
		try {
			copy(factory, args);
		} finally {
			((SlotsDbFactoryImpl) factory).deactivate();
		}
		System.out.println("Done.");
		System.exit(1);
	}

	private static void copy(FendoDbFactory factory, String[] args) throws IOException {
		final DumpConfigurationBuilder builder = DumpConfigurationBuilder.getInstance();
		long period = -1;
		long start = Long.MIN_VALUE;
		long end = Long.MAX_VALUE;

		boolean copy = false;
		boolean writable = false;

		for (int i = 1; i< args.length; i++) {
			final String a = args[i];
			if (a.equals("-i") && i < args.length-1) {
				try {
					period = Long.parseLong(args[i+1]);
				} catch (NumberFormatException e) {
					System.err.println("Argument " + args[i+1] + " is not a long value.");
					System.exit(1);
					return;
				}
			}
			else if (a.equals("-s") && i < args.length-1) {
				try {
					start = Long.parseLong(args[i+1]);
				} catch (NumberFormatException e) {
					try {
						start = parseTimeString(args[i+1]);
					} catch (NullPointerException | IllegalArgumentException ee) {
						System.err.println("Argument " + args[i+1] + " is neither a long value, nor a date string "
								+ " in the format yyyy-MM-dd'T'HH:mm:ss:SSS");
						System.exit(1);
						return;
					}
				}
 			}
			else if (a.equals("-e") && i < args.length-1) {
				try {
					end = Long.parseLong(args[i+1]);
				} catch (NumberFormatException e) {
					try {
						end = parseTimeString(args[i+1]);
					} catch (NullPointerException | IllegalArgumentException  ee) {
						System.err.println("Argument " + args[i+1] + " is neither a long value, nor a date string "
								+ " in the format yyyy-MM-dd'T'HH:mm:ss:SSS");
						System.exit(1);
						return;
					}
				}
 			}
			else if (a.equals("-z") || a.equals("--zip")) {
				builder.setDoZip(true);
			}
			else if (a.equals("-f") || a.equals("--format") && i < args.length-1) {
				try {
					final FendodbSerializationFormat format = FendodbSerializationFormat.valueOf(args[i+1].toUpperCase());
					builder.setFormat(format);
				} catch (IllegalArgumentException e) {
					error("Not a valid format: " + args[i+1] + "; must be one of " +
							(Arrays.stream(FendodbSerializationFormat.values()).map(val -> val.toString()).collect(Collectors.joining(", "))));
				}
			}
			else if (a.equals("-c") || a.equals("--copy")) {
				copy = true;
			}
			else if (a.equals("-w") || a.equals("--writable")) {
				writable = true;
			}
		}
		builder.setInterval(start, end);
		if (period > 0)
			builder.setSamplingInterval(period);
		try (final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(args[0]))) {
			if (copy) {
				final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance(instance.getConfiguration())
						.setReadOnlyMode(!writable)
						.build();
				instance.copy(Paths.get(args[1]), config, start, end);
			} else {
				FendoDbTools.dump(instance, Paths.get(args[1]), builder.build());
			}
		}
	}

	private static void checkArguments(final String[] args) {
		if (args.length < 2)
			error("Filenames required");
		final Path path0 = Paths.get(args[0]);
		if (!Files.exists(path0))
			error("Directory " + args[0] + " does not exist");
		if (!Files.isDirectory(path0))
			error("File " + args[0] + " is not a directory");
		final Path path1 = Paths.get(args[1]);
		if (Files.exists(path1)) {
			if (!Files.isDirectory(path1))
				error("Target directory " + args[1] + " exists but is not a directory");
			try {
				if (Files.list(path1).findAny().isPresent())
					error("Target directory " + args[1] + " is not empty");
			} catch (IOException | SecurityException e) {
				error ("Unexpected exception: " + e);
			}
		}
	}

	private static void error(String msg) {
		System.err.println(msg);
		printUsage();
		System.exit(1);
	}

	private static void printUsage() {
		System.out.println("usage: java -jar FendoDbReader.jar <filename original db> <filename db dump> (-s <start time>) (-e <end time>) (-i <Interval in ms>)");
	}

	private static long parseTimeString(final String time) {
		try {
			return Long.parseLong(time);
		} catch (NumberFormatException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.from(formatter.parse(time)), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.of(LocalDate.from(formatter.parse(time)), LocalTime.MIN), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		throw new IllegalArgumentException("Invalid time format " + time);
	}

}
