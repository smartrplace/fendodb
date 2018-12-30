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

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collection;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.template.DisplayTemplate;

public class UnitSelector extends TemplateDropdown<TemporalUnit> {

	private static final long serialVersionUID = 1L;
	private final static Collection<TemporalUnit> units = Arrays.asList(
		ChronoUnit.MINUTES,
		ChronoUnit.HOURS,
		ChronoUnit.DAYS,
		ChronoUnit.WEEKS,
		ChronoUnit.MONTHS,
		ChronoUnit.YEARS
	);

	public UnitSelector(WidgetPage<?> page, String id) {
		super(page, id);
		setDefaultItems(units);
		selectDefaultItem(ChronoUnit.DAYS);
		setTemplate(new DisplayTemplate<TemporalUnit>() {
			
			@Override
			public String getLabel(TemporalUnit object, OgemaLocale locale) {
				return object.toString();
			}
			
			// ensures appropriate sorting
			@Override
			public String getId(TemporalUnit object) {
				return String.valueOf(object.getDuration().toMillis());
			}
		});
		setComparator((o1,o2) -> Long.compare(Long.parseLong(o1.getValue()), Long.parseLong(o2.getValue())));
	}
	
}
