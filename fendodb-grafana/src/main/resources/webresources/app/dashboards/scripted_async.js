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
		// TODO report
		console.log("Config parameter missing");
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
						const db = filter.db;
						const searchParams = new URLSearchParams;
						searchParams.set("target", "find");
						searchParams.set("pw", otpwd);
						
						Object.keys(filter)
							.filter(key => fendoFilters.indexOf(key) >= 0)
							.forEach(key => Array.isArray(filter[key]) ? filter[key].forEach(v => searchParams.append(key,v)) 
									: searchParams.append(key, filter[key]));
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
										newTarget.function = target.hasOwnProperty("name") ? target.name : db;
										if (target.hasOwnProperty("labelpattern")) 
											newTarget.function = newTarget.function + "|" + target.labelpattern;
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
