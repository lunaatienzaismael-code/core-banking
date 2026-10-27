package com.ledgerflow.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;

/**
 * BACKWARD compatibility as Schema Registry defines it (ADR 0013): a consumer on the newer schema can read data written
 * with the older one, i.e. the newer schema is the reader and the older the writer.
 */
final class BackwardCompatibility {

    private BackwardCompatibility() {}

    /** Why {@code newer} cannot read data written with {@code older}; empty when it can. */
    static List<String> problems(Schema newer, Schema older) {
        return SchemaCompatibility.checkReaderWriterCompatibility(newer, older)
                .getResult()
                .getIncompatibilities()
                .stream()
                .map(problem -> problem.getType() + " at " + problem.getLocation() + ": " + problem.getMessage())
                .toList();
    }

    /**
     * Problems of a whole version against the released one, by full name: each released message must still exist and
     * be readable by its current schema. A message absent from the release is new and has nothing to break.
     */
    static List<String> problems(Map<String, Schema> current, Map<String, Schema> released) {
        var problems = new ArrayList<String>();
        released.forEach((name, older) -> {
            var newer = current.get(name);
            if (newer == null) {
                problems.add(name + ": removed or renamed; consumers of the released schema lose it");
            } else {
                problems(newer, older).forEach(problem -> problems.add(name + ": " + problem));
            }
        });
        return problems.stream().sorted().toList();
    }

    /**
     * Parses one version of the contracts (a set of {@code .avsc} sources) into its named schemas by full name. Enums
     * are parsed first because records refer to them by name.
     */
    static Map<String, Schema> parse(Collection<String> sources) {
        var ordered = new ArrayList<>(sources);
        ordered.sort(Comparator.comparing(source -> isEnum(source) ? 0 : 1));
        var parser = new Schema.Parser();
        return ordered.stream().map(parser::parse).collect(Collectors.toMap(Schema::getFullName, Function.identity()));
    }

    private static boolean isEnum(String source) {
        return source.matches("(?s)\\s*\\{\\s*\"type\"\\s*:\\s*\"enum\".*");
    }
}
