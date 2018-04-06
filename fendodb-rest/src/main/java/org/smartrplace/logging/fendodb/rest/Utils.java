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
package org.smartrplace.logging.fendodb.rest;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import org.ogema.core.channelmanager.measurements.BooleanValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.LongValue;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.Value;
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
	
	private static final Object getValue(final Value value) {
		if (value instanceof BooleanValue)
			return value.getBooleanValue();
		if (value instanceof IntegerValue || value instanceof LongValue)
			return value.getLongValue();
		return value.getDoubleValue();
	}
	
	
}
