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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.ogema.core.application.ApplicationManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

import de.iwes.widgets.api.extended.WidgetData;
import de.iwes.widgets.api.extended.html.bricks.PageSnippet;
import de.iwes.widgets.api.extended.plus.SelectorTemplate;
import de.iwes.widgets.api.widgets.LazyWidgetPage;
import de.iwes.widgets.api.widgets.WidgetPage;
import de.iwes.widgets.api.widgets.dynamics.TriggeredAction;
import de.iwes.widgets.api.widgets.dynamics.TriggeringAction;
import de.iwes.widgets.api.widgets.html.StaticTable;
import de.iwes.widgets.api.widgets.localisation.OgemaLocale;
import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;
import de.iwes.widgets.html.alert.Alert;
import de.iwes.widgets.html.buttonconfirm.ButtonConfirm;
import de.iwes.widgets.html.buttonconfirm.ButtonConfirmData;
import de.iwes.widgets.html.form.button.Button;
import de.iwes.widgets.html.form.button.ButtonData;
import de.iwes.widgets.html.form.dropdown.TemplateDropdown;
import de.iwes.widgets.html.form.label.Header;
import de.iwes.widgets.html.form.label.Label;
import de.iwes.widgets.html.form.textfield.TextField;
import de.iwes.widgets.html.popup.Popup;
import de.iwes.widgets.template.DisplayTemplate;

@Component(
		service=LazyWidgetPage.class,
		property= {
				LazyWidgetPage.BASE_URL + "=" + FendoVizConstants.URL_BASE, 
				LazyWidgetPage.RELATIVE_URL + "=tags.html",
				LazyWidgetPage.MENU_ENTRY + "=Tags view"
		}
)
public class TagsPage implements LazyWidgetPage {
	
	@Reference
	private FendoDbFactory factory;
	
	@Override
	public void init(ApplicationManager appMan, WidgetPage<?> page) {
		new TagsPageInit(page, factory);
	}
	
	private static class TagsPageInit {
	
		private final WidgetPage<?> page;
	
		private final Header header;
		private final Alert alert;
		private final FendoSelector dbSelector;
		private final TemplateDropdown<FendoTimeSeries> timeSeriesSelector;
		private final Label tags;
		private final Button applyStandardTags;
		private final Popup editPopup;
		private final Label editSelected;
		private final Label tagsCopy;
		private final Label tagsInDb;
		private final Label allTagProps;
		private final Button editTrigger;
		private final TextField editTagField;
		private final TextField editValueField;
		private final Button editSubmit;
		private final ButtonConfirm editDelete;
	
		@SuppressWarnings("serial")
		TagsPageInit(final WidgetPage<?> page, final FendoDbFactory factory) {
			this.page = page;
	
			this.header = new Header(page, "header", "FendoDB tags");
			header.addDefaultStyle(WidgetData.TEXT_ALIGNMENT_CENTERED);
			header.setDefaultColor("blue");
	
			this.alert = new Alert(page, "alert", "");
			alert.setDefaultVisibility(false);
	
			this.dbSelector = new FendoSelector(page, "dbSelector", factory);
			this.timeSeriesSelector = new TemplateDropdown<FendoTimeSeries>(page, "tsSelector") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference ref = dbSelector.getSelectedItem(req);
					if (ref == null) {
						update(Collections.emptyList(), req);
						return;
					}
					try (final CloseableDataRecorder rec = ref.getDataRecorder()) {
						update(rec.getAllTimeSeries(), req);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
	
			};
			timeSeriesSelector.setTemplate(new DisplayTemplate<FendoTimeSeries>() {
	
				@Override
				public String getLabel(FendoTimeSeries object, OgemaLocale locale) {
					return object.getPath();
				}
	
				@Override
				public String getId(FendoTimeSeries object) {
					return object.getPath();
				}
			});
			this.tags = new TagsLabel(page, "tagsLabel", timeSeriesSelector);
	
			this.applyStandardTags = new Button(page, "applyStandardTags", "Start tagging") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					if (dbSelector.getSelectedItem(req) == null) {
						disable(req);
						return;
					}
					enable(req);
				}
	
