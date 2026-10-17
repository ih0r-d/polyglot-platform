interface SignatureApi {
    String required(String name);
    String optional();
    String optional(String name);
    String mixed(String name);
    String mixed(String name, int count);
}
