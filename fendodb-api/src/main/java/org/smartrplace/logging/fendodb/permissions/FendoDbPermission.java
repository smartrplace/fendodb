/**
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
package org.smartrplace.logging.fendodb.permissions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read, write and admin permission for SlotsDb database instances.
 */
public class FendoDbPermission extends Permission {

	private static final long serialVersionUID = 1L;
	private final static String WILDCARD = "*";
	private final Path path;
	private final boolean pathHasWildcard;
	// null means all actions are permitted
	private final List<String> actions;
	// this being 3 means all permissions
	private final int nrActions;
	private final String actionsString;
	public final static String READ = "read";
	public final static String WRITE = "write";
	public final static String ADMIN = "admin";
	private final static List<String> ALL_ACTIONS = Collections.unmodifiableList(
			Arrays.asList(READ, WRITE, ADMIN)
	);
	
	public FendoDbPermission(String path, String actions) {
		this(path.trim().isEmpty() ? "empty" : path.trim(), path, actions);
	}
	
	public FendoDbPermission(String name, String path, String actions) {
		super(name);
		path = Objects.requireNonNull(path).trim();
		actions = Objects.requireNonNull(actions).trim();
		this.pathHasWildcard = path.endsWith("*");
		if (pathHasWildcard)
			path = path.substring(0, path.length()-1);
		this.path = path.isEmpty() ? null : Paths.get(path).normalize();
		if (actions.equals(WILDCARD)) {
			this.actions = ALL_ACTIONS;
		} else {
			this.actions = Collections.unmodifiableList(Arrays.stream(actions.split(","))
				.map(action -> action.trim().toLowerCase())
				.filter(action -> !action.isEmpty())
				.collect(Collectors.toList()));
			if (this.actions.stream().filter(action -> !ALL_ACTIONS.contains(action)).findAny().isPresent())
				throw new IllegalArgumentException("Invalid actions string: " + actions + ". Only 'read', 'write' and 'admin' permitted.");
		}
		this.nrActions = this.actions == null ? ALL_ACTIONS.size() : this.actions.size();
		this.actionsString = ALL_ACTIONS.stream()
			.filter(action -> hasAction(action))
			.collect(Collectors.joining(","));
	}
	
	private final boolean hasAction(final String action) {
		return nrActions == 3 || this.actions.contains(action);
	}

	@Override
	public boolean implies(Permission permission) {
		if (!(permission instanceof FendoDbPermission))
			return false;
		final FendoDbPermission other = (FendoDbPermission) permission;
		if (!this.pathHasWildcard && !equals(other.path, this.path))
			return false;
		if (this.pathHasWildcard && !startsWith(other.path, this.path))
			return false;
		if (this.nrActions == 3) // all actions
			return true;
		if (this.nrActions < other.nrActions)
			return false;
		// here both this.actions and other.actions must be non-null
		return !other.actions.stream().filter(action -> !this.actions.contains(action)).findAny().isPresent();
	}

	private static boolean equals(final Path path, final Path potentialMatch) {
		return Objects.equals(path, potentialMatch);
 	}
	
	private static boolean startsWith(final Path path, final Path potentialParent) {
		if (potentialParent == null)
			return true;
		if (path == null)
			return false;
		return path.startsWith(potentialParent);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof FendoDbPermission))
			return false;
		final FendoDbPermission other = (FendoDbPermission) obj;
		if (this.pathHasWildcard != other.pathHasWildcard)
			return false;
		if (!Objects.equals(this.path,other.path))
			return false;
		if (this.nrActions != other.nrActions)
			return false;
		if (this.nrActions != 3 && this.actions.stream().filter(action -> !other.actions.contains(action)).findAny().isPresent())
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, actions);
	}

	@Override
	public String getActions() {
		return actionsString;
	}
}
