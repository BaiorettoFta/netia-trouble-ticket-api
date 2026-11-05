package pl.netia.troubleticket.support;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.yaml.snakeyaml.Yaml;

/**
 * The OpenAPI contract read as a plain YAML tree, for tests that compare the application with the contract itself.
 * Schema checks are left to {@link ContractValidator}.
 */
public final class ContractDocument {

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final Map<String, Object> ROOT = load();

    private ContractDocument() {
    }

    /**
     * Every operation as {@code METHOD path}, with the base path taken from the first server and the default values
     * of its variables, for example {@code PATCH /api/v1/troubleTicket/{id}}.
     */
    public static Set<String> operations() {
        String basePath = basePath();
        Set<String> operations = new TreeSet<>();
        child(ROOT, "paths").forEach((path, item) -> asMap(item).keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> operations.add(method.toUpperCase(Locale.ROOT) + " " + basePath + path)));
        return operations;
    }

    private static String basePath() {
        Map<String, Object> server = asMap(((List<?>) ROOT.get("servers")).getFirst());
        String url = (String) server.get("url");
        for (Map.Entry<String, Object> variable : child(server, "variables").entrySet()) {
            url = url.replace("{" + variable.getKey() + "}", (String) asMap(variable.getValue()).get("default"));
        }
        return url;
    }

    private static Map<String, Object> child(Map<String, Object> node, String key) {
        return asMap(node.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }

    private static Map<String, Object> load() {
        try (Reader reader = Files.newBufferedReader(ContractValidator.CONTRACT)) {
            return new Yaml().load(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
