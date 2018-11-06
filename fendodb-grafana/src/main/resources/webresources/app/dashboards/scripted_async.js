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

return function(callback) {
	const search = new URLSearchParams(window.location.search);
	if (!search.has("config")) {
		// TODO report
		console.log("Config parameter missing");
		return;
	}
	const config = search.get("config");
	$.ajax({
        method: "GET",
	    url: "/org/smartrplace/tools/app/auth?user=" + otusr + "&pw=" + otpwd,
	    Accept: "text/plain"
	}).done(token => {
		$.ajax({
	        method: "GET",
		    url: "/org/smartrplace/logging/fendodb/grafana/servlet?target=config&config=" + config + "&user=" + otusr + "&pw=" + otpwd,
		    contentType: "application/json"
		})
		.done(dashboardJson => {
			// when dashboard is composed call the callback function and pass the dashboard
			otpwd = token; // fendo REST servlet is not accessible with standard otp, instead we need to use the token
			otp_uri_ext = "user=" + otusr + "&pw=" + otpwd; 
			console.log("Dashboard", dashboardJson);
			callback(dashboardJson);
	    });
	});

}