				@Override
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					try (final CloseableDataRecorder rec = Utils.getDataRecorder(dbSelector, req, false)) {
						if (rec == null)
							return;
						final boolean result;
						try {
							result = org.smartrplace.logging.fendodb.tagging.api.TaggingUtils.tagDatabase(rec);
						} catch (NoClassDefFoundError e) {
							alert.showAlert("Tagging utils seem not to be installed on this system.", false, req);
							return;
						}
						if (result)
							alert.showAlert("Tagged data in " + rec.getPath(), true, req);
						else
							alert.showAlert("Tagging failed for " + rec.getPath(), false, req);
					} catch (IOException e) {}
				}
	
	
			};
	
			// edit popup
	
			this.editPopup = new Popup(page, "editPopup", true);
			editPopup.setDefaultWidth("80%");
			this.editTrigger = new Button(page, "editTrigger", "Open dialog");
			editTrigger.addDefaultStyle(ButtonData.BOOTSTRAP_BLUE);
			this.editSelected = new Label(page, "editSelected") {
	
				public void onGET(OgemaHttpRequest req) {
					final FendoTimeSeries ts = timeSeriesSelector.getSelectedItem(req);
					if (ts == null) {
						setText("", req);
						return;
					}
					setText(ts.getPath(), req);
				}
	
			};
			this.tagsCopy = new TagsLabel(page, "tagsCopy", timeSeriesSelector);
			this.tagsInDb = new Label(page, "tagsInDb") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference ref = dbSelector.getSelectedItem(req);
					if (ref == null) {
						setText("", req);
						return;
					}
					try (CloseableDataRecorder rec = ref.getDataRecorder()) {
						setText(rec.getAllProperties().keySet().stream().collect(Collectors.joining(", ")), req);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
	
			};
			this.allTagProps = new Label(page, "allTagProps") {
	
				@Override
				public void onGET(OgemaHttpRequest req) {
					final DataRecorderReference ref = dbSelector.getSelectedItem(req);
					if (ref == null) {
						setText("", req);
						return;
					}
					try (CloseableDataRecorder rec = ref.getDataRecorder()) {
						final String tag = editTagField.getValue(req);
						final Collection<String> values = rec.getAllPropertyValues(tag);
						if (values.isEmpty()) {
							setText("", req);
							return;
						}
						setText(values.stream().collect(Collectors.joining(", ")), req);
					} catch (IOException e) {
						e.printStackTrace();
					}
	
				}
	
			};
			this.editTagField = new TextField(page, "editTagField");
			this.editValueField = new TextField(page, "editValueField") {
	
				public void onGET(OgemaHttpRequest req) {
					final String tag = editTagField.getValue(req);
					if (tag.isEmpty())
						return;
					final FendoTimeSeries ts = timeSeriesSelector.getSelectedItem(req);
					if (ts == null)
						return;
					final String prop = ts.getFirstProperty(tag);
					if (prop != null)
						setValue(prop, req);
				};
	
			};
			this.editSubmit = new Button(page, "editSubmit", "Submit") {
	
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final String tag = editTagField.getValue(req);
					if (tag.isEmpty()) {
						alert.showAlert("No tag selected", false, req);
						return;
					}
					final FendoTimeSeries ts = timeSeriesSelector.getSelectedItem(req);
					if (ts == null)
						return;
					final String value = editValueField.getValue(req);
					ts.setProperty(tag, value);
					alert.showAlert("Property set for time series " +ts.getPath() + ": " + tag + ": " + value , true, req);
				}
	
			};
			editSubmit.addDefaultStyle(ButtonData.BOOTSTRAP_LIGHT_BLUE);
			this.editDelete = new ButtonConfirm(page, "editDelete", "Delete property") {
	
				public void onGET(OgemaHttpRequest req) {
					disable(req);
					final String tag = editTagField.getValue(req);
					if (tag.isEmpty())
						return;
					final FendoTimeSeries ts = timeSeriesSelector.getSelectedItem(req);
					if (ts == null)
						return;
					if (ts.hasProperty(tag))
						enable(req);
				}
	
				public void onPOSTComplete(String data, OgemaHttpRequest req) {
					final String tag = editTagField.getValue(req);
					if (tag.isEmpty()) {
						alert.showAlert("No tag selected", false, req);
						return;
					}
					final FendoTimeSeries ts = timeSeriesSelector.getSelectedItem(req);
					if (ts == null)
						return;
					if (ts.removeProperty(tag))
						alert.showAlert("Property removed from time series " +ts.getPath() + ": " + tag , true, req);
					else
						alert.showAlert("Property not found: " + tag + ", time series: " + ts.getPath(), false, req);
				}
	
			};
			editDelete.setDefaultCancelBtnMsg("Cancel");
			editDelete.setDefaultConfirmBtnMsg("Delete");
			editDelete.setDefaultConfirmMsg("Do you really want to remove the tag?");
			editDelete.setDefaultConfirmPopupTitle("Confirm deletion");
			editDelete.addDefaultStyle(ButtonData.BOOTSTRAP_RED);
			editDelete.addDefaultStyle(ButtonConfirmData.CANCEL_LIGHT_BLUE);
			editDelete.addDefaultStyle(ButtonConfirmData.CONFIRM_RED);
	
			buildPage();
			setDependencies();
		}
	
		private final void buildPage() {
			int row = 0;
			final PageSnippet popupSnippet = new PageSnippet(page, "editPopupBody", true);
			popupSnippet.append(new StaticTable(8, 2, new int[] {4,8})
					.setContent(row, 0, "Selected time series").setContent(row++, 1, editSelected)
					.setContent(row, 0, "Timeseries tags").setContent(row++, 1, tagsCopy)
					.setContent(row, 0, "All tags in database").setContent(row++, 1, tagsInDb)
					.setContent(row, 0, "All Property values for selected tag").setContent(row++, 1, allTagProps)
					.setContent(row, 0, "Enter property key").setContent(row++, 1, editTagField)
					.setContent(row, 0, "Enter property value").setContent(row++, 1, editValueField)
															   .setContent(row++, 1, editSubmit)
															   .setContent(row++, 1, editDelete)
				, null);
			editPopup.setBody(popupSnippet, null);
			editPopup.setTitle("Edit tags", null);
			page.append(header).linebreak().append(alert).append(new StaticTable(5, 2, new int[] {2,4})
					.setContent(row = 0, 0, "Select database").setContent(row++, 1, dbSelector)
					.setContent(row, 0, "Apply standard tags").setContent(row++, 1, applyStandardTags)
					.setContent(row, 0, "Select time series").setContent(row++, 1, timeSeriesSelector)
					.setContent(row, 0, "Tags").setContent(row++, 1, tags)
					.setContent(row, 0, "Edit tags").setContent(row++, 1, editTrigger)
			).linebreak().append(editPopup);
		}
	
		private final void setDependencies() {
			dbSelector.triggerAction(timeSeriesSelector, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			dbSelector.triggerAction(applyStandardTags, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			timeSeriesSelector.triggerAction(tags, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			timeSeriesSelector.triggerAction(tags, TriggeringAction.GET_REQUEST, TriggeredAction.GET_REQUEST);
	
			editTrigger.triggerAction(editSelected, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTrigger.triggerAction(editDelete, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTrigger.triggerAction(tagsCopy, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTrigger.triggerAction(tagsInDb, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTrigger.triggerAction(editPopup, TriggeringAction.POST_REQUEST, TriggeredAction.SHOW_WIDGET);
	
			editSubmit.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editSubmit.triggerAction(editPopup, TriggeringAction.POST_REQUEST, TriggeredAction.HIDE_WIDGET);
			editSubmit.triggerAction(tags, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editDelete.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editDelete.triggerAction(tags, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editDelete.triggerAction(editPopup, TriggeringAction.POST_REQUEST, TriggeredAction.HIDE_WIDGET);
			editTagField.triggerAction(editValueField, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTagField.triggerAction(editDelete, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			editTagField.triggerAction(allTagProps, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
	
			applyStandardTags.triggerAction(alert, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
			applyStandardTags.triggerAction(tags, TriggeringAction.POST_REQUEST, TriggeredAction.GET_REQUEST);
		}
	
		@SuppressWarnings("serial")
		private final static class TagsLabel extends Label {
	
			private final SelectorTemplate<FendoTimeSeries> selector;
	
			TagsLabel(WidgetPage<?> page, String id, SelectorTemplate<FendoTimeSeries> selector) {
				super(page, id);
				this.selector = selector;
			}
	
			@Override
			public void onGET(OgemaHttpRequest req) {
				final FendoTimeSeries ts = selector.getSelectedItem(req);
				if (ts == null) {
					setText("", req);
					return;
				}
				final StringBuilder sb = new StringBuilder();
				ts.getProperties().forEach((key, value) -> sb.append(key).append(':').append(' ').append(value).append("<br>"));
				setHtml(sb.toString(), req);
			}
	
		}
	}

}
