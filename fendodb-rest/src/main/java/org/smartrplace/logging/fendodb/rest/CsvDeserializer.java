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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

class CsvDeserializer extends Deserializer {
	
	private final BufferedReader reader;
	private final static int BUFFER_SIZE = 20;
	private final SampledValue[] buffer = new SampledValue[BUFFER_SIZE];
	private int nextBufferIdx = 0;

	CsvDeserializer(Reader reader, FendoTimeSeries timeSeries, HttpServletResponse resp) {
		super(reader, timeSeries, resp);
		this.reader = new BufferedReader(reader);
		
	}

	@Override
	boolean deserializeValues() throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#') // comment
				continue;
			final SampledValue sv = deserializeLine(line);
			if (sv == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid content: " + line);
				return false;
			}
			if (latest != null && sv.getTimestamp() <= latest) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid time ordering");
				return false;
			}
			latest = sv.getTimestamp();
			buffer[nextBufferIdx++] = sv;
			if (nextBufferIdx >= BUFFER_SIZE) {
				try {
					writeBuffer();
				} catch (DataRecorderException e) {
					resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Writing values failed: " + e.getMessage());
					return false;
				}
			}
		}
		if (nextBufferIdx != 0) {
			try {
				writeBuffer();
			} catch (DataRecorderException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Writing values failed: " + e.getMessage());
				return false;
			}
		}
		return true;
	}
	
	private final void writeBuffer() throws DataRecorderException {
		final int lastEntry = nextBufferIdx;
		nextBufferIdx = 0;
		List<SampledValue> list = Arrays.asList(buffer);
		if (lastEntry != BUFFER_SIZE)
			list = list.subList(0, lastEntry);
		timeSeries.insertValues(list);
	}
	
	private static SampledValue deserializeLine(final String line) {
		final String[] split = line.split(";");
		if (split.length != 2 && split.length != 3)
			return null;
		final Long time = Utils.parseTimeString(split[0].trim(), null);
		if (time == null)
			return null;
		final float value;
		try {
			value = Float.parseFloat(split[1].trim());
		} catch (NumberFormatException e) {
			return null;
		}
		final Quality q;
		if (split.length == 2) 
			q = Quality.GOOD;
		else {
			try {
				q = Quality.valueOf(split[2].trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return new SampledValue(new FloatValue(value), time, q);
	}
	
	@Override
	boolean parseBuffer(char[] buffer, int start, int end) throws IOException {
		throw new UnsupportedOperationException();
	}
	
}
