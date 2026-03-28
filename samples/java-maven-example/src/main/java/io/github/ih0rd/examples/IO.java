package io.github.ih0rd.examples;

final class IO {

    private static final System.Logger LOGGER = System.getLogger(IO.class.getName());

    private IO() {}

    static void println(String message) {
        LOGGER.log(System.Logger.Level.INFO, message);
    }
}
