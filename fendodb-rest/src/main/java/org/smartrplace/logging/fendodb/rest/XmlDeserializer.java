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

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

class XmlDeserializer extends Deserializer {
	
	private final static char[] XML_ENTRY_TAG_START = {'<','e','n','t','r','y','>'};
	private final static char[] XML_ENTRY_TAG_END = {'<','/','e','n','t','r','y','>'};
	private final Map<String,String> map = new HashMap<>(8); // buffer for json entries

	
	XmlDeserializer(Reader reader, FendoTimeSeries timeSeries, HttpServletResponse resp) {
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
				if (c == '<') {
					idxStartPartial = i;
					break;
				}
			}
			if (idxStartPartial >= 0 && idxStartPartial < end) {
				int idxEndPartial = partial.length;
				for (int i=idxStartPartial; i< partial.length; i++) {
					if (partial[i] ==  DELIMITER) {
						idxEndPartial = i;
						break;
					}
				}
				final int length = idxEndPartial - idxStartPartial;
				final int bufferLength = Math.min(end, buffer.length);
				final char[] newBuffer = new char[length + bufferLength]; //
				System.arraycopy(partial, idxStartPartial, newBuffer, 0, length);
				System.arraycopy(buffer, 0, newBuffer, length, bufferLength);
				partial[0] = DELIMITER;
				return parseBuffer(newBuffer, 0, newBuffer.length);
			}
		}
		final int startIdx = indexOf(buffer, XML_ENTRY_TAG_START, start);
		final int endIdx = startIdx == -1 ? -1 : Math.min(end, indexOf(buffer, XML_ENTRY_TAG_END, startIdx + XML_ENTRY_TAG_START.length));
		if (endIdx == -1) {
			final int first = startIdx == -1 ? start : startIdx;
			System.arraycopy(buffer, first, partial, 0, buffer.length - first);
			partial[buffer.length-first] = DELIMITER;
			return true;
		} else {
			final Map<String,String> content = parseXmlEntryContent(buffer, startIdx + XML_ENTRY_TAG_START.length, endIdx);
			final SampledValue value = applyJson(content, resp);
			if (value == null)
				return false;
			values.add(value);
			return parseBuffer(buffer, endIdx + XML_ENTRY_TAG_END.length, end);
		}
	}
	
	// entry may only contain primitive tags (no nesting)
	private Map<String,String> parseXmlEntryContent(final char[] xml, final int start, final int end) {
		map.clear();
		for (int i = start; i < end-1; i++) {
			if (xml[i] != '<')
				continue;
			if (xml[i+1] == '/') 
				return null;
			i = addEntry(xml, i + 1, map); // parse substring and move forward
			if (i < 0)
				return null;
		}
		return map;
	}

	// search index of subarray TODO more efficient implementation, such as Boyer-Moore
	private static int indexOf(final char[] array, final char[] pattern, final int startIdx) {
		outer : for (int i=startIdx; i < array.length - pattern.length + 1; i++) {
			for (int j=0; j < pattern.length; j++) {
				if (array[i+j] != pattern[j])
					continue outer;
			}
			return i;
		}
		return -1;
	}
	
	// start: one plus < index
	private static int addEntry(final char[] xml, final int start, final Map<String,String> result) {
		int nextEnd = -1;
		int nextStart = -1;
		for (int i = start; i < xml.length - 1; i++) {
			if (xml[i] != '>') {
				if (xml[i] == '<')
					return -1;
				continue;
			}
			nextEnd = i; 
			break;
		}
		if (nextEnd == -1)
			return -1;
		final String tag0 = new String(xml, start, nextEnd - start);
		for (int i=nextEnd+1; i < xml.length-1; i++) {
			if (xml[i] != '<')
				continue;
			nextStart = i;
			final int tagLength = tag0.length();
			if (i + tagLength + 1 >= xml.length)
				return -1;
			if (xml[i+1] != '/')
				return -1;
			for (int j=0; j<tagLength;j++) {
				if (xml[i + j + 2] != tag0.charAt(j))
					return -1;
			}
			break;
		}
		final String value = new String(xml, nextEnd + 1, nextStart - nextEnd - 1).trim();
		result.put(tag0, value);
		return nextStart + tag0.length();
	}
	
}
