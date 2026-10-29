package io.github.ih0rd.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.spi.FileSystemScriptSource;
import io.github.ih0rd.examples.contracts.ShippingPolicy;

/** Reloads a filesystem-backed shipping policy without replacing its Java client. */
public final class PythonReloadDemo {
    private static final System.Logger LOG = System.getLogger(PythonReloadDemo.class.getName());
    private static final String MODULE_FILE = "shipping_policy.py";
    private static final int ORDER_SUBTOTAL_CENTS = 8_000;

    public static void main(String[] args) throws IOException {
        Path workspace = Files.createTempDirectory("polyglot-reload-");
        try {
            new PythonReloadDemo().run(workspace);
        } finally {
            Files.deleteIfExists(workspace.resolve("python").resolve(MODULE_FILE));
            Files.deleteIfExists(workspace.resolve("python"));
            Files.deleteIfExists(workspace);
        }
    }

    void run(Path workspace) throws IOException {
        Path module = Files.createDirectories(workspace.resolve("python")).resolve(MODULE_FILE);
        installModule("initial", module);
        try (PyExecutor executor = PyExecutor.create(new FileSystemScriptSource(workspace), null)) {
            ShippingPolicy client = executor.bind(ShippingPolicy.class);
            logQuote("Initial policy", client);

            installModule("updated", module);
            logQuote("File replaced, before explicit reload", client);

            executor.reloadContract(ShippingPolicy.class);
            logQuote("After reload, same Java client", client);

            demonstrateRejectedReplacement(executor, client, module);
        }
    }

    private void demonstrateRejectedReplacement(
            PyExecutor executor, ShippingPolicy client, Path module) throws IOException {
        installModule("incompatible", module);
        try {
            executor.reloadContract(ShippingPolicy.class);
        } catch (BindingException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Shipping policy validation failed", failure);
            LOG.log(System.Logger.Level.WARNING,
                    "Replacement rejected; known bindings restored. Arbitrary Python side effects are not rolled back.");
        }
        logQuote("After rejected replacement, same Java client", client);
    }

    private void installModule(String version, Path module) throws IOException {
        String resource = "/reload/" + version + "/" + MODULE_FILE;
        try (var source = PythonReloadDemo.class.getResourceAsStream(resource)) {
            if (source == null) {
                throw new IOException("Missing reload demo resource: " + resource);
            }
            Files.copy(source, module, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void logQuote(String stage, ShippingPolicy client) {
        LOG.log(System.Logger.Level.INFO,
                "{0}: subtotal={1} cents, shipping={2} cents",
                stage, ORDER_SUBTOTAL_CENTS, client.quote(ORDER_SUBTOTAL_CENTS));
    }
}
