package io.github.ih0rd.examples.contracts;

import java.util.List;
import java.util.Map;

public interface SimplexSolver{
    Map<String, Object> runSimplex(List<List<Integer>> aInput, List<Integer> bInput,
                                    List<Integer> cInput, String prob, Object ineq,
                                    boolean enableMsg, boolean latex);
}
