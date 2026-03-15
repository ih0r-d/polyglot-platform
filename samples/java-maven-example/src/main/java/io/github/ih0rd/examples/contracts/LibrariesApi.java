package io.github.ih0rd.examples.contracts;

import java.util.Map;


import java.util.List;

public interface LibrariesApi {

    List<Map<String, Object>> genUsers(int n);

    String formatUsers(int n);

    String fakeParagraphs(int n);
}
