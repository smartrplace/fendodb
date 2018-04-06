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
