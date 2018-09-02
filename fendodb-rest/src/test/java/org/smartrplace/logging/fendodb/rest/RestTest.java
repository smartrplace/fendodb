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
package org.smartrplace.logging.fendodb.rest;

import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentServiceObjects;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.impl.SlotsDbFactoryImpl;
import org.smartrplace.logging.fendodb.impl.StatisticsServiceImpl;
import org.smartrplace.logging.fendodb.rest.Parameters;
import org.smartrplace.logging.fendodb.rest.RecordedDataServlet;
import org.smartrplace.logging.fendodb.stats.StatisticsService;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class RestTest {

	private final static String TEST_FOLDER = "testdata";
	private final File testdir = new File(TEST_FOLDER);
	private volatile FendoDbFactory factory;
	
	@Before
	public void deleteTestdata() throws IOException {
		if (testdir.exists())
			FileUtils.deleteDirectory(testdir);
		Assert.assertFalse("Test directory still exists",testdir.exists());
		factory = createFactory();
	}
	
	@After
	public void closeFactory() {
		closeFactory(factory);
		factory = null;
	}
	
	public static RecordedDataServlet createServlet(final FendoDbFactory factory) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		final RecordedDataServlet servlet = new RecordedDataServlet();
		final Field f = RecordedDataServlet.class.getDeclaredField("factory");
		f.setAccessible(true);
		f.set(servlet, factory);
		
		final StatisticsService stats = new StatisticsServiceImpl();
		final ComponentServiceObjects<StatisticsService> serviceWrapper = mock(ComponentServiceObjects.class);
		when(serviceWrapper.getService()).thenReturn(stats);
		final Field f2 = RecordedDataServlet.class.getDeclaredField("statisticsService");
		f2.setAccessible(true);
		f2.set(servlet, serviceWrapper);
		
		return servlet;
	}

	public static FendoDbFactory createFactory() {
		final SlotsDbFactoryImpl factory = new SlotsDbFactoryImpl();
		try {
			final Method m  = SlotsDbFactoryImpl.class.getDeclaredMethod("activate", BundleContext.class);
			m.setAccessible(true);
			m.invoke(factory, (BundleContext) null);
			return factory;
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void closeFactory(FendoDbFactory factory) {
		Objects.requireNonNull(factory);
		try {
			final Method m  = SlotsDbFactoryImpl.class.getDeclaredMethod("deactivate");
			m.setAccessible(true);
			m.invoke(factory);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static HttpServletRequest requestWithParams(final String method, final Map<String,String> params) throws IOException {
		return requestWithParams(method, params, null);
	}
	
	private static HttpServletRequest requestWithParams(final String method, final Map<String,String> params, final Map<String, String> headers) throws IOException {
		return requestWithParams(method, params, headers, null);
	}
	
	private static HttpServletRequest requestWithParams(final String method, final Map<String,String> params, 
			final Map<String, String> headers, final String body) throws IOException {
		final HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getMethod()).thenReturn(method);
		if (params != null) {
			params.entrySet().forEach(entry -> when(req.getParameter(entry.getKey())).thenReturn(entry.getValue()));
			params.entrySet().forEach(entry -> when(req.getParameterValues(entry.getKey())).thenReturn(new String[] { entry.getValue()} ));
			final Map<String,String[]> arrParams = new HashMap<>(params.size());
			params.entrySet().forEach(entry -> arrParams.put(entry.getKey(), new String[] {entry.getValue()} ));
			when(req.getParameterMap()).thenReturn(arrParams);
		}
		if (headers != null) {
			headers.entrySet().forEach(entry -> when(req.getHeader(entry.getKey())).thenReturn(entry.getValue()));
		}
		if (body != null) {
			when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
		}
		return req;
	}
	
	private static HttpServletResponse getResponse(final Response response) throws IOException {
		final HttpServletResponse resp = mock(HttpServletResponse.class);
		when(resp.getWriter()).thenReturn(response.writerOut);
		when(resp.getOutputStream()).thenReturn(response.streamOut);
		return resp;
	}
	
	private static String sendJsonRequest(final Map<String,String> params, final RecordedDataServlet servlet) throws ServletException, IOException {
		final HttpServletRequest request = requestWithParams("GET", params, Collections.singletonMap("Accept", "application/json")); // TODO other formats
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doGet(request, resp);
		verify(resp).setContentType("application/json");
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		return response.getResponseAsString();
	}
	
	private static String getContentType(FendodbSerializationFormat format) {
		return format == FendodbSerializationFormat.XML ? "application/xml" :
			format == FendodbSerializationFormat.JSON ? "application/x-json-stream" :
			"text/csv";
	}
	
	private static String serializeValueJson(final long t, final float val) {
		final JSONObject json = new JSONObject();
		json.put("time", t);
		json.put("value", val);
		return json.toString();
	}
	
	private static String serializeValueXml(final long t, final float val) {
		final StringBuilder sb = new StringBuilder();
		sb.append("<entry><time>").append(t).append("</time><value>")
			.append(val).append("</value><quality>GOOD</quality></entry>");
		return sb.toString();
	}
	
	private static String serializeValueCsv(final long t, final float val) {
		final StringBuilder sb = new StringBuilder();
		sb.append(t).append(';').append(val).append('\n');
		return sb.toString();
	}
	
	@Test
	public void restWorks() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException, IOException {
		final RecordedDataServlet servlet = createServlet(factory);
		final HttpServletRequest request = requestWithParams("GET", null, Collections.singletonMap("Accept", "application/json"));
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doGet(request, resp);
		verify(resp).setContentType("application/json");
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		final JSONObject json = new JSONObject(response.getResponseAsString());
		final JSONArray entries = json.getJSONArray("entries");
		Assert.assertNotNull(entries);
	}
	
	@Test
	public void putWorks() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException, IOException {
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params0 = new HashMap<>(4);
		params0.put(Parameters.PARAM_DB, TEST_FOLDER);
		params0.put(Parameters.PARAM_TARGET, Parameters.TARGET_DB);
		final HttpServletRequest request = requestWithParams("PUT", params0);
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doPut(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		Assert.assertTrue("Database does not exist after PUT operation", factory.databaseExists(testdir.toPath()));
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		final String id = "test";
		params.put(Parameters.PARAM_ID, id);
		params.put(Parameters.PARAM_UPDATE_MODE, "on_value_update");
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_TIMESERIES);
		final HttpServletRequest request2 = requestWithParams("PUT", params);
		final Response response2 = new Response();
		final HttpServletResponse resp2 = getResponse(response2); 
		servlet.doPut(request2, resp2);
		verify(resp2).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testdir.toPath())) {
			Assert.assertNotNull(recorder);
			final RecordedData data = recorder.getRecordedDataStorage(id);
			Assert.assertNotNull("Timeseries is null after PUT operation",data);
		}
	}

	@Test
	public void getWorks() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final String id = "test";
		final List<SampledValue> values = Arrays.asList(
				new SampledValue(new FloatValue(12.2F), 23, Quality.GOOD),
				new SampledValue(new FloatValue(23), 2342, Quality.GOOD),
				new SampledValue(new FloatValue(2.2F), 43452, Quality.GOOD),
				new SampledValue(new FloatValue(-12.2F), 72423, Quality.GOOD),
				new SampledValue(new FloatValue(0), 94352, Quality.GOOD)
		);
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = recorder.createRecordedDataStorage(id, cfg);
			ts.insertValues(values);
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_ID, id);
		final String result = sendJsonRequest(params, servlet);
		final JSONObject json = new JSONObject(result);
		Assert.assertNotNull(json);
		final JSONArray array = json.getJSONArray("entries");
		Assert.assertEquals("Unexpected values size",values.size(), array.length());
		
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_SIZE);
		final String result2 = sendJsonRequest(params, servlet);
		final JSONObject json2 = new JSONObject(result2);
		Assert.assertNotNull(json2);
		Assert.assertEquals("Unexpected size", values.size(), json2.getInt("size"));
		
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_NEXT);
		params.put(Parameters.PARAM_TIMESTAMP, String.valueOf(Long.MIN_VALUE));
		final String result3 = sendJsonRequest(params, servlet);
		final JSONObject json3 = new JSONObject(result3);
		Assert.assertNotNull(json3);
		final double val = json3.getDouble("value");
		Assert.assertEquals("Unexpected value", values.get(0).getValue().getDoubleValue(), val, 0.1);
		
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_PREVIOUS);
		params.put(Parameters.PARAM_TIMESTAMP, String.valueOf(Long.MAX_VALUE));
		final String result4 = sendJsonRequest(params, servlet);
		final JSONObject json4 = new JSONObject(result4);
		Assert.assertNotNull(json4);
		final double val4 = json4.getDouble("value");
		Assert.assertEquals("Unexpected value", values.get(values.size()-1).getValue().getDoubleValue(), val4, 0.1);

	}
	
	private void postWorks(FendodbSerializationFormat format) throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final String id = "test";
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = recorder.createRecordedDataStorage(id, cfg);
			Assert.assertTrue(ts.isEmpty());
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_ID, id);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_VALUE);
		final float value = 324.2F;
		final long t = 324;
		final String serialized = format == FendodbSerializationFormat.JSON ? serializeValueJson(t, value) :
			format == FendodbSerializationFormat.XML ? serializeValueXml(t, value) :
			serializeValueCsv(t, value);
		final HttpServletRequest request = requestWithParams("POST", params, 
				Collections.singletonMap("Content-Type", getContentType(format)), serialized);
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doPost(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = recorder.getRecordedDataStorage(id);
			final List<SampledValue> values = ts.getValues(Long.MIN_VALUE);
			Assert.assertFalse("Value missing",values.isEmpty());
			Assert.assertEquals("Unexpected nr of values",1, values.size());
			final SampledValue sv = values.get(0);
			Assert.assertEquals("Unexpected timestamp", t, sv.getTimestamp());
			Assert.assertEquals("Unexpected value", value, sv.getValue().getFloatValue(), 0.1);			
		}
	}

	private void postMultiValuesWorks(final long[] t, final float[] v, final FendodbSerializationFormat format) 
			throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		if (t.length != v.length)
			throw new IllegalArgumentException();
		final String id = "test";
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = recorder.createRecordedDataStorage(id, cfg);
			Assert.assertTrue(ts.isEmpty());
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_ID, id);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_VALUES);
		final StringBuilder sb = new StringBuilder();
		for (int i=0;i<t.length;i++) {
			final String serialized = format == FendodbSerializationFormat.XML ? serializeValueXml(t[i], v[i]) :
				format == FendodbSerializationFormat.JSON ? serializeValueJson(t[i], v[i]) :
				serializeValueCsv(t[i],v[i]);
			sb.append(serialized);
		}
		final HttpServletRequest request = requestWithParams("POST", params, 
				Collections.singletonMap("Content-Type", getContentType(format)), sb.toString());
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doPost(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts = recorder.getRecordedDataStorage(id);
			final List<SampledValue> valuesReturn = ts.getValues(Long.MIN_VALUE);
			Assert.assertFalse("Values missing",valuesReturn.isEmpty());
			Assert.assertEquals("Unexpected nr of values",t.length, valuesReturn.size());
			int cnt = 0;
			for (SampledValue sv :valuesReturn) {
				Assert.assertEquals("Unexpected value", v[cnt], sv.getValue().getFloatValue(), 0.1F);
				Assert.assertEquals("Unexpected time stamp",t[cnt++], sv.getTimestamp());;
			}
		}
	}
	
	@Test
	public void postWorksJson() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, DataRecorderException, ServletException {
		postWorks(FendodbSerializationFormat.JSON);
	}
	
	@Test
	public void postWorksJson2() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final long[] t  = { 10, 25, 232, 324234 };
		final float[] v = { 23.3F, 12, -23, 1233.65F };
		postMultiValuesWorks(t, v, FendodbSerializationFormat.JSON);
	}

	@Test
	public void postWorksJson3() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final int size = 1000;
		final long[] t = new long[size];
		final float[] v = new float[size];
		for (int i=0;i<size;i++) {
			t[i] = i * 1000000;
			v[i] = (float) (100 * Math.random());
		}
		postMultiValuesWorks(t, v, FendodbSerializationFormat.JSON);
	}
	
	@Test
	public void postWorksXml() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, DataRecorderException, ServletException {
		postWorks(FendodbSerializationFormat.XML);
	}
	
	@Test
	public void postWorksXml2() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final long[] t  = { 10, 25, 232, 324234 };
		final float[] v = { 23.3F, 12, -23, 1233.65F };
		postMultiValuesWorks(t, v, FendodbSerializationFormat.XML);
	}

	@Test
	public void postWorksXml3() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final int size = 1000;
		final long[] t = new long[size];
		final float[] v = new float[size];
		for (int i=0;i<size;i++) {
			t[i] = i * 1000000;
			v[i] = (float) (100 * Math.random());
		}
		postMultiValuesWorks(t, v, FendodbSerializationFormat.XML);
	}
	
	@Test
	public void postWorksCsv() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, DataRecorderException, ServletException {
		postWorks(FendodbSerializationFormat.CSV);
	}

	@Test
	public void postWorksCsv2() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final long[] t  = { 10, 25, 232, 324234 };
		final float[] v = { 23.3F, 12, -23, 1233.65F };
		postMultiValuesWorks(t, v, FendodbSerializationFormat.CSV);
	}

	@Test
	public void postWorksCsv3() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final int size = 1000;
		final long[] t = new long[size];
		final float[] v = new float[size];
		for (int i=0;i<size;i++) {
			t[i] = i * 1000000;
			v[i] = (float) (100 * Math.random());
		}
		postMultiValuesWorks(t, v, FendodbSerializationFormat.CSV);
	}
	
	@Test
	public void getFindWorksJson() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		
		final String id0 = "test0";
		final String id1 = "test1";
		final String tag0 = "tag0";
		final String tag1 = "tag1";
		final String value = "value";
		
		final List<SampledValue> values = Arrays.asList( // not really relevant here
				new SampledValue(new FloatValue(12.2F), 23, Quality.GOOD),
				new SampledValue(new FloatValue(23), 2342, Quality.GOOD),
				new SampledValue(new FloatValue(2.2F), 43452, Quality.GOOD),
				new SampledValue(new FloatValue(-12.2F), 72423, Quality.GOOD),
				new SampledValue(new FloatValue(0), 94352, Quality.GOOD)
		);
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = recorder.createRecordedDataStorage(id0, cfg);
			ts0.insertValues(values);
			ts0.addProperty(tag0, value);
			final FendoTimeSeries ts1 = recorder.createRecordedDataStorage(id1, cfg);
			ts1.addProperty(tag1, value);
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_FIND);
		params.put(Parameters.PARAM_TAGS, tag0);
		final String result = sendJsonRequest(params, servlet);
		final JSONObject json = new JSONObject(result);
		Assert.assertNotNull(json);
		final JSONArray array = json.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 1, array.length()); // we expect only id0 as result
		Assert.assertEquals("Unexpected time series id", id0, array.getString(0));
		
		// same check for the other tag
		params.put(Parameters.PARAM_TAGS, tag1);
		final String result2 = sendJsonRequest(params, servlet);
		final JSONObject json2 = new JSONObject(result2);
		Assert.assertNotNull(json2);
		final JSONArray array2 = json2.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 1, array2.length()); // we expect only id0 as result
		Assert.assertEquals("Unexpected time series id", id1, array2.getString(0));
		
		params.remove(Parameters.PARAM_TAGS);
		params.put(Parameters.PARAM_PROPERTIES, tag0 + "=nonsense");
		final String result3 = sendJsonRequest(params, servlet);
		final JSONObject json3 = new JSONObject(result3);
		Assert.assertNotNull(json3);
		final JSONArray array3 = json3.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 0, array3.length()); // we do not expect any findings
		
		params.put(Parameters.PARAM_PROPERTIES, tag0 + "=" + value);
		final String result4 = sendJsonRequest(params, servlet);
		final JSONObject json4 = new JSONObject(result4);
		Assert.assertNotNull(json4);
		final JSONArray array4 = json4.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 1, array4.length()); // we expect only id0
		Assert.assertEquals("Unexpected time series id", id0, array4.getString(0));
		
		params.remove(Parameters.PARAM_PROPERTIES);
		params.put(Parameters.PARAM_ID, id1); 
		final String result5 = sendJsonRequest(params, servlet);
		final JSONObject json5 = new JSONObject(result5);
		Assert.assertNotNull(json5);
		final JSONArray array5 = json5.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 1, array5.length()); // we expect only id1
		Assert.assertEquals("Unexpected time series id", id1, array5.getString(0));
		
		params.remove(Parameters.PARAM_ID);
		params.put(Parameters.PARAM_ID_EXCLUDED, id1); 
		final String result6 = sendJsonRequest(params, servlet);
		final JSONObject json6 = new JSONObject(result6);
		Assert.assertNotNull(json6);
		final JSONArray array6 = json6.getJSONArray("entries");
		Assert.assertEquals("Unexpected result size", 1, array6.length()); // we expect only id0
		Assert.assertEquals("Unexpected time series id", id0, array6.getString(0));
	}
	
	@Test
	public void addPropertiesWorks() throws DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException, ServletException {
		final String id0 = "test0";
		final String id1 = "test1";
		final String propKey = "prop";
		final String propVal = "myVal";
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = recorder.createRecordedDataStorage(id0, cfg);
			final FendoTimeSeries ts1 = recorder.createRecordedDataStorage(id1, cfg);
			Assert.assertTrue(ts0.getProperties().isEmpty());
			Assert.assertTrue(ts1.getProperties().isEmpty());
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_PROPERTIES);
		params.put(Parameters.PARAM_ID, id0);
		params.put(Parameters.PARAM_PROPERTIES, propKey + "=" + propVal);
		final HttpServletRequest request = requestWithParams("PUT", params); 
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doPut(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testdir.toPath())) {
			Assert.assertNotNull(recorder);
			final FendoTimeSeries ts0 = recorder.getRecordedDataStorage(id0);
			final FendoTimeSeries ts1 = recorder.getRecordedDataStorage(id1);
			Assert.assertNotNull(id0);
			Assert.assertNotNull(id1);
			Assert.assertFalse("Newly added property missing",ts0.getProperties().isEmpty());
			Assert.assertEquals("Unexpected properties size",1, ts0.getProperties().size());
			Assert.assertTrue(ts1.getProperties().isEmpty());
			final List<String> values = ts0.getProperties(propKey);
			Assert.assertEquals("Unexpected properties size",1, values.size());
			Assert.assertEquals("Unexpected property value",propVal, values.get(0));
		}
	}
	
	@Test
	public void removePropertiesWorks() throws IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final String id0 = "test0";
		final String id1 = "test1";
		final String propKey0 = "prop";
		final String propVal0a = "myVal";
		final String propVal0b = "myVal2";
		final String propKey1 = "moreprops";
		final String propVal1 = "morevalues";
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = recorder.createRecordedDataStorage(id0, cfg);
			final FendoTimeSeries ts1 = recorder.createRecordedDataStorage(id1, cfg);
			ts0.addProperty(propKey0, propVal0a);
			ts0.addProperty(propKey0, propVal0b);
			ts0.addProperty(propKey1, propVal1);
			ts1.addProperty(propKey0, propVal0a);
			Assert.assertEquals(2, ts0.getProperties().size());
			Assert.assertEquals(1, ts1.getProperties().size());
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4,1);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_PROPERTIES);
		params.put(Parameters.PARAM_ID, id0);
		params.put(Parameters.PARAM_PROPERTIES, propKey0 + "=" + propVal0a); // we remove only this one
		final HttpServletRequest request = requestWithParams("DELETE", params); 
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doDelete(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testdir.toPath())) {
			Assert.assertNotNull(recorder);
			final FendoTimeSeries ts0 = recorder.getRecordedDataStorage(id0);
			final FendoTimeSeries ts1 = recorder.getRecordedDataStorage(id1);
			Assert.assertNotNull(id0);
			Assert.assertNotNull(id1);
			Assert.assertFalse("Properties missing", ts0.getProperties().isEmpty());
			Assert.assertEquals("Unexpected properties size", 2, ts0.getProperties().size());
			final List<String> values = ts0.getProperties(propKey0);
			Assert.assertEquals("Unexpected properties size", 1, values.size());
			Assert.assertEquals("Unexpected property value",propVal0b, values.get(0));
			Assert.assertFalse(ts1.getProperties().isEmpty());
		}
		params.remove(Parameters.PARAM_PROPERTIES);
		params.put(Parameters.PARAM_TAGS, propKey0);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_TAG);
		final HttpServletRequest request2 = requestWithParams("DELETE", params); 
		final Response response2 = new Response();
		final HttpServletResponse resp2 = getResponse(response2); 
		servlet.doDelete(request2, resp2);
		verify(resp2).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testdir.toPath())) {
			Assert.assertNotNull(recorder);
			final FendoTimeSeries ts0 = recorder.getRecordedDataStorage(id0);
			final FendoTimeSeries ts1 = recorder.getRecordedDataStorage(id1);
			Assert.assertNotNull(id0);
			Assert.assertNotNull(id1);
			Assert.assertFalse("Properties missing", ts0.getProperties().isEmpty());
			Assert.assertEquals("Unexpected properties size", 1, ts0.getProperties().size());
			Assert.assertFalse("Properties missing", ts1.getProperties().isEmpty());
		}
	}
	
	@Test
	public void updateConfigurationWorks() throws DataRecorderException, IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ServletException {
		final String id0 = "test0";
		final String id1 = "test1";
		final RecordedDataConfiguration cfg0 = new RecordedDataConfiguration();
		cfg0.setStorageType(StorageType.ON_VALUE_UPDATE);
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final FendoTimeSeries ts0 = recorder.createRecordedDataStorage(id0, cfg0);
			recorder.createRecordedDataStorage(id1, cfg0);
			Assert.assertEquals(cfg0, ts0.getConfiguration());
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4,1);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_TIMESERIES);
		params.put(Parameters.PARAM_ID, id0);
		params.put(Parameters.PARAM_UPDATE_MODE, StorageType.FIXED_INTERVAL.toString());
		final long interval = 25000;
		params.put(Parameters.PARAM_INTERVAL, String.valueOf(interval)); 
		final HttpServletRequest request = requestWithParams("PUT", params); 
		final Response response = new Response();
		final HttpServletResponse resp = getResponse(response); 
		servlet.doPut(request, resp);
		verify(resp).setStatus(HttpServletResponse.SC_OK);
		try (final CloseableDataRecorder recorder = factory.getExistingInstance(testdir.toPath())) {
			Assert.assertNotNull(recorder);
			final FendoTimeSeries ts0 = recorder.getRecordedDataStorage(id0);
			final FendoTimeSeries ts1 = recorder.getRecordedDataStorage(id1);
			Assert.assertNotNull(ts0);
			Assert.assertNotNull(ts1);
			Assert.assertEquals(cfg0, ts1.getConfiguration());
			final RecordedDataConfiguration cfg1 = ts0.getConfiguration();
			Assert.assertEquals("Storage type update failed",StorageType.FIXED_INTERVAL, cfg1.getStorageType());
			Assert.assertEquals("Fixed interval update failed", interval, cfg1.getFixedInterval());
		}
	}
	
	@Test
	public void statisticsWorkJson0() throws ServletException, IOException, DataRecorderException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		final String id0 = "test";
		final List<SampledValue> values = IntStream.range(0, 50)
				.mapToObj(i -> new SampledValue(new FloatValue(i), i, Quality.GOOD))
				.collect(Collectors.toList());
		try (final CloseableDataRecorder recorder = factory.getInstance(testdir.toPath())) {
			final RecordedDataConfiguration cfg = new RecordedDataConfiguration();
			cfg.setStorageType(StorageType.ON_VALUE_UPDATE);
			final FendoTimeSeries ts0 = recorder.createRecordedDataStorage(id0, cfg);
			ts0.insertValues(values);
		}
		final RecordedDataServlet servlet = createServlet(factory);
		final Map<String,String> params = new HashMap<>(4);
		params.put(Parameters.PARAM_DB, TEST_FOLDER);
		params.put(Parameters.PARAM_TARGET, Parameters.TARGET_STATISTICS);
		params.put(Parameters.PARAM_PROVIDERS, "cnt");
		final String result = sendJsonRequest(params, servlet);
		final JSONObject json = new JSONObject(result);
		Assert.assertNotNull(json);
		final JSONObject res = json.getJSONObject("entries");
		Assert.assertTrue("Statistics missing",res.has("cnt"));
		Assert.assertEquals("Unexpected data point count", values.size(), Integer.parseInt(res.get("cnt").toString()));
	}
	
}
