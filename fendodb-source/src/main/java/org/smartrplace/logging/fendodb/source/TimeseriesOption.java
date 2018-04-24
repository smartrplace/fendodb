package org.smartrplace.logging.fendodb.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.html.selectiontree.LinkingOption;
import de.iwes.widgets.html.selectiontree.SelectionItem;
import de.iwes.widgets.html.selectiontree.TerminalOption;

public class TimeseriesOption extends TerminalOption<FendoTimeSeries> {
	
	private final LinkingOption[] dependencies;
	
	TimeseriesOption(DbOption dbOption, TagOption tagOption) {
		this.dependencies = new LinkingOption[] {dbOption, tagOption};
	}

	@Override
	public FendoTimeSeries getElement(SelectionItem item) {
		if (!(item instanceof TimeseriesItem))
			return null;
		return ((TimeseriesItem) item).getTimeseries();
	}

	@Override
	public LinkingOption[] dependencies() {
		return dependencies.clone();
	}

	@Override
	public List<SelectionItem> getOptions(List<Collection<SelectionItem>> dependencies) {
		if (dependencies == null || dependencies.isEmpty() || dependencies.size() != 2)
			return Collections.emptyList();
		final List<String> tags = dependencies.get(1).stream()
			.map(item -> ((TagItem) item).getTag())
			.collect(Collectors.toList());
		final Stream<DataRecorderReference> recorders  = dependencies.get(0).stream()
				.map(item -> ((DbItem) item).getDataRecorder());
		try {
			if (tags.isEmpty()) {
				return recorders.flatMap(rec -> getTimeSeries(rec))
						.map(ts -> new TimeseriesItem(ts))
						.collect(Collectors.toList());
			}
			final String[] arr = tags.toArray(new String[tags.size()]);
			final TimeSeriesMatcher matcher = SearchFilterBuilder.getInstance()
				.filterByTags(arr)
				.build();
			return recorders.flatMap(ref -> getTimeSeries(ref, matcher))
					.map(ts -> new TimeseriesItem(ts))
					.collect(Collectors.toList());
		} catch (UncheckedIOException e) {
			e.printStackTrace(); // TODO
			return Collections.emptyList();
		}
	}
	
	private static Stream<FendoTimeSeries> getTimeSeries(final DataRecorderReference ref, final TimeSeriesMatcher matcher) {
		try (final CloseableDataRecorder rec = Utils.open(ref)) {
			if (rec == null)
				return Stream.empty();
			return rec.findTimeSeries(matcher).stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static Stream<FendoTimeSeries> getTimeSeries(final DataRecorderReference ref) {
		try (final CloseableDataRecorder rec = Utils.open(ref)) {
			if (rec == null)
				return Stream.empty();
			return rec.getAllTimeSeries().stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	

	@Override
	public String id() {
		return "timeseries";
	}

	@Override
	public String label(OgemaLocale locale) {
		return "Select timeseries";
	}
	
}
