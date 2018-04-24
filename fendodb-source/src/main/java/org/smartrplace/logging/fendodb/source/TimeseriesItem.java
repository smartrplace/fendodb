package org.smartrplace.logging.fendodb.source;

import org.ogema.tools.resource.util.ResourceUtils;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

import de.iwes.widgets.html.selectiontree.samples.SelectionItemImpl;

class TimeseriesItem extends SelectionItemImpl {
	
	private final FendoTimeSeries timeSeries;

	TimeseriesItem(FendoTimeSeries timeSeries) {
		super("fendo_"  +ResourceUtils.getValidResourceName(timeSeries.getPath()), "Time series: " + timeSeries.getPath());
		this.timeSeries = timeSeries;
	}
	
	FendoTimeSeries getTimeseries() {
		return timeSeries;
	}
	
}
