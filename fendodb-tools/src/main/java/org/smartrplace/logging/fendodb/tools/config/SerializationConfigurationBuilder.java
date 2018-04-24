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
package org.smartrplace.logging.fendodb.tools.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class SerializationConfigurationBuilder {

	private boolean prettyPrint = true;
	private int indentation = 4;
	private char delimiter = ';';
	private long startTime = Long.MIN_VALUE;
	private long endTime = Long.MAX_VALUE;
	private DateTimeFormatter formatter = null;
	private ZoneId timeZone = ZoneId.of("Z"); // default: UTC
	private Long samplingInterval;
	private int maxNrValues = 10000000;
	private FendodbSerializationFormat format = FendodbSerializationFormat.CSV;

	private SerializationConfigurationBuilder() {}

	/**
	 * Retrieve a new builder instance, with default settings.
	 * @return
	 */
	public static SerializationConfigurationBuilder getInstance() {
		return new SerializationConfigurationBuilder();
	}

	/**
	 * Retrieve a new builder instance, by copying the settings from the passed configuration.
	 * @param configToCopy
	 * @return
	 */
	public static SerializationConfigurationBuilder getInstance(final SerializationConfiguration configToCopy) {
		final SerializationConfigurationBuilder builder = new SerializationConfigurationBuilder();
		if (configToCopy != null) {
			builder.setInterval(configToCopy.getStartTime(), configToCopy.getEndTime());
			builder.setFormatter(configToCopy.getFormatter(), configToCopy.getTimeZone());
			builder.setSamplingInterval(configToCopy.samplingInterval());
			builder.setFormat(configToCopy.getFormat());
			builder.setMaxNrValues(configToCopy.getMaxNrValues());
		}
		return builder;
	}

	/**
	 * Restrict the time interval to be serialized.
	 * @param startTime
	 * 		in ms since epoch (1970-01-01T00:00:00 UTC)
	 * @param endTime
	 * 		in ms since epoch (1970-01-01T00:00:00 UTC)
	 * @return
	 */
	public SerializationConfigurationBuilder setInterval(long startTime, long endTime) {
		if (startTime > endTime)
			throw new IllegalArgumentException("Start time greater than end time: " + startTime + ", " + endTime);
		this.startTime = startTime;
		this.endTime = endTime;
		return this;
	}

	/**
	 * Create the configuration.
	 * @return
	 */
	public SerializationConfiguration build() {
		return new SerializationConfigurationImpl(
				format,
				delimiter,
				startTime,
				endTime,
				formatter,
				timeZone,
				samplingInterval,
				maxNrValues,
				prettyPrint,
				indentation);
	}



	/**
	 * Define a formatter for printing the time stamps. Default is null, in which case the timestamps
	 * are printed as long values (milliseconds since epoch).
	 * @param formatter
	 * @return
	 */
	public SerializationConfigurationBuilder setFormatter(DateTimeFormatter formatter) {
		return setFormatter(formatter, null);
	}

	/**
	 * Define a formatter for printing the time stamps. Default is null, in which case the timestamps
	 * are printed as long values (milliseconds since epoch).
	 * @param formatter
	 * @param timeZone
	 * @return
	 */
	public SerializationConfigurationBuilder setFormatter(DateTimeFormatter formatter, ZoneId timeZone) {
		this.formatter = formatter;
		if (timeZone != null)
			this.timeZone = timeZone;
		return this;
	}

	/**
	 * Set the serialization format. Default is {@link FendodbSerializationFormat#CSV}.
	 * @param format
	 * @return
	 */
	public SerializationConfigurationBuilder setFormat(FendodbSerializationFormat format) {
		this.format = Objects.requireNonNull(format);
		return this;
	}

	/**
	 * Set the delimiter between records
	 * Default: ';'
	 * @param delimiter
	 * @return
	 */
	public SerializationConfigurationBuilder setDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}

	/**
	 * Set the maximum number of entries to be written. Defaults to ten million (10000000).
	 * @param maxNrValues
	 * @return
	 */
	public SerializationConfigurationBuilder setMaxNrValues(int maxNrValues) {
		this.maxNrValues = maxNrValues;
		return this;
	}

	/**
	 * Pretty print result? Default is true.
	 * Only relevant if serialization format is
	 * {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}.
	 * @param prettyPrint
	 * @return
	 */
	public SerializationConfigurationBuilder setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
		return this;
	}

	/**
	 * Set indentation. Also sets {@link #setPrettyPrint(boolean)} to true.
	 * Only relevant if serialization format is
	 * {@link FendodbSerializationFormat#XML}
	 * or {@link FendodbSerializationFormat#JSON}.
	 * @param indentation
	 * @return
	 */
	public SerializationConfigurationBuilder setIndentation(int indentation) {
		if (indentation < 0)
			throw new IllegalArgumentException("Indentation must be non-negative: " + indentation);
		this.indentation = indentation;
		this.prettyPrint = true;
		return this;
	}

	/**
	 * If null, values are written in raw form, if non-null they are downsampled to
	 * the respective interval (in ms). Default is null.
	 * @param samplingInterval
	 * @return
	 * @throws IllegalArgumentException if samplingInterval is non-positive
	 */
	public SerializationConfigurationBuilder setSamplingInterval(Long samplingInterval) {
		if (samplingInterval != null && samplingInterval <= 0)
			throw new IllegalArgumentException("Sampling interval must be positive, got: " + samplingInterval);
		this.samplingInterval = samplingInterval;
		return this;
	}

}
