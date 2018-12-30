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
document.addEventListener("DOMContentLoaded", () => {
	const initMenu = () => {
		const submenu = document.querySelector(".submenu-controls>div>div>ul.grafana-segment-list");
		if (!submenu) {
    		setTimeout(initMenu, 100);
    		return;
		}
		const search = new URLSearchParams(window.location.search);
		const newMenu = document.createElement("li");
		newMenu.classList.add("grafana-target-segment");
		newMenu.innerText = "Configuration";
		newMenu.dataset.state = "hidden";
		newMenu.style.cursor = "pointer";
		const snippet = document.createElement("div");
		snippet.classList.add("dashboard-edit-view");
		snippet.style.display = "none";
		const clfx = document.querySelector(".clearfix");
		clfx.parentNode.insertBefore(snippet, clfx.nextElementSibling);
		let initDone = false;
		newMenu.onclick = () => {
			const state = newMenu.dataset.state !== "hidden";
			snippet.style.display = state ? "none" : "block";
			newMenu.dataset.state = state ? "hidden" : "present";
			if (state || initDone)
				return;
			const db = search.has("db") ? search.get("db") : "data/slotsdb";
			fetch("/rest/fendodb?target=tags&db=" + db + "&pw=" + otpwd, {
			 	method: "GET",
		        credentials: "omit",
		        headers: {
		        	Accept: "application/json"
		        }
			}).then(response => {
				if (!response.ok)
					return Promise.reject("Request failed "  + response.status + ": " + response.statusText);
				return response.json();
			}).then(response => {
				if (initDone)
					return;
				initDone = true;
				const tags = {};
				response.entries.forEach(entry => {
					entry.tags.forEach(prop => {
						for (p in prop) {
							if (!tags.hasOwnProperty(p))
								tags[p] = [];
							prop[p]
								.filter(value => tags[p].indexOf(value) < 0)
								.forEach(value => tags[p].push(value));
						}
					});
				});
				
				const flex = document.createElement("div");
				flex.style.display = "flex";
				flex.style.justifyContent = "flex-start";
				flex.style.flexWrap = "wrap";
				const selectors = [];
				const currentProps = search.getAll("properties")
					.map(p => p.split("="));
				const propsMap ={};
				currentProps.forEach(arr => {
					if (!propsMap.hasOwnProperty(arr[0]))
						propsMap[arr[0]] = [];
					propsMap[arr[0]].push(arr[1]);
				}); 
				const allTags = Object.keys(tags).sort();
				//const maxLength = allTags.map(t => t.length).reduce((a,b) => a > b ? a : b);
				allTags.forEach(t => {
				 	const container = document.createElement("div");
				 	container.style["paddingRight"] = "3em";
				 	const fs = document.createElement("fieldset");
				 	fs.style["paddingTop"] = "1em";
				 	const label = document.createElement("label");
				 	label.innerText = "Select " + t + ":";
				 	label.title = "Hold 'Ctrl' to select multiple values or deselect a single value"
				 	//label.style["paddingRight"] = "1em";
				 	fs.appendChild(label);
				 	container.appendChild(fs);
					const select = document.createElement("select");
					select.dataset.tagselector = "true";
					select.dataset.tag = t;
					select.multiple=true;
					selectors.push(select);
					container.appendChild(select);
					/*
					const unselected = document.createElement("option");
					unselected.value = "all";
					unselected.innerText = "all";
					if (!propsMap.hasOwnProperty(t))
						unselected.selected = true;
					select.appendChild(unselected);
					*/
					const checkSelected = () => {
						const isSelected = select.selectedOptions.length > 0;
						if (isSelected) {
							if (!label.classList.contains("selectedGroup"))
								label.classList.add("selectedGroup");
						} else {
							label.classList.remove("selectedGroup");
						}
					};
					tags[t].sort().forEach(val => {
						const li = document.createElement("option");
						li.value = val;
						li.innerText = val;
						if (propsMap.hasOwnProperty(t) && propsMap[t].indexOf(val) >= 0)
							li.selected = true;
						select.appendChild(li);
					});
					select.onclick = checkSelected;
					checkSelected();
					flex.appendChild(container);
				});
				snippet.appendChild(flex);

				const timelabel =  document.createElement("span");
	    		timelabel.innerText = "Select time range";
	    		timelabel.style["marginRight"] = "1em";
	    		const timerange = document.createElement("input");
	    		timerange.type = "number";
	    		timerange.min = "1";
	    		timerange.step = "1";
	    		timerange.style["marginRight"] = "1em";
	    		timerange.value = "2";
	    		timerange.style.width = "4em";
	    		const units = document.createElement("select");
	    		const years = document.createElement("option");
	    		years.value = "y";
	    		years.innerText = "years";
	    		const months = document.createElement("option");
	    		months.value = "M";
	    		months.innerText = "months";
	    		const days = document.createElement("option");
		    	days.value = "d";
	    		days.innerText = "days";
	    		const hours = document.createElement("option");
	    		hours.value = "h";
	    		hours.innerText = "hours";
	    		const minutes = document.createElement("option");
	    		minutes.value = "m";
	    		minutes.innerText = "minutes";
	    		units.appendChild(years);
	    		units.appendChild(months);
	    		units.appendChild(days);
	    		units.appendChild(hours);
	    		units.appendChild(minutes);
	    		units.style.width="10em";
	    		units.value = "d";
	    		const timeFlex = document.createElement("div");
	    		timeFlex.style.display = "flex";
	    		timeFlex.appendChild(timelabel);
	    		timeFlex.appendChild(timerange);
	    		timeFlex.appendChild(units);
	    		
	    		if (search.has("timerange")) {
	    			const range = search.get("timerange");
	    			if (range.length > 1) {
	    				const num = parseInt(range.substring(0, range.length-1));
	    				const unit = range[range.length-1];
	    				if (!isNaN(num)) {
	    					timerange.value = num + "";
	    					units.value = unit;
	    				}
	    			}
	    		}
	    		
	    		const deselect = document.createElement("input");
				deselect.type = "button";
				deselect.value = "Deselect all";
				deselect.onclick = 
					() => {
							Array.from(snippet.querySelectorAll("select[data-tagselector]")).forEach(sel => {
							Array.from(sel.options).forEach(opt => opt.selected = false);
							sel.parentElement.querySelector("label").classList.remove("selectedGroup");
						});
					};
				const btn = document.createElement("input");
	    		btn.type = "button";
	    		btn.value = "Apply";
	    		btn.onclick = () => {
	    		    search.delete("properties");
	    		    selectors
	    		    	.filter(s => s.selectedOptions.length > 0)
	    		    	.forEach(s => {
	    		    		const sels = Array.from(s.selectedOptions).map(o => o.value);
	    		    		sels.forEach(sel => search.append("properties",s.dataset.tag + "=" + sel));
	    		    	});
	    		    const t0 = Number.parseInt(timerange.value);
	    		    const tunit = units.value;
	    		    if (!isNaN(t0)) {
	    		    	search.set("timerange", t0 + tunit);
	    		    }
	    		    window.location.search = search.toString();
	    		    /*
	    		    window.location.search = search.toString(); // reloads page
	    		    */
	    		};
	    		
	    		const buttonsFlex = document.createElement("div");
	    		buttonsFlex.style.display = "flex";
	    		buttonsFlex.appendChild(deselect);
	    		buttonsFlex.appendChild(btn);
	    		deselect.style["marginRight"] = "1em";
	    		
	    		snippet.appendChild(timeFlex);	
	    		snippet.appendChild(buttonsFlex);					
			});
	    			
		};
		submenu.appendChild(newMenu);
	};
	initMenu();
});