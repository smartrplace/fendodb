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
