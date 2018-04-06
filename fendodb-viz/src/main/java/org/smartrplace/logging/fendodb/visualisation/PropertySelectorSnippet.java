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
