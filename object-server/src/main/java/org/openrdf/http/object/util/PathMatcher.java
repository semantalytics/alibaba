package org.openrdf.http.object.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.openrdf.annotations.Path;

public class PathMatcher {
	private static Map<Path, Pattern[]> patterns = Collections
			.synchronizedMap(new WeakHashMap<Path, Pattern[]>());

	public static Pattern[] compile(Path path) {
		Pattern[] result = patterns.get(path);
		if (result == null) {
			result = new Pattern[path.value().length];
			int i = 0;
			for (String p : path.value()) {
				result[i++] = compile(p);
			}
			patterns.put(path, result);
		}
		return result;
	}

	public static Pattern compile(String regex) {
		try {
			return Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			return Pattern.compile(regex, Pattern.LITERAL);
		}
	}

	private static final Pattern NAMED_GROUP_PATTERN = Pattern
			.compile("\\(\\?<(\\w+)>");

	private String url;
	private int start;

	public PathMatcher(String url, int start) {
		this.url = url;
		this.start = start;
	}

	public boolean matches(String regex) {
		return startingAt(compile(regex).matcher(url));
	}

	public boolean matches(Pattern pattern) {
		return startingAt(pattern.matcher(url));
	}

	public Map<String, String> match(String regex) {
		return groups(compile(regex).matcher(url));
	}

	public Map<String, String> match(Pattern pattern) {
		return groups(pattern.matcher(url));
	}

	private boolean startingAt(Matcher m) {
		return m.find(start) && m.start() == start;
	}

	private Map<String, String> groups(Matcher m) {
		if (!startingAt(m))
			return null;
		int n = m.groupCount();
		Map<String, String> map = new LinkedHashMap<String, String>(n * 2);
		for (int i = 0; i <= n; i++) {
			map.put(Integer.toString(i), m.group(i));
		}
		for (String name : extractGroupNames(m)) {
			map.put(name, m.group(name));
		}
		return map;
	}

	private List<String> extractGroupNames(Matcher m) {
		List<String> groupNames = new ArrayList<String>();
		Matcher matcher = NAMED_GROUP_PATTERN.matcher(m.pattern().pattern());
		while (matcher.find()) {
			groupNames.add(matcher.group(1));
		}
		return groupNames;
	}
}