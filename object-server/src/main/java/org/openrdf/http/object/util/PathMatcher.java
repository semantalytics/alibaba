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

public class PathMatcher {
	private static Map<String, Pattern> patterns = Collections
			.synchronizedMap(new WeakHashMap<String, Pattern>());

	public static Pattern compile(String regex) {
		Pattern result = patterns.get(regex);
		if (result != null)
			return result;
		try {
			patterns.put(regex, result = Pattern.compile(regex));
		} catch (PatternSyntaxException e) {
			patterns.put(regex,
					result = Pattern.compile(regex, Pattern.LITERAL));
		}
		return result;
	}

	private static final Pattern NAMED_GROUP_PATTERN = Pattern
			.compile("\\(\\?<(\\w+)>");

	private CharSequence url;
	private int start;

	public PathMatcher(CharSequence url, int start) {
		this.url = url;
		this.start = start;
	}

	public boolean matches(String regex) {
		return matches(compile(regex));
	}

	public boolean matches(Pattern pattern) {
		return startingAt(pattern.matcher(url));
	}

	public Map<String, String> match(String regex) {
		return match(compile(regex));
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
		Pattern pattern = m.pattern();
		if ((pattern.flags() & Pattern.LITERAL) == 0) {
			Matcher matcher = NAMED_GROUP_PATTERN.matcher(pattern.pattern());
			while (matcher.find()) {
				groupNames.add(matcher.group(1));
			}
		}
		return groupNames;
	}
}
