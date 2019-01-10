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
package org.smartrplace.logging.fendodb.rest;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.function.Function;

import org.ogema.core.channelmanager.measurements.BooleanValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.LongValue;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.osgi.service.component.ComponentServiceObjects;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

class Utils {
	
	private final static DateTimeFormatter formatter = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd")
			.optionalStart()
				.appendPattern("'T'HH")
				.optionalStart()
					.appendPattern(":mm")
					.optionalStart()
						.appendPattern(":ss")
					.optionalEnd()
				.optionalEnd()
			.optionalEnd()
			.toFormatter(Locale.ENGLISH);
	
	private final static ZoneId zone = ZoneId.of("Z");
	private final static long HOUR = 3600000;

	private static ZonedDateTime toZdt(long t) {
		return ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone);
	}
	
	private static ChronoUnit getAlignmentUnit(final long duration) {
		if (duration < 60000) // less than a minute
			return null;
		if (duration <= HOUR) {
			if (duration % 60000 == 0 && HOUR % duration == 0)
				return ChronoUnit.HOURS;
			return null;
		}
		if (duration <= 24 * HOUR) {
			if (duration % HOUR == 0 && (24 * HOUR) % duration == 0)
				return ChronoUnit.DAYS;
			return null;
		}
		if (duration == 7 * 24 * 60 * 60 * 1000)
			return ChronoUnit.WEEKS;
		if (duration == 30 * 24 * 60 * 60 * 1000)
			return ChronoUnit.MONTHS;
		if (duration == 365 * 24 * 60 * 60 * 1000)
			return ChronoUnit.YEARS;
		return null;
	}
	
	public static long getLastAlignedTimestamp(final long startTime, final long duration) {
		final ChronoUnit unit = getAlignmentUnit(duration);
		// FIXME
		System.out.println("   alignment unit " + unit + ": " + duration);
		if (unit == null)
			return startTime;
		final ZonedDateTime zdt = toZdt(startTime);
		ZonedDateTime truncated = zdt.truncatedTo(unit.isTimeBased() ? unit : ChronoUnit.DAYS);
		if (unit.isDateBased() && !unit.equals(ChronoUnit.DAYS)) {
			truncated = unit == ChronoUnit.WEEKS ? zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) :
						unit == ChronoUnit.MONTHS ? zdt.with(TemporalAdjusters.firstDayOfMonth()) : 
						zdt.with(TemporalAdjusters.firstDayOfYear());
		}
		if ((unit.isTimeBased() || unit.equals(ChronoUnit.DAYS)) && duration < unit.getDuration().toMillis()) {
			final ChronoUnit secondaryUnit = unit == ChronoUnit.HOURS ? ChronoUnit.MINUTES :
				unit == ChronoUnit.DAYS ? ChronoUnit.HOURS : null;
			if (secondaryUnit != null) {
				final long factor = duration / secondaryUnit.getDuration().toMillis();
				ZonedDateTime next = truncated;
				while (next.compareTo(zdt) < 0) {
					truncated = next;
					next = next.plus(factor, secondaryUnit);
				}
			}
		}
		// FIXME
		System.out.println("    truncated instant " + truncated);
		System.out.println("    original: " + zdt);
		return truncated.toInstant().toEpochMilli();
	}
	
	public final static Long parseTimeString(final String time, final Long defaulValue) {
		if (time == null || time.isEmpty())
			return defaulValue;
		try {
			return Long.parseLong(time);
		} catch (NumberFormatException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.from(formatter.parse(time)), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.of(LocalDate.from(formatter.parse(time)), LocalTime.MIN), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		return defaulValue;
	}
	
	static String serializeValue(final SampledValue sv, final FendodbSerializationFormat format, final DateTimeFormatter formatter,
			final char[] lineBreak, final char[] indentation) {
		if (sv == null)
			return "null";
		final String time = formatter == null ? sv.getTimestamp() + "" : formatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(sv.getTimestamp()), zone));
		final Value value = sv.getValue();
		final StringBuilder sb = new StringBuilder();
		switch (format) {
		case CSV:
			sb.append("time:").append(time).append(lineBreak);
			sb.append("value:").append(getValue(value)).append(lineBreak);
			sb.append("quality:").append(sv.getQuality());
			break;
		case JSON:
			sb.append('{').append(lineBreak).append(indentation)
				.append("\"@type\":\"SampledFloat\",")
				.append(lineBreak).append(indentation)
				.append("\"time\":");
			if (formatter != null)
				sb.append('\"');
			sb.append(time);
			if (formatter != null)
				sb.append('\"');
			sb.append(',').append(lineBreak).append(indentation);
			sb.append("\"value\":").append(getValue(value)).append(',').append(lineBreak).append(indentation);
			sb.append("\"quality\":\"").append(sv.getQuality()).append('\"').append(lineBreak)
				.append('}');
			break;
		case XML:
			/*
			 *  <entry xsi:type="SampledFloat">
			        <time>1516754775808</time>
			        <quality>GOOD</quality>
			        <value>0.0</value>
			    </entry>
			 */
			sb.append("<entry xsi:type=\"").append(value.getClass().getSimpleName()).append('\"').append('>');
			sb.append(lineBreak).append(indentation);
			sb.append("<time>").append(time).append("</time>");
			sb.append(lineBreak).append(indentation);
			sb.append("<value>").append(getValue(value)).append("</value>");
			sb.append(lineBreak).append(indentation);
			sb.append("<quality>").append(sv.getQuality()).append("</quality>");	
			sb.append(lineBreak);
			sb.append("</entry>");
		}
		
		return sb.toString();
	}

	static <S,T> T useService(final ComponentServiceObjects<S> service, final Function<S,T> operation) {
		final S instance= service.getService();
		try {
			return operation.apply(instance);
		} finally {
			service.ungetService(instance);
		}
	}
	
	private static final Object getValue(final Value value) {
		if (value instanceof BooleanValue)
			return value.getBooleanValue();
		if (value instanceof IntegerValue || value instanceof LongValue)
			return value.getLongValue();
		return value.getDoubleValue();
	}

	
}
