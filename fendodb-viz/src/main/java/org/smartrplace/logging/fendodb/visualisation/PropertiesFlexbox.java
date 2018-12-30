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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import de.iwes.widgets.api.widgets.OgemaWidget;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.html5.Flexbox;
import de.iwes.widgets.html.html5.FlexboxData;
import de.iwes.widgets.html.html5.flexbox.FlexWrap;
import de.iwes.widgets.html.html5.flexbox.JustifyContent;

class PropertiesFlexbox extends Flexbox {

	private static final long serialVersionUID = 1L;
	private static final AtomicInteger cnt = new AtomicInteger(0);
	
	PropertiesFlexbox(WidgetPage<?> page, String id) {
		super(page, id, false);
		setDefaultFlexWrap(FlexWrap.WRAP);
		setDefaultJustifyContent(JustifyContent.SPACE_BETWEEN);
		try {
			setDefaultAddEmptyItem(true);
		} catch (NoSuchMethodError e) { /* for widgets version < 2.1.3 */ }
	}

	@Override
	public FlexboxData createNewSession() {
		return new PropertiesFlexboxData(this);
	}
	
	@Override
	public PropertiesFlexboxData getData(OgemaHttpRequest req) {
		return (PropertiesFlexboxData) super.getData(req);
	}
	
	void updateProps(final Map<String,Collection<String>> properties, final OgemaHttpRequest req) {
		getData(req).updateProps(properties, req);
	}
	
	void updateTags(final Collection<String> tags, final OgemaHttpRequest req) {
		getData(req).updateTags(tags, req);
	}
	
	Map<String, Collection<String>> getSelectedProperties(final OgemaHttpRequest req) {
		return getData(req).valueSelectors.values().stream()
			.collect(Collectors.toMap(selector -> selector.getPropertyKey(), selector -> selector.getSelectedValues(req)));
	}
	
	private final static class PropertiesFlexboxData extends FlexboxData {

		// map tag -> values
		Map<String, Collection<String>> allProps = Collections.emptyMap();
		// map tag -> selector
		final Map<String, PropertySelectorSnippet> valueSelectors = new HashMap<>();
		
		PropertiesFlexboxData(PropertiesFlexbox flexbox) {
			super(flexbox);
			try {
				setFlexGrow(1, null);
			} catch (NullPointerException e) {/* for widgets version < 2.1.3 */}
		}
		
		void updateTags(final Collection<String> tags, final OgemaHttpRequest req) {
			final Iterator<Map.Entry<String, PropertySelectorSnippet>> selectorKeys = valueSelectors.entrySet().iterator();
			while (selectorKeys.hasNext()) {
				final Map.Entry<String, PropertySelectorSnippet> entry = selectorKeys.next();
				final String key = entry.getKey();
				if (tags.contains(key))
					continue;
				final PropertySelectorSnippet selector = entry.getValue();
				selectorKeys.remove();
				removeItem(selector);
			}
			tags.stream()
				.filter(tag -> !valueSelectors.containsKey(tag) && allProps.containsKey(tag))
				.map(tag -> new PropertySelectorSnippet(widget, cnt.getAndIncrement(), req, tag, allProps.get(tag)))
				.forEach(selector -> {
					addItem(getNewPosition(selector), selector);
					valueSelectors.put(selector.getPropertyKey(), selector);
				});
		}
		
		int getNewPosition(final PropertySelectorSnippet snippet) {
			final String tag = snippet.getPropertyKey();
			final List<OgemaWidget> currentWidgets = getItems();
			return (int) currentWidgets.stream()
				.filter(item -> ((PropertySelectorSnippet) item).getPropertyKey().compareToIgnoreCase(tag) <= 0)
				.count();
		}
		
		
		void updateProps(final Map<String,Collection<String>> properties, final OgemaHttpRequest req) {
			this.allProps = properties;
			final Iterator<Map.Entry<String, PropertySelectorSnippet>> selectorKeys = valueSelectors.entrySet().iterator();
			while (selectorKeys.hasNext()) {
				final Map.Entry<String, PropertySelectorSnippet> entry = selectorKeys.next();
				final String key = entry.getKey();
				if (properties.containsKey(key))
					continue;
				final PropertySelectorSnippet selector = entry.getValue();
				selectorKeys.remove();
				removeItem(selector);
			}
			properties.entrySet().stream()
				.filter(entry -> !valueSelectors.containsKey(entry.getKey()))
				.map(entry -> new PropertySelectorSnippet(widget, cnt.getAndIncrement(), req, entry.getKey(), entry.getValue()))
				.forEach(selector -> {
					addItem(getNewPosition(selector), selector);
					valueSelectors.put(selector.getPropertyKey(), selector);
				});
		}
		
		
		
	}
	
}
