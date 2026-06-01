# XML SchemaLightener

[![Maven](https://github.com/brcolow/SchemaLightener/actions/workflows/maven.yml/badge.svg)](https://github.com/brcolow/SchemaLightener/actions/workflows/maven.yml)

XML SchemaLightener is a Java library and XSLT toolkit for reducing and consolidating XML Schema and WSDL assets.

It provides three transformations:

- **Schema Lightener**: removes unused global schema components based on an XML instance.
- **Schema Flattener**: consolidates schema includes and keeps imports in the fewest possible schema files.
- **WSDL Flattener**: consolidates a WSDL and the schema dependencies it references.

The core transformations are still the original XSLT 2.0 stylesheets. This project now packages them as a standard Maven library with a Java API, JPMS module descriptor, Maven wrapper, CI, and JUnit tests.

## Maven

Build and test with:

```shell
./mvnw verify
```

On Windows:

```shell
mvnw.cmd verify
```

The Maven coordinates are:

```xml
<dependency>
    <groupId>io.github.brcolow</groupId>
    <artifactId>schema-lightener</artifactId>
    <version>5.0.0-SNAPSHOT</version>
</dependency>
```

The JPMS module name is:

```java
requires com.xmlhelpline.schemalightener;
```

The module exports only `com.xmlhelpline.schemalightener`. Saxon HE is used at runtime to execute the bundled XSLT 2.0 stylesheets, but Saxon types are not exposed through the public Java API.

## Java API

Path-based transformations write result documents to an output directory:

```java
import com.xmlhelpline.schemalightener.SchemaLightener;
import com.xmlhelpline.schemalightener.TransformationResult;

import java.nio.file.Paths;

SchemaLightener schemaLightener = new SchemaLightener();

TransformationResult flattened = schemaLightener.flattenSchema(
        Paths.get("schemas/root.xsd"),
        Paths.get("build/flattened"));

TransformationResult lightened = schemaLightener.lightenSchema(
        Paths.get("schemas/root.xsd"),
        Paths.get("samples/example.xml"),
        Paths.get("build/lightened"));

TransformationResult flattenedWsdl = schemaLightener.flattenWsdl(
        Paths.get("service.wsdl"),
        Paths.get("build/wsdl"));
```

In-memory transformations accept strings, readers, or `XmlInput` values and return generated documents without writing to disk:

```java
import com.xmlhelpline.schemalightener.InMemoryTransformationResult;
import com.xmlhelpline.schemalightener.SchemaLightener;

SchemaLightener schemaLightener = new SchemaLightener();

String schemaXml =
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "    <xsd:element name=\"order\" type=\"xsd:string\"/>" +
        "</xsd:schema>";

InMemoryTransformationResult result = schemaLightener.flattenSchema(schemaXml);
String flattenedSchema = result.findResultDocument("schema.xsd")
        .orElseThrow(() -> new IllegalStateException("No schema result produced"));
```

When in-memory documents need to resolve relative includes or imports, provide stable absolute system IDs:

```java
import com.xmlhelpline.schemalightener.InMemoryTransformationResult;
import com.xmlhelpline.schemalightener.XmlInput;

import java.net.URI;

XmlInput commonSchema = XmlInput.fromString(
        commonSchemaXml,
        URI.create("memory:/schemas/common.xsd"));

InMemoryTransformationResult flattened = schemaLightener.flattenSchema(
        rootSchemaXml,
        URI.create("memory:/schemas/root.xsd"),
        commonSchema);
```

## Requirements

- Java 9 or later.
- Maven 3.9.x when using the wrapper.
- Saxon HE 12.9 at runtime. The Maven build declares this dependency.

## How Lightening Works

The lightener applies an XSLT stylesheet to an XML Schema and an XML instance that is valid against that schema. The instance represents the subset you want to preserve. The transformation removes unused global elements, attributes, types, attribute groups, and groups, then writes a smaller schema that still conforms to the original schema's data model for the selected subset.

This is useful when you need a smaller validation schema for a trading partner, implementation profile, standards subset, or internal integration view without hand-editing a large schema.

## Project Layout

- `src/main/java`: Java library API and JPMS descriptor.
- `src/main/resources/com/xmlhelpline/schemalightener/xslt`: bundled XSLT stylesheets.
- `src/test/java`: JUnit tests for schema lightening, schema flattening, WSDL flattening, and in-memory APIs.
- `SampleData`: historical sample schemas and XML instances from standards consortia.

## Historical Testing

Before the project was published on GitHub, a regression run created and validated more than 15,000 generated schema files. A summary is included in `regressiontesting_schemas_validationinfo.txt`.

Historical project notes are available at:

- https://www.xmlhelpline.com/tools/SchemaLightener.php#howmuch
- https://www.xmlhelpline.com/tools/SchemaLightener.php#tested

## Authors

* **Paul Kiel** - *Initial work* - [Paul Kiel](https://github.com/pkielgithub)

## License

This project is licensed under the MIT License. See [LICENSE.md](LICENSE.md).
