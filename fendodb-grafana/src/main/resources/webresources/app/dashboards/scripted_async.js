/*
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
/* global _ */

/*
 * Complex scripted dashboard
 * This script generates a dashboard object that Grafana can load. It also takes a number of user
 * supplied URL parameters (int ARGS variable)
 *
 * Global accessible variables
 * window, document, $, jQuery, ARGS, moment
 *
 * Return a dashboard object, or a function
 *
 * For async scripts, return a function, this function must take a single callback function,
 * call this function with the dasboard object
 */



// accessible variables in this scope
//var window, document, ARGS, $, jQuery, moment, kbn;

// TODO support multiple label patterns, depending on which tags are present! 
return function(callback) {
	const search = new URLSearchParams(window.location.search);
	if (!search.has("config")) {
		if (!search.has("db"))
			search.set("db", "data/slotsdb");
		fetch("/org/smartrplace/tools/app/auth?user=" + otusr + "&pw=" + otpwd, {
			 method: "GET",
			 credentials: "same-origin",
	         headers: {
	             Accept: "text/plain"
	         }
		}).then(response => {
			if (!response.ok)
				return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
			return response.text();
		}).then(tokenResponse => {
			otpwd = tokenResponse; // fendo REST servlet is not accessible with standard otp, instead we need to use the token
			otp_uri_ext = "user=" + otusr + "&pw=" + otpwd; 
			search.set("target", "find");
			search.set("pw", otpwd);
			return fetch("/rest/fendodb?" + search.toString(), {
			 	method: "GET",
		        credentials: "omit",
		        headers: {
		        	Accept: "text/plain"
		        }
			});
		}).then(response => {
			if (!response.ok)
				return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
			return response.text();
		}).then(response => {
			const dashboard = {
					  "id": null,
					  "title": "Room climate data",
					  "originalTitle": "Room climate data",
					  "tags": [],
					  "style": "dark",
					  "timezone": "browser",
					  "editable": true,
					  "hideControls": false,
					  "sharedCrosshair": false,
					  "rows": [
					  ],
					  "nav": [
					    {
					      "type": "timepicker",
					      "collapse": false,
					      "notice": false,
					      "enable": true,
					      "status": "Stable",
					      "time_options": [
					        "5m",
					        "15m",
					        "1h",
					        "6h",
					        "12h",
					        "24h",
					        "2d",
					        "7d",
					        "30d",
							"1y"
					      ],
					      "refresh_intervals": [
					        "5s",
					        "10s",
					        "30s",
					        "1m",
					        "5m",
					        "15m",
					        "30m",
					        "1h",
					        "2h",
					        "1d"
					      ],
					      "now": false
					    }
					  ],
					  "time": {
					    "from": "now-2d",
					    "to": "now"
					  },
					  "templating": {
					    "list": [],
					    "enable": true
					  },
					  "annotations": {
					    "enable": false
					  },
					  "version": 6,
					  "hideAllLegends": false
					};
			const timerange = search.get("timerange");
			if (timerange)
				dashboard.time.from = "now-" + timerange;
			const db = search.get("db");
			const tags = {};
			const tagsPromises = response.trim().split(/\s+/)
				.filter(id => id.length > 0)
				.map(id => fetch("/rest/fendodb?target=tags&db=" + db + "&id=" + id + "&pw=" + otpwd, {
					 	method: "GET",
				        credentials: "omit",
				        headers: {
				        	Accept: "application/json"
				        }
					}).then(r => {
						if (!r.ok)
							return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
						return r.json();
					}).then(json => {
						const tagsArray = json.entries[0].tags;
						const devType = tagsArray.find(obj => Object.keys(obj).indexOf("deviceTypeSpecific") >= 0);
						if (!devType || devType.deviceTypeSpecific.length === 0) {
//						if (!tagsArray.hasOwnProperty("deviceTypeSpecific") || tagsArray.deviceTypeSpecific.length === 0) {
							if (!tags.hasOwnProperty("Miscellaneous")) 
								tags.Miscellaneous = [];
							tags.Miscellaneous.push(id);
							return;
						}
						const sensorTag = devType.deviceTypeSpecific.find(tp => tp.indexOf("Sensor") >= 0);
						const t = sensorTag ? sensorTag.substring(0, sensorTag.indexOf("Sensor")) : devType.deviceTypeSpecific[0];
						if (!tags.hasOwnProperty(t))
							tags[t] = [];
						tags[t].push(id);
					})
				);
			return Promise.all(tagsPromises).then(() => {
				Object.keys(tags).forEach(tag => { // tag = {id : [properties objects]}
					const row = {
					      "title": tag,
					      "height": "500px",
					      "editable": true,
					      "panels": [
					        {
					          "title": tag,
					          "type": "graph",
					          "id": 1,
					          "span": 12,
					          "editable": true,
					          "fill": 2,
					          "scale": 2,
					          "y_formats": [
					            "short",
					            "short"
					          ],
					          "points": false,
					          "pointradius": 3,
					          "linewidth": 2,
					          "lines": true,
					          "bars": false,
					          "targets": [],
					          "steppedLine": true,
					          "datasource": "influxdb",
					          "tooltip": {
					            "shared": false,
					            "value_type": "cumulative"
					          },
					          "renderer": "flot",
					          "x-axis": true,
					          "y-axis": true,
					          "grid": {
					            "leftMax": null,
					            "rightMax": null,
					            "leftMin": null,
					            "rightMin": null,
					            "threshold1": null,
					            "threshold2": null,
					            "threshold1Color": "rgba(216, 200, 27, 0.27)",
					            "threshold2Color": "rgba(234, 112, 112, 0.22)"
					          },
					          "stack": false,
					          "percentage": false,
					          "legend": {
					            "show": true,
					            "values": false,
					            "min": false,
					            "max": false,
					            "current": false,
					            "total": false,
					            "avg": false
					          },
					          "nullPointMode": "connected",
					          "aliasColors": {},
					          "seriesOverrides": [],
					          "leftYAxisLabel":""
					        }
					      ],
					      "collapse": false
					    };
					
					tags[tag].forEach(id => {
						let isPowerInfo = false;
						const target = {
				        	  "column": "value",
				        	  "target": "mean('" + db + ":" + id + "')",
				        	  "series": db + ":" + id,
				        	  "function": "{$roomName}|{$roomPath}|" + tag + "||{$deviceName}|{$timeseries}"
							};
						if (tag === "Temperature") {
							target.column = target.column + "-273.15";
							row.panels[0].leftYAxisLabel = "°C";
						}
						else if (tag === "Humidity") {
							target.column = target.column + "*100";
							row.panels[0].leftYAxisLabel = "%";
						}
						else if (tag.indexOf("Power") >= 0) {
							row.panels[0].leftYAxisLabel = "W";
							isPowerInfo = true;
						}
						else if (tag.indexOf("Energy") >= 0) {
							row.panels[0].leftYAxisLabel = "J";
						}
						else if (tag === "ElectricCurrent") {
							row.panels[0].leftYAxisLabel = "A";
							isPowerInfo = true;
						}
						else if (tag === "ElectricVoltage") {
							row.panels[0].leftYAxisLabel = "V";
							isPowerInfo = true;
						}
						if (isPowerInfo) {
							target["function"] ="{$roomName}|{$roomPath}|" + tag + "||{$deviceName}_{$phase}|{$timeseries}"; 
						}
						row.panels[0].targets.push(target);
					});
					dashboard.rows.push(row);
				});
				return dashboard;
			});
		}).then(callback);
		return;
	}
	let token = null;
	const config = search.get("config");
	fetch("/org/smartrplace/tools/app/auth?user=" + otusr + "&pw=" + otpwd, {
		 method: "GET",
		 credentials: "same-origin",
         headers: {
             Accept: "text/plain"
         }
	}).then(response => {
		if (!response.ok)
			return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
		return response.text();
	}).then(tokenResponse => {
		token = tokenResponse;
		return fetch("/org/smartrplace/logging/fendodb/grafana/servlet?target=config&config=" + config + "&user=" + otusr + "&pw=" + otpwd, {
	        method: "GET",
	        credentials: "same-origin",
	        headers: {
	        	Accept: "application/json"
	        }
		})
	}).then(response => {
		if (!response.ok)
			return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
		return response.json();
	}).then(dashboardJson => {
		// when dashboard is composed call the callback function and pass the dashboard
		otpwd = token; // fendo REST servlet is not accessible with standard otp, instead we need to use the token
		otp_uri_ext = "user=" + otusr + "&pw=" + otpwd; 
		console.log("Dashboard", dashboardJson);
		const fendoFilters = ["db", "tags", "properties", "id", "idexcluded"];
		const promises = [];
		dashboardJson.rows.forEach(row => {
			row.panels.forEach(panel => {
				panel.targets.forEach(target => {
					if (target.type === "fendodb" && target.hasOwnProperty("db") && target.hasOwnProperty("timeseries")) {
						target.target = target.db + ":" + target.timeseries;
						target.series = target.target;
						target.column = "value";
						delete target.type;
						delete target.db;
						delete target.timeseries;
					}
					else if (target.type === "fendodb" && target.hasOwnProperty("filter")) {
						const filter = target.filter;
						if (!filter.hasOwnProperty("db")) {
							console.error("Filter is lacking property 'db': ", filter);
							return;
						}
						const replaceVars = string => {
							let lastEnd = -1;
							b = "";
							while (lastEnd < string.length - 1) {
								const idx0 = string.indexOf("{$", lastEnd + 1);
								if (idx0 < 0)
									break;
								const idx1 = string.indexOf("}", idx0);
								if (idx1 < 0)
									break;
								const variable = string.substring(idx0+2, idx1);
								const val = search.getAll(variable);
								if (val.length === 0) {
									console.log("Missing page parameter ", variable);
								} else {
									b = b + string.substring(lastEnd + 1, idx0) + val[0];
								}
								lastEnd = idx1;
							}
							if (lastEnd < string.length-1)
								b = b + string.substring(lastEnd + 1);
							return b;
						};
						const db = replaceVars(filter.db);
						const searchParams = new URLSearchParams;
						searchParams.set("target", "find");
						searchParams.set("pw", otpwd);
						Object.keys(filter)
							.filter(key => fendoFilters.indexOf(key) >= 0)
							.forEach(key => Array.isArray(filter[key]) ? filter[key].forEach(v => searchParams.append(key,replaceVars(v))) 
									: searchParams.append(key, replaceVars(filter[key])));
						const promise = fetch("/rest/fendodb?" + searchParams.toString(), {
							 	method: "GET",
						        credentials: "omit",
						        headers: {
						        	Accept: "text/plain"
						        }
							}).then(response => {
								if (!response.ok)
									throw Error("Request failed: ", response.status, ":", response.statusText);
								return response.text();
							}).then(list => {
								list.split(/\r?\n/)
									.map(str => str.trim())
									.filter(str => str.length > 0)
									.forEach(str => {
										const newTarget = {};
										newTarget.target = db + ":" + str;
										newTarget.series = newTarget.target;
										newTarget.column = "value";
										if (target.hasOwnProperty("factor"))
											newTarget.column = newTarget.column + "*" + target.factor;
										if (target.hasOwnProperty("offset")) {
											if (target.offset >= 0)
												newTarget.column = newTarget.column + "+";
											newTarget.column = newTarget.column + target.offset;
										}
										newTarget.function = target.hasOwnProperty("labelprimary") ? target.labelprimary : db;
										if (target.hasOwnProperty("labelsecondary")) 
											newTarget.function = newTarget.function + "||" + target.labelsecondary;
										panel.targets.push(newTarget);
									});
								delete target; // FIXME during iteration... better delete this afterwards!
							});
						promises.push(promise);
					}
				});
			});
		});
		return Promise.all(promises).then(() => dashboardJson);
    })
    .then(callback)
    .catch(err => console.error("Failed to load dashboard",err)); // TODO display error
}
