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

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

class JsonDeserializer extends Deserializer {
	
	JsonDeserializer(Reader reader,FendoTimeSeries timeSeries, HttpServletResponse resp) {
		super(reader, timeSeries, resp);
	}

	@Override
	boolean parseBuffer(final char[] buffer, final int start, final int end) throws IOException {
		if (start == 0) {
			int idxStartPartial = -1;
			for (int i=0;i<partial.length;i++) {
				final char c = partial[i];
				if (c == DELIMITER)
					break;
				if (c == '{') {
					idxStartPartial = i;
					break;
				}
			}
			if (idxStartPartial >= 0) {
				int idxEnd = -1;
				for (int j=0;j<Math.min(buffer.length,end);j++) {
					if (buffer[j] == '{') {
						resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid content");
						return false;
					}
					if (buffer[j] == '}') {
						idxEnd = j;
						break;
					}
				}			
				if (idxEnd == -1) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid content");
					return false;
				}
				final StringBuilder sb = new StringBuilder();
				if (idxStartPartial != partial.length) {
					int idxEndPartial = partial.length;
					for (int i=idxStartPartial;i<partial.length;i++) {
						if (partial[i] == DELIMITER) {
							idxEndPartial = i;
							break;
						}
					}
					sb.append(partial, idxStartPartial+1, idxEndPartial - idxStartPartial-1);
				}
				sb.append(buffer, 0, idxEnd);
				final Map<String,String> map = parseJsonEntryContent(sb.toString());
				if (map == null)
					return false;
				final SampledValue sv = applyJson(map, resp);
				values.add(sv);
				partial[0] = DELIMITER;
				return parseBuffer(buffer, idxEnd+1, end);
			}
		}
		int idxStart = -1;
		for (int i=start;i<Math.min(buffer.length, end);i++) {
			if (buffer[i] == '{') {
				idxStart = i;
				break;
			}
		}
		if (idxStart == -1) {
			return true;
		}
		int idxEnd = -1;
		for (int j=idxStart;j<Math.min(buffer.length, end);j++) {
			if (buffer[j] == '}') {
				idxEnd = j;
				break;
			}
		}
		if (idxEnd == -1) {
			System.arraycopy(buffer, idxStart, partial, 0, buffer.length - idxStart);
			partial[buffer.length-idxStart] = DELIMITER;
			return true;
		} else {
			final Map<String,String> content = parseJsonEntryContent(new String(buffer, idxStart + 1, idxEnd - idxStart - 1));
			final SampledValue value = applyJson(content, resp);
			if (value == null)
				return false;
			values.add(value);
			return parseBuffer(buffer, idxEnd + 1, end);
		}
		
	}
	
	static Map<String,String> parseSimpleJsonEntry(final String json, final HttpServletResponse resp) throws IOException {
		final int idxOpen = json.indexOf('{');
		final int idxClose = json.indexOf('}');
		if (idxOpen < 0 || idxClose < idxOpen) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid json");
			return null;
		}
		return parseJsonEntryContent(json.substring(idxOpen+1, idxClose));
	}
	
	private static Map<String,String> parseJsonEntryContent(final String json) throws IOException {
		final String[] entries = json.split(",");
		return Arrays.stream(entries)
			.map(entry -> entry.replace('\"', ' ').split(":"))
			.filter(entry -> entry.length == 2)
			.collect(Collectors.toMap(arr -> arr[0].trim().toLowerCase(), arr -> arr[1].trim()));
	}
	
}
