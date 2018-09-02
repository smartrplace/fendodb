package org.smartrplace.logging.fendodb.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.LinkingOption;
import de.iwes.widgets.html.selectiontree.LinkingOptionType;
import de.iwes.widgets.html.selectiontree.SelectionItem;
import de.iwes.widgets.html.selectiontree.TerminalOption;
import de.iwes.timeseries.eval.api.DataProvider;
import de.iwes.timeseries.eval.api.EvaluationInput;
import de.iwes.timeseries.eval.api.TimeSeriesData;
import de.iwes.timeseries.eval.base.provider.utils.EvaluationInputImpl;
import de.iwes.timeseries.eval.base.provider.utils.TimeSeriesDataImpl;

@Component(
		service=DataProvider.class,
		property="provider-id=" + FendoDbSource.ID
)
public class FendoDbSource implements DataProvider<FendoTimeSeries> {
	
	final static String ID = "fendodbProvider";
	private volatile LinkingOption[] options;
	private volatile TimeseriesOption terminal;
	LinkingOptionType type;
	
	@Activate
	protected void activate() {
		final DbOption db = new DbOption(factory);
		final TagOption tags = new TagOption(db);
		this.terminal = new TimeseriesOption(db, tags);
		this.options = new LinkingOption[] {db, tags, terminal};
	}
	
	@Deactivate
	protected void deactivate() {
		this.options = null;
		this.terminal = null;
	}
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public String description(OgemaLocale arg0) {
		return "FendoDb data provider";
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String label(OgemaLocale arg0) {
		return "FendoDb data provider";
	}

	@Override
	public EvaluationInput getData(List<SelectionItem> items) {
		Objects.requireNonNull(items);
		final List<TimeSeriesData> timeSeriesData = new ArrayList<>();
		for (SelectionItem item : items) {
			if (!(item instanceof TimeseriesItem)) {
				throw new IllegalArgumentException("Argument must be of type " + 
						TimeseriesItem.class.getSimpleName() + ", got " + item.getClass().getSimpleName());
			}
			TimeseriesItem tsItem = (TimeseriesItem) item;
			TimeSeriesDataImpl dataImpl = new TimeSeriesDataImpl(
					tsItem.getTimeseries(), 
					item.label(OgemaLocale.ENGLISH), // TODO? 
					item.label(OgemaLocale.ENGLISH), 
					null);
			timeSeriesData.add(dataImpl);
		}
		return new EvaluationInputImpl(timeSeriesData);
	}

	@Override
	public TerminalOption<? extends ReadOnlyTimeSeries> getTerminalOption() {
		return terminal;
	}

	@Override
	public LinkingOption[] selectionOptions() {
		final LinkingOption[] opts = this.options;
		return opts != null ? opts.clone() : null;
	}
	
}
