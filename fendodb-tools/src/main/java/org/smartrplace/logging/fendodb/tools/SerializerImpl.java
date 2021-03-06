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
import java.io.Writer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.ogema.core.channelmanager.measurements.BooleanValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.LongValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.timeseries.InterpolationMode;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.ogema.tools.timeseries.iterator.api.DataPoint;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIterator;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIteratorBuilder;
import org.ogema.tools.timeseries.iterator.api.SampledValueDataPoint;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

class SerializerImpl {
	
	private final static TemporalUnit[] units = { ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, 
			ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS };

	static int write(final ReadOnlyTimeSeries timeSeries, final SerializationConfiguration config, final Writer writer) throws IOException {
		Objects.requireNonNull(timeSeries);
		Objects.requireNonNull(config);
		Objects.requireNonNull(writer);
		final FendodbSerializationFormat format = config.getFormat();
		final int cnt;
		switch (format) {
		case JSON:
			cnt = write(timeSeries, config, writer, false);
			writer.flush();
			break;
		case XML: 
			cnt = write(timeSeries, config, writer, true);
			writer.flush();
			break;
		default:
			final char delimiter = config.getDelimiter();
			final CSVFormat csvFormat = CSVFormat.newFormat(delimiter).withTrailingDelimiter(false).withRecordSeparator('\n');
			try (final CSVPrinter printer = new CSVPrinter(writer, csvFormat);) {
				cnt = write(timeSeries, config, printer);
			}
		}
		return cnt;
	}
	
	static int write(final ReadOnlyTimeSeries timeSeries, final SerializationConfiguration config, final Writer writer, final boolean xmlOrJson) throws IOException {
		long start = config.getStartTime();
		final long end = config.getEndTime();
		final DateTimeFormatter formatter = config.getFormatter();
		final ZoneId timeZone = config.getTimeZone();
		final Long samplingInterval = config.samplingInterval();
		final Iterator<SampledValue> it0 = timeSeries.iterator(start, end);
		final Iterator<SampledValue> it;
		if (samplingInterval != null) {
			start = getAlignedIntervalStartTime(timeSeries, start, samplingInterval, timeZone);
			it = new WrappedIterator(MultiTimeSeriesIteratorBuilder.newBuilder(Collections.singletonList(it0))
					.setGlobalInterpolationMode(InterpolationMode.LINEAR)
					.setStepSize(start, samplingInterval)
					.build());
		} else {
			it = it0;
		}
		final boolean pretty = config.isPrettyPrint();
		final char[] separator;
		final char[] linebreak;
		if (!pretty) {
			separator = new char[0];
			linebreak = separator;
		} else {
			separator = new char[config.getIndentation()];
			Arrays.fill(separator, ' ');
			linebreak = new char[1];
			linebreak[0] = '\n';
		}
		final String path = (timeSeries instanceof Schedule) ? ((Schedule) timeSeries).getPath() :
			(timeSeries instanceof RecordedData) ? ((RecordedData) timeSeries).getPath() : 
			"n.a.";
		final long start0;
		final SampledValue first = timeSeries.getNextValue(start);
		if (first == null || first.getTimestamp() > end)
			start0 = start;
		else {
			start0 = first.getTimestamp();
		}
		final Object startTime = formatTimestamp(start0, formatter, timeZone);
		final long end0;
		final SampledValue last = timeSeries.getPreviousValue(end);
		if (last == null || last.getTimestamp() < start)
			end0 = end;
		else {
			end0 = last.getTimestamp();
		}
		final Object endTime = formatTimestamp(end0, formatter, timeZone);
		final Long intv0 = config.samplingInterval();
		long intv = 0;
		if (intv0 != null)
			intv = intv0;
		else if (timeSeries instanceof RecordedData) {
			intv = ((RecordedData) timeSeries).getConfiguration().getFixedInterval();
		}
		printHeader(writer, path, startTime, endTime, intv, xmlOrJson, separator, linebreak);
		final int maxValue = config.getMaxNrValues();
		if (maxValue <= 0) {
			printEnd(writer, xmlOrJson, separator, linebreak);
			return 0;
		}
		if (it.hasNext()) {
			final StringBuilder sb = serialize(it.next(), formatter, timeZone, xmlOrJson, separator, linebreak);
			final int length = sb.length();
			final char[] buf = new char[length];
			sb.getChars(0, length, buf, 0);
			writer.write(buf);
		}
		int cnt = 1;
		while (it.hasNext()) {
			if (cnt++ >= maxValue) {
				cnt--;
				break;
			}
			if (!xmlOrJson)
				writer.write(',');
			final StringBuilder sb = serialize(it.next(), formatter, timeZone, xmlOrJson, separator, linebreak);
			final int length = sb.length();
			final char[] buf = new char[length];
			sb.getChars(0, length, buf, 0);
			writer.write(buf);
		}
		printEnd(writer, xmlOrJson, separator, linebreak);
		return cnt;
	}
	
