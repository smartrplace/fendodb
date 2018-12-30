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
package org.smartrplace.logging.fendodb.visualisation;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import de.iwes.widgets.api.extended.html.bricks.PageSnippet;
import de.iwes.widgets.api.widgets.OgemaWidget;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.form.dropdown.DropdownOption;
import de.iwes.widgets.html.form.label.Label;
import de.iwes.widgets.html.multiselect.Multiselect;

@SuppressWarnings("serial")
class PropertySelectorSnippet extends PageSnippet {

	private final String tag;
	private final Label header;
	private final Multiselect valueSelector;
	
	PropertySelectorSnippet(OgemaWidget widget, int id, OgemaHttpRequest req, String tag, Collection<String> values) {
		super(widget, "propSnippet" + id, req);
		this.tag = tag;
		this.header = new Label(this, "header" + id, req);
		header.setText("Select " + formatTag(tag), req);
		this.valueSelector = new Multiselect(this, "valueSelector" + id, req);
		valueSelector.setOptions(getDropOptions(values), req);
		buildSnippet(req);
	}

	private final void buildSnippet(final OgemaHttpRequest req) {
		this.append(header, req).linebreak(req).append(valueSelector, req);
	}
	
	String getPropertyKey() {
		return tag;
	}
	
	Collection<String> getSelectedValues(final OgemaHttpRequest req) {
		return valueSelector.getSelectedValues(req);
	}
	
	private final static List<DropdownOption> getDropOptions(final Collection<String> values) {
		return values.stream()
			.map(val -> new DropdownOption(val, val, false))
			.collect(Collectors.toList());
	}
	
	private final static String formatTag(final String tag) {
		final int sum = tag.chars()
			.filter(c -> Character.isLetter(c))
			.map(c -> Character.isLowerCase(c) ? +1 : -1)
			.sum();
		if (sum <= 0)
			return tag;
		final StringBuilder sb = new StringBuilder();
		for (int i=0;i<tag.length();i++) {
			final char c = tag.charAt(i);
			if (Character.isUpperCase(c)) {
				sb.append(' ').append(Character.toLowerCase(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
}
