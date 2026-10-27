package com.ledgerflow.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaCompatibilityTest {

    private static final Path SCHEMAS = Path.of("src", "main", "avro");

    @Test
    @DisplayName("B3 every schema is backward compatible with its version at the last release tag")
    void everySchemaIsBackwardCompatibleWithTheLastRelease() throws IOException {
        var release = LastRelease.of(Path.of("").toAbsolutePath());
        var files = schemaFiles();
        var current = BackwardCompatibility.parse(read(files));
        var released = BackwardCompatibility.parse(release.list(SCHEMAS).stream()
                .filter(file -> file.toString().endsWith(".avsc"))
                .map(release::read)
                .flatMap(Optional::stream)
                .toList());

        assertThat(BackwardCompatibility.problems(current, released)).isEmpty();
    }

    @Test
    @DisplayName("B3 a schema that did not exist at the last release has nothing to break")
    void aNewSchemaHasNothingToBreak() {
        var schema = record("""
                {"name": "paymentId", "type": "string"}""");

        assertThat(BackwardCompatibility.problems(Map.of("test.Sample", schema), Map.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("B3 removing or renaming a released schema is caught: its consumers lose it")
    void removingAReleasedSchemaIsCaught() {
        var schema = record("""
                {"name": "paymentId", "type": "string"}""");

        assertThat(BackwardCompatibility.problems(Map.of(), Map.of("test.Sample", schema)))
                .singleElement()
                .asString()
                .contains("test.Sample")
                .contains("removed");
    }

    @Test
    @DisplayName("B3 an incompatible change is reported with the message name")
    void anIncompatibleChangeIsReportedWithTheMessageName() {
        var older = record("""
                {"name": "paymentId", "type": "string"}""");
        var newer = record("""
                {"name": "paymentId", "type": "string"},
                {"name": "channel", "type": "string"}""");

        assertThat(BackwardCompatibility.problems(Map.of("test.Sample", newer), Map.of("test.Sample", older)))
                .singleElement()
                .asString()
                .startsWith("test.Sample: READER_FIELD_MISSING_DEFAULT_VALUE");
    }

    @Test
    @DisplayName("B3 adding a field without a default is caught")
    void addingAFieldWithoutADefaultIsCaught() {
        var older = record("""
                {"name": "paymentId", "type": "string"}""");
        var newer = record("""
                {"name": "paymentId", "type": "string"},
                {"name": "channel", "type": "string"}""");

        assertThat(BackwardCompatibility.problems(newer, older))
                .singleElement()
                .asString()
                .contains("READER_FIELD_MISSING_DEFAULT_VALUE")
                .contains("channel");
    }

    @Test
    @DisplayName("B3 adding a field with a default is compatible")
    void addingAFieldWithADefaultIsCompatible() {
        var older = record("""
                {"name": "paymentId", "type": "string"}""");
        var newer = record("""
                {"name": "paymentId", "type": "string"},
                {"name": "channel", "type": "string", "default": "API"}""");

        assertThat(BackwardCompatibility.problems(newer, older)).isEmpty();
    }

    @Test
    @DisplayName("B3 removing a field is compatible: the new reader ignores it")
    void removingAFieldIsCompatible() {
        var older = record("""
                {"name": "paymentId", "type": "string"},
                {"name": "channel", "type": "string"}""");
        var newer = record("""
                {"name": "paymentId", "type": "string"}""");

        assertThat(BackwardCompatibility.problems(newer, older)).isEmpty();
    }

    @Test
    @DisplayName("B3 changing a field to a type that cannot read the old one is caught")
    void changingAFieldTypeIsCaught() {
        var older = record("""
                {"name": "amountMinorUnits", "type": "long"}""");
        var newer = record("""
                {"name": "amountMinorUnits", "type": "int"}""");

        assertThat(BackwardCompatibility.problems(newer, older))
                .singleElement()
                .asString()
                .contains("TYPE_MISMATCH at /fields/0/type")
                .contains("INT not compatible with writer type: LONG");
    }

    @Test
    @DisplayName("B3 removing an enum symbol is caught: old data may carry it")
    void removingAnEnumSymbolIsCaught() {
        var older = enumOf("\"INSUFFICIENT_FUNDS\", \"UNKNOWN_ACCOUNT\"");
        var newer = enumOf("\"INSUFFICIENT_FUNDS\"");

        assertThat(BackwardCompatibility.problems(newer, older))
                .singleElement()
                .asString()
                .contains("MISSING_ENUM_SYMBOLS")
                .contains("UNKNOWN_ACCOUNT");
    }

    @Test
    @DisplayName("B3 records refer to enums defined in other files of the same version")
    void recordsReferToEnumsOfTheSameVersion() {
        var parsed = BackwardCompatibility.parse(List.of("""
                {"type": "record", "name": "Refused", "namespace": "test",
                 "fields": [{"name": "reason", "type": "Reason"}]}""", """
                {"type": "enum", "name": "Reason", "namespace": "test", "symbols": ["A"]}"""));

        assertThat(parsed).containsOnlyKeys("test.Refused", "test.Reason");
        assertThat(parsed.get("test.Refused").getField("reason").schema()).isEqualTo(parsed.get("test.Reason"));
    }

    private static List<Path> schemaFiles() throws IOException {
        try (Stream<Path> files = Files.list(SCHEMAS)) {
            return files.filter(file -> file.toString().endsWith(".avsc"))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> read(List<Path> files) {
        return files.stream()
                .map(file -> {
                    try {
                        return Files.readString(file);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
    }

    private static Schema record(String fields) {
        return new Schema.Parser().parse("""
                {"type": "record", "name": "Sample", "namespace": "test", "fields": [%s]}""".formatted(fields));
    }

    private static Schema enumOf(String symbols) {
        return new Schema.Parser().parse("""
                {"type": "enum", "name": "Reason", "namespace": "test", "symbols": [%s]}""".formatted(symbols));
    }
}
