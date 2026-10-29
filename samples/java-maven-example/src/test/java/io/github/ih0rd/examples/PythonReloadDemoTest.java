package io.github.ih0rd.examples;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.spi.FileSystemScriptSource;
import io.github.ih0rd.examples.contracts.ShippingPolicy;
import io.github.ih0rd.polyglot.Convention;

class PythonReloadDemoTest {
    @TempDir Path workspace;

    @Test
    void defaultClientUsesNewPolicyAfterExplicitReload() throws IOException {
        verifyReload(Convention.DEFAULT);
    }

    @Test
    void methodNameClientUsesNewPolicyAfterExplicitReload() throws IOException {
        verifyReload(Convention.BY_METHOD_NAME);
    }

    @Test
    void completeDemoRunsWithPackagedPolicyResources() {
        assertDoesNotThrow(() -> new PythonReloadDemo().run(workspace));
    }

    private void verifyReload(Convention convention) throws IOException {
        Path module = Files.createDirectory(workspace.resolve("python")).resolve("shipping_policy.py");
        copyPolicy("initial", module);
        try (PyExecutor executor = PyExecutor.create(new FileSystemScriptSource(workspace), null)) {
            ShippingPolicy client = executor.bind(ShippingPolicy.class, convention);
            assertEquals(500, client.quote(8_000));
            assertEquals(0, client.quote(10_000));

            copyPolicy("updated", module);
            assertEquals(500, client.quote(8_000));
            executor.reloadContract(ShippingPolicy.class, convention);
            assertEquals(0, client.quote(8_000));
            assertEquals(350, client.quote(5_000));

            copyPolicy("incompatible", module);
            BindingException failure = assertThrows(BindingException.class,
                    () -> executor.reloadContract(ShippingPolicy.class, convention));
            assertTrue(failure.getMessage().contains("shipping_policy"));
            assertEquals(0, client.quote(8_000));
            assertEquals(350, client.quote(5_000));

            copyPolicy("updated", module);
            executor.reloadContract(ShippingPolicy.class, convention);
            assertEquals(350, client.quote(5_000));
        }
    }

    private void copyPolicy(String version, Path module) throws IOException {
        try (var source = getClass().getResourceAsStream("/reload/" + version + "/shipping_policy.py")) {
            if (source == null) throw new IOException("Missing policy fixture: " + version);
            Files.copy(source, module, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