	static int write(final List<FendoTimeSeries> timeSeries, final SerializationConfiguration config, final CSVPrinter printer) throws IOException {
		if (timeSeries == null || timeSeries.isEmpty())
			return 0;
		final int size = timeSeries.size();
		final Object[] record = new Object[size + 1];
		record[0] = "ID";
		final AtomicInteger idx = new AtomicInteger(1);
		timeSeries.stream()
			.map(FendoTimeSeries::getPath)
			.forEach(ts -> record[idx.getAndIncrement()] = ts);
		printer.printRecord(record);
		record[0] = "Tags";
		IntStream.range(0, size).forEach(i -> record[i+1] = "--");
		printer.printRecord(record);
		final List<String> tags = timeSeries.stream()
			.flatMap(ts -> ts.getProperties().keySet().stream())
			.distinct()
			.collect(Collectors.toList());
		for (String tag : tags) {
			record[0] = tag;
			idx.set(1);
			for (FendoTimeSeries ts : timeSeries) {
				final List<String> properties = ts.getProperties(tag);
				final String values = properties == null || properties.isEmpty() ? "" 
						: properties.stream().collect(Collectors.joining(","));
				record[idx.getAndIncrement()] = values; 
			}
			printer.printRecord(record);
		}
		record[0] = "Timestamp";
		IntStream.range(0, size).forEach(i -> record[i+1] = "--");
		printer.printRecord(record);
		
		final long start0 = config.getStartTime();
		final long end = config.getEndTime();
		final DateTimeFormatter formatter = config.getFormatter();
		final ZoneId timeZone = config.getTimeZone();
		final Long samplingInterval = config.samplingInterval();
		if (samplingInterval == null || samplingInterval <= 0)
			throw new IllegalArgumentException("Illegal sampling interval " + samplingInterval + "; this should not have happened...");
		final long start = getAlignedIntervalStartTime(null, start0, samplingInterval, timeZone);
		final List<Iterator<SampledValue>> iterators = timeSeries.stream()
					.map(ts -> ts.iterator(start, end))
					.collect(Collectors.toList());
		final MultiTimeSeriesIterator multiIt = MultiTimeSeriesIteratorBuilder.newBuilder(iterators)
				.setGlobalInterpolationMode(InterpolationMode.LINEAR)
				.setStepSize(start, samplingInterval)
				.build();
		final int maxNr = config.getMaxNrValues();
		int cnt = 0;
		while (multiIt.hasNext()) {
			if (cnt++ >= maxNr) {
				cnt--;
				break;
			}
			final SampledValueDataPoint data = multiIt.next();
			final long t = data.getTimestamp();
			final Object time = formatter == null ? t : formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), timeZone));
			record[0] = time;
			for (int i=1; i <= size; i++) {
				record[i] = getValue(data.getElement(i-1));
			}
			printer.printRecord(record);
		}
		return cnt;
	}
	
	private static final Object getValue(final SampledValue sv) {
		if (sv == null || sv.getQuality() == Quality.BAD)
			return "";
		final float value = sv.getValue().getFloatValue();
		return Float.isFinite(value) ? value : "";
	}
	
	private static void printHeader(final Writer writer, final String path, final Object startTime, final Object endTime, final long interval,
			final boolean xmlOrJson, final char[] separator, final char[] linebreak) throws IOException {
		if (xmlOrJson) {
			// format
//			<recordedData xmlns:og="http://www.ogema-source.net/REST" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
//			<resource>
//			HomeMatic/devices/HM_HM_WDS10_TH_O_NEQ1382594/WEATHERNEQ1382594_1/sensors/TEMPERATURE/reading
//			</resource>
//			<interpolationMode>NONE</interpolationMode>
//			<startTime>0</startTime>
//			<endTime>9223372036854775807</endTime>
//			<interval>0</interval>
//			<reductionMode>NONE</reductionMode>
			writer.write("<recordedData xmlns:og=\"http://www.ogema-source.net/REST\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<resource>");
			writer.write(linebreak);
			writer.write(separator);
			writer.write(separator);
			writer.write(path);
			writer.write(linebreak);
			writer.write(separator);
			writer.write("</resource>");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<interpolationMode>NONE</interpolationMode>");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<reductionMode>NONE</reductionMode>"); // FIXME depends on passed config!
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<startTime>");
			writer.write(startTime.toString());
			writer.write("</startTime>");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<endTime>");
			writer.write(endTime.toString());
			writer.write("</endTime>");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("<interval>");
			writer.write(interval + "");
			writer.write("</interval>");
		} else {
			// format
//			{
//			  "@type" : "FloatSchedule",
//			  "entry" : [ 
			writer.write('{');
			writer.write(linebreak);
			writer.write(separator);
			writer.write("\"@type\":\"FloatSchedule\",");
			writer.write(linebreak);
			writer.write(separator);
			writer.write("\"entries\":[");
		}
	}
	
	private static void printEnd(final Writer writer, final boolean xmlOrJson,
			final char[] separator, final char[] linebreak) throws IOException {
		if (xmlOrJson) {
			writer.write(linebreak);
			writer.write("</recordeddata>");
		} else {
			writer.write(linebreak);
			writer.write(separator);
			writer.write(']');
			writer.write(linebreak);
			writer.write('}');
		}
	}
	
	private static Object formatTimestamp(final long t, final DateTimeFormatter formatter, final ZoneId timeZone) {
		return formatter == null ? t : formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), timeZone));
	}
	
	private static final StringBuilder serialize(final SampledValue sv, final DateTimeFormatter formatter, 
			final ZoneId timeZone, final boolean xmlOrJson, final char[] separator, final char[] linebreak) {
		final Object time = formatTimestamp(sv.getTimestamp(), formatter, timeZone);
		final Value val = sv.getValue();
		final StringBuilder sb = new StringBuilder();
		final String type = val.getClass().getSimpleName().replace("Value", "");
		if (xmlOrJson) {
			// format
		    //<entry xsi:type="og:SampledFloat">
		    //    <time>1516230000000</time>
		    //    <quality>GOOD</quality>
		    //    <value>6.0</value>
		    //</entry>
			sb.append(linebreak).append(separator);
			sb.append("<entry xsi:type=\"").append("Sampled").append(type).append("\">")
				.append(linebreak).append(separator).append(separator)
					.append("<time>").append(time).append("</time>")
				.append(linebreak).append(separator).append(separator)
				.append("<quality>").append(sv.getQuality().toString()).append("</quality>")
				.append(linebreak).append(separator).append(separator)				
				.append("<value>")
				.append(val instanceof BooleanValue ? val.getBooleanValue() :
						val instanceof IntegerValue || val instanceof LongValue ? val.getLongValue() :
						val.getFloatValue())
				.append("</value>")
				.append(linebreak).append(separator).append("</entry>");
		} else {
			// format
			//	{
			//   "@type" : "SampledFloat",
			//    "time" : 1516230900000,
			//    "quality" : "GOOD",
			//    "value" : 6.0
			//  }
			sb.append(linebreak).append(separator)
				.append('{')
				.append(linebreak).append(separator).append(separator)
				.append("\"@type\":\"Sampled").append(type).append('\"').append(',')
				.append(linebreak).append(separator).append(separator)					
				.append("\"time\":");
			final boolean isString = time instanceof String;
			if (isString)
				sb.append('\"');
			sb.append(time);
			if (isString)
				sb.append('\"');
			sb.append(',')
				.append(linebreak).append(separator).append(separator)
				.append("\"quality\":\"").append(sv.getQuality().toString()).append('\"').append(',')
				.append(linebreak).append(separator).append(separator)				
				.append("\"value\":").append(val instanceof BooleanValue ? val.getBooleanValue() :
						val instanceof IntegerValue || val instanceof LongValue ? val.getLongValue() :
						val.getFloatValue())
				.append(linebreak).append(separator);
			sb.append('}');
			
		}
		return sb;
	}
	
	static int write(final ReadOnlyTimeSeries timeSeries, final SerializationConfiguration config, final CSVPrinter printer) throws IOException {
		long start = config.getStartTime();
		final long end = config.getEndTime();
		final DateTimeFormatter formatter = config.getFormatter();
		final ZoneId timeZone = config.getTimeZone();
		final Long samplingInterval = config.samplingInterval();
		final Iterator<SampledValue> it0 = timeSeries.iterator(start, end);
		final Iterator<SampledValue> it;
		if (samplingInterval != null) {
			start = getAlignedIntervalStartTime(timeSeries, start, samplingInterval, timeZone);
			it = new WrappedIterator(MultiTimeSeriesIteratorBuilder.newBuilder(Collections.singletonList(it0))
					.setGlobalInterpolationMode(InterpolationMode.LINEAR)
					.setStepSize(start, samplingInterval)
					.build());
		} else {
			it = it0;
		}
		final int maxNr = config.getMaxNrValues();
		int cnt = 0;
		while (it.hasNext()) {
			if (cnt++ >= maxNr) {
				cnt--;
				break;
			}
			final SampledValue sv = it.next();
			final Object time = formatter == null ? sv.getTimestamp() : formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(sv.getTimestamp()), timeZone));
			printer.printRecord(time, sv.getValue().getFloatValue());
		}
		return cnt;
	}
	
	private final static long getAlignedIntervalStartTime(ReadOnlyTimeSeries timeSeries, long startTime, long samplingInterval, final ZoneId timeZone) {
		final Instant t0;
		if (timeSeries != null) {
			final SampledValue sv = timeSeries.getNextValue(startTime);
			if (sv == null)
				return startTime;
			t0 = Instant.ofEpochMilli(sv.getTimestamp());
		} else {
			t0 = Instant.ofEpochMilli(startTime);
		}
		for (TemporalUnit unit :units) {
			if (samplingInterval == unit.getDuration().toMillis()) {
				if (!unit.isDateBased())
					return t0.truncatedTo(unit).toEpochMilli();
				else {
					final ZonedDateTime day = ZonedDateTime.ofInstant(t0, timeZone).truncatedTo(ChronoUnit.DAYS);
					final ZonedDateTime truncated;
					if (unit == ChronoUnit.WEEKS) 
						truncated = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
					else if (unit == ChronoUnit.MONTHS) 
						truncated = day.with(TemporalAdjusters.firstDayOfMonth());
					else
						truncated = day.with(TemporalAdjusters.firstDayOfYear());
					return truncated.toInstant().toEpochMilli();
				}
			}
		}
		return startTime;
	}
	
	private final static class WrappedIterator implements Iterator<SampledValue> {
		
		private final Iterator<DataPoint<SampledValue>> base;

		WrappedIterator(Iterator<DataPoint<SampledValue>> base) {
			this.base = base;
		}

		@Override
		public boolean hasNext() {
			return base.hasNext();
		}

		@Override
		public SampledValue next() {
			return base.next().getElements().get(0);
		}
		
	}
	
	
}
