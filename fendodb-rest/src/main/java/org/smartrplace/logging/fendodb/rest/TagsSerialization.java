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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

class TagsSerialization {
	
	/**
	 * @param timeSeries
	 * @param resp
	 * @param format
	 * 		0: csv,
	 * 		1: xml,
	 * 		2: json
	 * @param indent
	 * @throws IOException 
	 */
	static void serializeTags(final Collection<FendoTimeSeries> timeSeries, final Writer writer, 
			final FendodbSerializationFormat format0, final char[] indentation, final char[] lineBreak) throws IOException {
		final int format = format0 == FendodbSerializationFormat.CSV ? 0 :
				format0 == FendodbSerializationFormat.XML ? 1 : 2;
		switch (format) {
		case 1:
			writer.write("<timeSeries>");
			break;
		case 2:
			writer.write('{');
			nextLine(writer, lineBreak, indentation, 1);
			writer.write("\"entries\":[");
			break;
		default:
		}
		final AtomicBoolean first = new AtomicBoolean(true);
		timeSeries.forEach(ts -> serializeTags(ts, writer, format, indentation, lineBreak, first));
		switch (format) {
		case 1:
			writer.write(lineBreak);
			writer.write("</timeSeries>");
			break;
		case 2:
			nextLine(writer, lineBreak, indentation, 1);
			writer.write(']');
			writer.write(lineBreak);
			writer.write('}');
			break;
		default:
		}
	}
	
	static void serializeTags(final FendoTimeSeries timeSeries, final Writer writer, final int format, 
			final char[] indentation, final char[] lineBreak, final AtomicBoolean first) {
		try {
			if (format == 1) {
				writer.write(lineBreak);
				writer.write(indentation);
			}
			switch (format) {
			case 1:
				writer.write("<entry>");
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("<id>");
				break;
			case 2:
				if (!first.getAndSet(false))
					writer.write(',');
				writer.write('{');
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("\"id\":\"");
				break;
			default:
				if (!first.getAndSet(false))
					writer.write(lineBreak);
			}
			writer.write(timeSeries.getPath());
			switch (format) {
			case 1:
				writer.write("</id>");
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("<tags>");
				break;
			case 2:
				writer.write("\",");
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("\"tags\":[");
				break;
			default:
			}
			writeTags(writer, timeSeries, format, lineBreak, indentation);
			switch (format) {
			case 1:
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("</tags>");
				nextLine(writer, lineBreak, indentation, 1);
				writer.write("</entry>");
				break;
			case 2:
				nextLine(writer, lineBreak, indentation, 2);
				writer.write("]");
				nextLine(writer, lineBreak, indentation, 1);
				writer.write('}');
				break;
			default:
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	/*
	 * xml:
	 * <tag>
	 * 		<key>
	 * 			key
	 * 		<key>
	 * 		<values>
	 * 			<value>value1</value>
	 * 			<value>value2</value>
	 * 			...
	 * 		</values>
	 * </tag>
	 * json: 
	 * 		"key": ["value1", "value2", ...]
	 * csv:
	 * 		key: [value1, value2, ...]
	 */
	private final static void writeTags(final Writer writer, final FendoTimeSeries ts, final int format, final char[] lineBreak, final char[] indentation) throws IOException {
		if (format == 1)
			writeTagXml(writer, ts, lineBreak, indentation);
		else
			writeTagsJson(writer, ts, format, lineBreak, indentation);
	}
	
	/*
	 * json: 
	 * 		{ 
	 * 			"key1": ["value1", "value2", ...],
	 * 			"key2": ["value1", "value2", ...]
	 * 		}
	 * csv:
	 * 		key1: [value1, value2, ...]
	 * 		key2: [value1, value2
	 */
	// TODO json encoding
	private final static void writeTagsJson(final Writer writer, final FendoTimeSeries ts, final int format, final char[] lineBreak, final char[] indentation) throws IOException {
		final char d = '\"';
		final Map<String, List<String>> properties = ts.getProperties();
		boolean first = true;
		for (Map.Entry<String, List<String>> entry: properties.entrySet()) {
			if (!first)
				writer.write(',');
			first = false;
			nextLine(writer, lineBreak, indentation, format == 2 ? 4 : 1);
			if (format == 2) {
				writer.write('{');
				writer.write(d);
			}
			writer.write(entry.getKey());
			if (format == 2)
				writer.write(d);
			writer.write(": [");
			if (format == 2) {
				writer.write(d);
				writer.write(entry.getValue().stream().collect(Collectors.joining("\", \"")));
				writer.write(d);
			} else {
				writer.write(entry.getValue().stream().collect(Collectors.joining(", ")));
			}
			writer.write(']');
			if (format == 2)
				writer.write('}');
		}
		
	}
	
	/*
	 * xml:
	 * <tag>
	 * 		<key>
	 * 			key
	 * 		<key>
	 * 		<values>
	 * 			<value>value1</value>
	 * 			<value>value2</value>
	 * 			...
	 * 		</values>
	 * </tag>
	 */
	private final static void writeTagXml(final Writer writer, final FendoTimeSeries ts, final char[] lineBreak, final char[] indentation) throws IOException {
		final Map<String, List<String>> properties = ts.getProperties();
		for (Map.Entry<String, List<String>> entry: properties.entrySet()) {
			nextLine(writer, lineBreak, indentation, 3);
			writer.write("<tag>");
			nextLine(writer, lineBreak, indentation, 4);
			writer.write("<key>");
			writer.write(entry.getKey());
			writer.write("</key>");
			nextLine(writer, lineBreak, indentation, 4);
			writer.write("<values>");
			entry.getValue().forEach(v -> {
				try {
					nextLine(writer, lineBreak, indentation, 5);
					writer.write("<value>");
					writer.write(v);
					writer.write("</value>");
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			nextLine(writer, lineBreak, indentation, 4);
			writer.write("</values>");
			nextLine(writer, lineBreak, indentation, 3);
			writer.write("</tag>");
		}
	}
	
	private final static void nextLine(final Writer writer, final char[] lineBreak, final char[] indentation, final int indent) throws IOException {
		writer.write(lineBreak);
		for (int i=0; i<indent; i++)
			writer.write(indentation);
	}
	
}
