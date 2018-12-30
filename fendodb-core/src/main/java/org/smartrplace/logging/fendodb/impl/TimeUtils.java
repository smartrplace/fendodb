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
package org.smartrplace.logging.fendodb.impl;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;

class TimeUtils {

	// always work in utc time... simplifies things!
	final static ZoneId zone = ZoneId.of("Z");
	final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

	static final long getCurrentStart(final long timestamp, final TemporalUnit unit) {
		try {
			return getCurrentStart(Instant.ofEpochMilli(timestamp), unit).toEpochMilli();
		} catch (ArithmeticException e) {
			return getCurrentStart(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone).plus(1, unit).toInstant(), unit)
					.toEpochMilli();
		}
	}

	static final Instant getCurrentStart(final Instant instant0, final TemporalUnit unit) {
		if (unit.isDateBased()) {
			final ZonedDateTime zdt = ZonedDateTime.ofInstant(instant0, zone).truncatedTo(ChronoUnit.DAYS);
			if (unit.equals(ChronoUnit.DAYS))
				return zdt.toInstant();
			if (unit.equals(ChronoUnit.WEEKS))
				return zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toInstant();
			if (unit.equals(ChronoUnit.YEARS))
				return zdt.with(TemporalAdjusters.firstDayOfYear()).toInstant();
			if (unit.equals(ChronoUnit.MONTHS))
				return zdt.with(TemporalAdjusters.firstDayOfMonth()).toInstant();
			else
				throw new IllegalArgumentException("Invalid temporal unit " + unit);
		}
		else
			return instant0.truncatedTo(unit);
	}

	/**
	 * @param folderName
	 * @return
	 * @throws DateTimeParseException
	 */
	final static long parseCompatibilityFolderName(final String folderName) {
		return ZonedDateTime.of(LocalDate.from(formatter.parse(folderName)), LocalTime.MIN, zone).toInstant().toEpochMilli();
	}

	/**
	 * @param t
	 * @return
	 * @throws DateTimeException
	 */
	final static String formatCompatibilityFolderName(final long t) {
		return formatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), TimeUtils.zone));
	}

}
