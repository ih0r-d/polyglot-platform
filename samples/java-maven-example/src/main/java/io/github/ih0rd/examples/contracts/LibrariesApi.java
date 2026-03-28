package io.github.ih0rd.examples.contracts;

import java.util.List;
import java.util.Map;

public interface LibrariesApi {

    List<Map<String, Object>> genUsers(int n);

    String formatUsers(int n);

    String fakeParagraphs(int n);
}
