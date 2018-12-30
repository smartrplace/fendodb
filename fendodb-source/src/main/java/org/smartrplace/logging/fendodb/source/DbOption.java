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
package org.smartrplace.logging.fendodb.source;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.smartrplace.logging.fendodb.FendoDbFactory;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.LinkingOption;
import de.iwes.widgets.html.selectiontree.SelectionItem;

class DbOption extends LinkingOption {

	private final FendoDbFactory factory;
	
	DbOption(FendoDbFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public LinkingOption[] dependencies() {
		return null;
	}

	@Override
	public List<SelectionItem> getOptions(List<Collection<SelectionItem>> dependencies) {
		return factory.getAllInstances().values().stream()
			.map(ref -> new DbItem(ref))
			.collect(Collectors.toList());
	}

	@Override
	public String id() {
		return "dboption";
	}

	@Override
	public String label(OgemaLocale locale) {
		return "Select database";
	}
	
}
