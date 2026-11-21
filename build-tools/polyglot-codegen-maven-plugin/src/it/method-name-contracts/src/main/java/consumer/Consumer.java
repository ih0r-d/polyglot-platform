package consumer;
public final class Consumer {
    public String invoke(GreetingApi names, ExportApi exports) {
        return names.hello("Ada") + names.hello("Ada", 2) + exports.hello("Ada");
    }
}
