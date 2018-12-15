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
				currentProps.forEach(arr => propsMap[arr[0]] = arr[1]); 
				const allTags = Object.keys(tags).sort();
				//const maxLength = allTags.map(t => t.length).reduce((a,b) => a > b ? a : b);
				allTags.forEach(t => {
				 	const container = document.createElement("div");
				 	container.style["paddingRight"] = "3em";
				 	const label = document.createElement("span");
				 	const length = t.length;
				 	label.innerText = "Select " + t + ":";
				 	label.style["paddingRight"] = "1em";
				 	container.appendChild(label);
					const select = document.createElement("select");
					select.dataset.tag = t;
					selectors.push(select);
					container.appendChild(select);
					const unselected = document.createElement("option");
					unselected.value = "all";
					unselected.innerText = "all";
					if (!propsMap.hasOwnProperty(t))
						unselected.selected = true;
					select.appendChild(unselected);
					tags[t].sort().forEach(val => {
						const li = document.createElement("option");
						li.value = val;
						li.innerText = val;
						if (propsMap.hasOwnProperty(t) && propsMap[t] === val)
							li.selected = true;
						select.appendChild(li);
					});
					flex.appendChild(container);
				});
				snippet.appendChild(flex);
				const btn = document.createElement("input");
	    		btn.type = "button";
	    		btn.value = "Apply";
	    		btn.onclick = () => {
	    		    search.delete("properties");
	    		    selectors.filter(s => s.selectedOptions[0].value !== "all").forEach(s => search.append("properties",s.dataset.tag + "=" + s.selectedOptions[0].value));
	    		    window.location.search = search.toString();
	    		    /*
	    		    window.location.search = search.toString(); // reloads page
	    		    */
	    		};
	    		snippet.appendChild(btn);						
			});
	    			
		};
		submenu.appendChild(newMenu);
	};
	initMenu();
});