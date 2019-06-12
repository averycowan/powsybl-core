/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.exceptions.UncheckedSaxException;
import com.powsybl.commons.exceptions.UncheckedXmlStreamException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionProviders;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.IidmImportExportMode;
import com.powsybl.iidm.anonymizer.Anonymizer;
import com.powsybl.iidm.anonymizer.SimpleAnonymizer;
import com.powsybl.iidm.export.BusFilter;
import com.powsybl.iidm.export.ExportOptions;
import com.powsybl.iidm.import_.ImportOptions;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.xml.update.*;
import javanet.staxutils.IndentingXMLStreamWriter;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.powsybl.iidm.xml.IidmXmlConstants.*;
import static com.powsybl.iidm.xml.XMLImporter.SUFFIX_MAPPING;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class NetworkXml {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkXml.class);

    private static final String EXTENSION_CATEGORY_NAME = "network";
    static final String NETWORK_ROOT_ELEMENT_NAME = "network";
    private static final String EXTENSION_ELEMENT_NAME = "extension";
    private static final String IIDM_XSD = "iidm.xsd";
    private static final String CASE_DATE = "caseDate";
    private static final String FORECAST_DISTANCE = "forecastDistance";
    private static final String SOURCE_FORMAT = "sourceFormat";
    private static final String ID = "id";
    private static final String FILE_NOT_FOUND = "%s file is not found";
    private static final String TOPO_SUFFIX = "-TOPO.xiidm";
    private static final String STATE_SUFFIX = "-STATE.xiidm";
    private static final String CONTROL_SUFFIX = "-CONTROL.xiidm";



    // cache XMLOutputFactory to improve performance
    private static final Supplier<XMLOutputFactory> XML_OUTPUT_FACTORY_SUPPLIER = Suppliers.memoize(XMLOutputFactory::newFactory);

    private static final Supplier<XMLInputFactory> XML_INPUT_FACTORY_SUPPLIER = Suppliers.memoize(XMLInputFactory::newInstance);

    private static final Supplier<ExtensionProviders<ExtensionXmlSerializer>> EXTENSIONS_SUPPLIER = Suppliers.memoize(() -> ExtensionProviders.createProvider(ExtensionXmlSerializer.class, EXTENSION_CATEGORY_NAME));

    private NetworkXml() {
    }

    private static XMLStreamWriter createXmlStreamWriter(ExportOptions options, OutputStream os) throws XMLStreamException {
        XMLStreamWriter writer = XML_OUTPUT_FACTORY_SUPPLIER.get().createXMLStreamWriter(os, StandardCharsets.UTF_8.toString());
        if (options.isIndent()) {
            IndentingXMLStreamWriter indentingWriter = new IndentingXMLStreamWriter(writer);
            indentingWriter.setIndent(INDENT);
            writer = indentingWriter;
        }
        return writer;
    }

    private static Set<String> getNetworkExtensions(Network n) {
        Set<String> extensions = new TreeSet<>();
        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            for (Extension<? extends Identifiable<?>> extension : identifiable.getExtensions()) {
                extensions.add(extension.getName());
            }
        }
        return extensions;
    }

    private static void validate(Source xml, List<Source> additionalSchemas) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Source[] sources = new Source[additionalSchemas.size() + 1];
        sources[0] = new StreamSource(NetworkXml.class.getResourceAsStream("/xsd/" + IIDM_XSD));
        for (int i = 0; i < additionalSchemas.size(); i++) {
            sources[i + 1] = additionalSchemas.get(i);
        }
        try {
            Schema schema = factory.newSchema(sources);
            Validator validator = schema.newValidator();
            validator.validate(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SAXException e) {
            throw new UncheckedSaxException(e);
        }
    }

    static void validate(InputStream is) {
        validate(new StreamSource(is), Collections.emptyList());
    }

    static void validate(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            validate(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void validateWithExtensions(InputStream is) {
        List<Source> additionalSchemas = EXTENSIONS_SUPPLIER.get().getProviders().stream()
                .map(e -> new StreamSource(e.getXsdAsStream()))
                .collect(Collectors.toList());
        validate(new StreamSource(is), additionalSchemas);
    }

    static void validateWithExtensions(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            validateWithExtensions(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeExtensionNamespace(ExportOptions options, XMLStreamWriter writer, String extensionName) throws XMLStreamException {
        ExtensionXmlSerializer extensionXmlSerializer = getExtensionXmlSerializer(options, extensionName);
        if (extensionXmlSerializer == null) {
            return;
        }
        writer.setPrefix(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
        writer.writeNamespace(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
    }

    private static void writeExtensionNamespaces(Network n, ExportOptions options, XMLStreamWriter writer) throws XMLStreamException {
        Set<String> extensionUris = new HashSet<>();
        Set<String> extensionPrefixes = new HashSet<>();
        for (String extensionName : getNetworkExtensions(n)) {
            ExtensionXmlSerializer extensionXmlSerializer = getExtensionXmlSerializer(options, extensionName);
            if (extensionXmlSerializer == null) {
                continue;
            }
            if (extensionUris.contains(extensionXmlSerializer.getNamespaceUri())) {
                throw new PowsyblException("Extension namespace URI collision");
            } else {
                extensionUris.add(extensionXmlSerializer.getNamespaceUri());
            }
            if (extensionPrefixes.contains(extensionXmlSerializer.getNamespacePrefix())) {
                throw new PowsyblException("Extension namespace prefix collision");
            } else {
                extensionPrefixes.add(extensionXmlSerializer.getNamespacePrefix());
            }
            writer.setPrefix(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
            writer.writeNamespace(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
        }
    }

    private static void writeExtension(Extension<? extends Identifiable<?>> extension, NetworkXmlWriterContext context) throws XMLStreamException {
        XMLStreamWriter writer = context.getExtensionsWriter();
        ExtensionXmlSerializer extensionXmlSerializer = getExtensionXmlSerializer(context.getOptions(),
                extension.getName());

        if (extensionXmlSerializer == null) {
            if (context.getOptions().isThrowExceptionIfExtensionNotFound()) {
                throw new PowsyblException("XmlSerializer for" + extension.getName() + "not found");
            }
            return;
        }
        if (extensionXmlSerializer.hasSubElements()) {
            writer.writeStartElement(extensionXmlSerializer.getNamespaceUri(), extension.getName());
        } else {
            writer.writeEmptyElement(extensionXmlSerializer.getNamespaceUri(), extension.getName());
        }
        extensionXmlSerializer.write(extension, context);
        if (extensionXmlSerializer.hasSubElements()) {
            writer.writeEndElement();
        }
    }

    private static ExtensionXmlSerializer getExtensionXmlSerializer(ExportOptions options, String extensionName) {
        ExtensionXmlSerializer extensionXmlSerializer = options.isThrowExceptionIfExtensionNotFound()
                ? EXTENSIONS_SUPPLIER.get().findProviderOrThrowException(extensionName)
                : EXTENSIONS_SUPPLIER.get().findProvider(extensionName);
        if (extensionXmlSerializer == null) {
            LOGGER.warn("No Extension XML Serializer for {}", extensionName);
        }
        return extensionXmlSerializer;
    }

    static Map<String, Set<String>> getIdentifiablesPerExtensionType(Network n) {
        Map<String, Set<String>> extensionsPerType = new HashMap<>();
        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            for (Extension<? extends Identifiable<?>> extension : identifiable.getExtensions()) {
                extensionsPerType.computeIfAbsent(extension.getName(), key -> new HashSet<>()).add(identifiable.getId());
            }
        }
        return extensionsPerType;
    }

    private static Set<String> getExtensionsName(Collection<? extends Extension<? extends Identifiable<?>>> extensions) {
        Set<String> extensionsSet = new HashSet<>();
        for (Extension<? extends Identifiable<?>> extension : extensions) {
            extensionsSet.add(extension.getName());
        }
        return extensionsSet;
    }

    private static void writeExtensions(Network n, NetworkXmlWriterContext context, ExportOptions options) throws XMLStreamException {

        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            Collection<? extends Extension<? extends Identifiable<?>>> extensions = identifiable.getExtensions();
            if (!context.isExportedEquipment(identifiable) || extensions.isEmpty() || !options.hasAtLeastOneExtension(getExtensionsName(extensions))) {
                continue;
            }
            context.getExtensionsWriter().writeStartElement(IIDM_URI, EXTENSION_ELEMENT_NAME);
            context.getExtensionsWriter().writeAttribute(ID, context.getAnonymizer().anonymizeString(identifiable.getId()));
            for (Extension<? extends Identifiable<?>> extension : extensions) {
                if (options.withExtension(extension.getName())) {
                    writeExtension(extension, context);
                }
            }
            context.getExtensionsWriter().writeEndElement();
        }
    }

    private static void writeMainAttributes(Network n, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute(ID, n.getId());
        writer.writeAttribute(CASE_DATE, n.getCaseDate().toString());
        writer.writeAttribute(FORECAST_DISTANCE, Integer.toString(n.getForecastDistance()));
        writer.writeAttribute(SOURCE_FORMAT, n.getSourceFormat());
    }

    private static XMLStreamWriter writeStartAttributes(OutputStream os, ExportOptions options) throws XMLStreamException {
        XMLStreamWriter writer;
        writer = createXmlStreamWriter(options, os);
        writer.writeStartDocument(StandardCharsets.UTF_8.toString(), "1.0");
        writer.setPrefix(IIDM_PREFIX, IIDM_URI);
        writer.writeStartElement(IIDM_URI, NETWORK_ROOT_ELEMENT_NAME);
        writer.writeNamespace(IIDM_PREFIX, IIDM_URI);
        return writer;
    }

    private static XMLStreamWriter initializeWriter(Network n, OutputStream os, ExportOptions options) throws XMLStreamException {
        XMLStreamWriter writer = writeStartAttributes(os, options);
        if (!options.withNoExtension()) {
            writeExtensionNamespaces(n, options, writer);
        }
        writeMainAttributes(n, writer);
        return writer;
    }

    private static XMLStreamWriter initializeBaseNetworkWriter(Network n, OutputStream os, ExportOptions options) throws XMLStreamException {
        XMLStreamWriter writer = writeStartAttributes(os, options);
        writeMainAttributes(n, writer);
        return writer;
    }

    private static XMLStreamWriter initializeExtensionFileWriter(Network n, OutputStream os, ExportOptions options, String extensionName) throws XMLStreamException {
        XMLStreamWriter writer = writeStartAttributes(os, options);
        writeExtensionNamespace(options, writer, extensionName);
        writeMainAttributes(n, writer);
        return writer;
    }

    private static void writeEndElement(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    private static void writeExtensionsInMultipleFile(Network n, NetworkXmlWriterContext context, DataSource dataSource, ExportOptions options, String dataSourceExt) throws XMLStreamException, IOException {
        //here we right one extension type  per file
        Map<String, Set<String>> m = getIdentifiablesPerExtensionType(n);
        String ext = dataSourceExt.isEmpty() ? "" : "." + dataSourceExt;

        for (Map.Entry<String, Set<String>> entry : m.entrySet()) {
            String name = entry.getKey();
            Set<String> ids = entry.getValue();
            if (options.withExtension(name)) {
                try (OutputStream os = dataSource.newOutputStream(dataSource.getBaseName() + "-" +  name + ext, false);
                     BufferedOutputStream bos = new BufferedOutputStream(os)) {
                    XMLStreamWriter writer = initializeExtensionFileWriter(n, bos, options, name);
                    for (String id : ids) {
                        writer.writeStartElement(IIDM_URI, EXTENSION_ELEMENT_NAME);
                        writer.writeAttribute("id", context.getAnonymizer().anonymizeString(id));
                        context.setExtensionsWriter(writer);
                        writeExtension(n.getIdentifiable(id).getExtensionByName(name), context);
                        writer.writeEndElement();
                    }
                    writeEndElement(writer);
                }
            }
        }
    }

    private static NetworkXmlWriterContext writeBaseNetwork(Network n, OutputStream os, ExportOptions options) throws XMLStreamException {

        // create the  writer of the base file
        XMLStreamWriter writer;
        if (options.getMode() == IidmImportExportMode.UNIQUE_FILE) {
            writer = initializeWriter(n, os, options);
        } else {
            writer = initializeBaseNetworkWriter(n, os, options);
        }
        BusFilter filter = BusFilter.create(n, options);
        Anonymizer anonymizer = options.isAnonymized() ? new SimpleAnonymizer() : null;
        NetworkXmlWriterContext context = new NetworkXmlWriterContext(anonymizer, writer, options, filter);
        // Consider the network has been exported so its extensions will be written also
        context.addExportedEquipment(n);

        for (Substation s : n.getSubstations()) {
            SubstationXml.INSTANCE.write(s, null, context);
        }
        for (Line l : n.getLines()) {
            if (!filter.test(l)) {
                continue;
            }
            if (l.isTieLine()) {
                TieLineXml.INSTANCE.write((TieLine) l, n, context);
            } else {
                LineXml.INSTANCE.write(l, n, context);
            }
        }
        for (HvdcLine l : n.getHvdcLines()) {
            if (!filter.test(l.getConverterStation1()) || !filter.test(l.getConverterStation2())) {
                continue;
            }
            HvdcLineXml.INSTANCE.write(l, n, context);
        }
        return context;
    }

    public static Anonymizer write(Network n, ExportOptions options, OutputStream os) {
        if (options.getMode() == IidmImportExportMode.EXTENSIONS_IN_ONE_SEPARATED_FILE ||
                options.getMode() == IidmImportExportMode.ONE_SEPARATED_FILE_PER_EXTENSION_TYPE) {
            throw new PowsyblException("You can call this method only with UNIQUE_FILE mode");
        }
        try {
            NetworkXmlWriterContext context = writeBaseNetwork(n, os, options);
            // write extensions
            writeExtensions(n, context, options);
            writeEndElement(context.getWriter());
            return context.getAnonymizer();
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Anonymizer write(Network n, OutputStream os) {
        return write(n, new ExportOptions(), os);
    }

    private static String getFileExtensionFromPath(Path xmlFile) {
        return FilenameUtils.getExtension(xmlFile.getFileName().toString());
    }

    private static DataSource getDataSourceFromPath(Path xmlFile) {
        String fileBaseName =  FilenameUtils.getBaseName(xmlFile.getFileName().toString());
        return new FileDataSource(xmlFile.getParent(), fileBaseName);
    }

    public static Anonymizer write(Network n, ExportOptions options, Path xmlFile) {
        try {
            return write(n, options, getDataSourceFromPath(xmlFile), getFileExtensionFromPath(xmlFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Anonymizer write(Network n, Path xmlFile) {
        return write(n, new ExportOptions(), xmlFile);
    }

    public static void incrementalWrite(Network n, ExportOptions options, DataSource dataSource) throws IOException {
        if (options.isTopo()) {
            try (OutputStream os = dataSource.newOutputStream(dataSource.getBaseName() + TOPO_SUFFIX, false);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                write(n, options, bos, IncrementalIidmFiles.TOPO);
            }
        }
        if (options.isState()) {
            try (OutputStream os = dataSource.newOutputStream(dataSource.getBaseName() + STATE_SUFFIX, false);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                write(n, options, bos, IncrementalIidmFiles.STATE);
            }
        }
        if (options.isControl()) {
            try (OutputStream os = dataSource.newOutputStream(dataSource.getBaseName() + CONTROL_SUFFIX, false);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                write(n, options, bos, IncrementalIidmFiles.CONTROL);
            }
        }
    }

    private static void writeLine(Network n, Line l, NetworkXmlWriterContext context, IncrementalIidmFiles targetFile) throws XMLStreamException {
        if ((targetFile == IncrementalIidmFiles.STATE && LineXml.INSTANCE.hasStateValues(l)) ||
                targetFile == IncrementalIidmFiles.TOPO && LineXml.INSTANCE.hasTopoValues(l, context)) {
            if (l.isTieLine()) {
                TieLineXml.INSTANCE.write((TieLine) l, n, context);
            } else {
                LineXml.INSTANCE.write(l, n, context);
            }
        }
    }

    private static void writeLines(Network n, NetworkXmlWriterContext  context, IncrementalIidmFiles targetFile, BusFilter filter) throws XMLStreamException {
        if (targetFile == IncrementalIidmFiles.STATE && context.getOptions().isWithBranchSV() ||
                targetFile == IncrementalIidmFiles.TOPO) {
            for (Line l : n.getLines()) {
                if (!filter.test(l)) {
                    continue;
                }
                writeLine(n, l, context, targetFile);
            }
        }
    }

    private static void writeSubstations(Network n, NetworkXmlWriterContext  context, IncrementalIidmFiles targetFile) throws XMLStreamException {
        for (Substation s : n.getSubstations()) {
            if ((targetFile == IncrementalIidmFiles.CONTROL && !SubstationXml.INSTANCE.hasControlValues(s, context)) ||
                    (targetFile == IncrementalIidmFiles.STATE && !SubstationXml.INSTANCE.hasStateValues(s, context)) ||
                    targetFile == IncrementalIidmFiles.TOPO && !SubstationXml.INSTANCE.hasTopoValues(s, context)) {
                continue;
            }
            SubstationXml.INSTANCE.write(s, null, context);
        }
    }

    private static void writeHvdcLines(Network n, NetworkXmlWriterContext  context, IncrementalIidmFiles targetFile, BusFilter filter) throws XMLStreamException {
        if (targetFile == IncrementalIidmFiles.CONTROL) {
            for (HvdcLine l : n.getHvdcLines()) {
                if (!filter.test(l.getConverterStation1()) || !filter.test(l.getConverterStation2())) {
                    continue;
                }
                HvdcLineXml.INSTANCE.write(l, n, context);
            }
        }
    }

    public static void write(Network n, ExportOptions options, OutputStream os, IncrementalIidmFiles targetFile) {
        // create the  writer of the base file
        final XMLStreamWriter writer;
        try {
            writer = initializeWriter(n, os, options);
            BusFilter filter = BusFilter.create(n, options);
            Anonymizer anonymizer = options.isAnonymized() ? new SimpleAnonymizer() : null;
            NetworkXmlWriterContext context = new NetworkXmlWriterContext(anonymizer, writer, options, filter);
            context.setTargetFile(targetFile);
            writeSubstations(n, context, targetFile);
            writeLines(n, context, targetFile, filter);
            writeHvdcLines(n, context, targetFile, filter);
            writeEndElement(writer);
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Anonymizer write(Network network, ExportOptions options, DataSource dataSource, String dataSourceExt) throws IOException {
        try (OutputStream osb = dataSource.newOutputStream("", dataSourceExt, false);
             BufferedOutputStream bosb = new BufferedOutputStream(osb)) {

            if (options.getMode() == IidmImportExportMode.UNIQUE_FILE) {
                Anonymizer anonymizer = write(network, options, bosb);
                if (options.isAnonymized()) {
                    try (BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(dataSource.newOutputStream("_mapping", "csv", false), StandardCharsets.UTF_8))) {
                        anonymizer.write(writer2);
                    }
                }
                return anonymizer;
            }

            NetworkXmlWriterContext context = writeBaseNetwork(network, bosb, options);
            writeEndElement(context.getWriter());

            // write extensions
            if (!options.withNoExtension() && !getNetworkExtensions(network).isEmpty()) {

                if (options.getMode() == IidmImportExportMode.EXTENSIONS_IN_ONE_SEPARATED_FILE) {
                    try (OutputStream ose = dataSource.newOutputStream("-ext", dataSourceExt, false);
                         BufferedOutputStream bose = new BufferedOutputStream(ose)) {

                        final XMLStreamWriter extensionsWriter = initializeWriter(network, bose, options);
                        context.setExtensionsWriter(extensionsWriter);
                        writeExtensions(network, context, options);
                        writeEndElement(extensionsWriter);
                    }
                } else if (options.getMode() == IidmImportExportMode.ONE_SEPARATED_FILE_PER_EXTENSION_TYPE) {
                    writeExtensionsInMultipleFile(network, context, dataSource, options, dataSourceExt);
                }
            }

            if (options.isAnonymized()) {
                try (BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(dataSource.newOutputStream("_mapping", "csv", false), StandardCharsets.UTF_8))) {
                    context.getAnonymizer().write(writer2);
                }
            }
            return context.getAnonymizer();
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Anonymizer writeAndValidate(Network n, Path xmlFile) {
        try {
            return writeAndValidate(n, new ExportOptions(), xmlFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Anonymizer writeAndValidate(Network n, ExportOptions options, Path xmlFile) throws IOException {
        Anonymizer anonymizer = write(n, options, xmlFile);
        if (options.getMode() == IidmImportExportMode.UNIQUE_FILE) {
            validateWithExtensions(xmlFile);
            return anonymizer;
        }
        validate(xmlFile, options.getMode());
        return anonymizer;
    }

    public static Network read(InputStream is) {
        return read(is, new ImportOptions(), null);
    }

    public static Network read(InputStream is, ImportOptions config, Anonymizer anonymizer) {
        return read(is, config, anonymizer, NetworkFactory.findDefault());
    }

    public static Network read(InputStream is, ImportOptions config, Anonymizer anonymizer, NetworkFactory networkFactory) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(is);
            int state = reader.next();
            while (state == XMLStreamReader.COMMENT) {
                state = reader.next();
            }
            String id = reader.getAttributeValue(null, ID);
            DateTime date = DateTime.parse(reader.getAttributeValue(null, CASE_DATE));
            int forecastDistance = XmlUtil.readOptionalIntegerAttribute(reader, FORECAST_DISTANCE, 0);
            String sourceFormat = reader.getAttributeValue(null, SOURCE_FORMAT);

            Network network = networkFactory.createNetwork(id, sourceFormat);
            network.setCaseDate(date);
            network.setForecastDistance(forecastDistance);

            NetworkXmlReaderContext context = new NetworkXmlReaderContext(anonymizer, reader, config);

            Set<String> extensionNamesNotFound = new TreeSet<>();

            boolean[] modeWarn = new boolean[1];

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {
                switch (reader.getLocalName()) {
                    case SubstationXml.ROOT_ELEMENT_NAME:
                        SubstationXml.INSTANCE.read(network, context);
                        break;

                    case LineXml.ROOT_ELEMENT_NAME:
                        LineXml.INSTANCE.read(network, context);
                        break;

                    case TieLineXml.ROOT_ELEMENT_NAME:
                        TieLineXml.INSTANCE.read(network, context);
                        break;

                    case HvdcLineXml.ROOT_ELEMENT_NAME:
                        HvdcLineXml.INSTANCE.read(network, context);
                        break;

                    case EXTENSION_ELEMENT_NAME:
                        if (config.getMode() != IidmImportExportMode.UNIQUE_FILE) {
                            modeWarn[0] = true;
                        }
                        String id2 = context.getAnonymizer().deanonymizeString(reader.getAttributeValue(null, "id"));
                        Identifiable identifiable = network.getIdentifiable(id2);
                        if (identifiable == null) {
                            throw new PowsyblException("Identifiable " + id2 + " not found");
                        }
                        readExtensions(identifiable, context, extensionNamesNotFound);
                        break;

                    default:
                        throw new AssertionError();
                }
            });

            if (modeWarn[0]) {
                LOGGER.warn("Mode isn't UNIQUE_FILE and some extensions was found in the base file!, some extensions may be overwritten later when reading extensions files");
            }

            context.getEndTasks().forEach(Runnable::run);
            return network;
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Network read(Path xmlFile) {
        try {
            return read(xmlFile, new ImportOptions());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Network read(ReadOnlyDataSource dataSource, NetworkFactory networkFactory, ImportOptions options, String dataSourceExt) throws IOException {
        Objects.requireNonNull(dataSource);
        Network network;
        Anonymizer anonymizer = null;

        if (dataSource.exists(SUFFIX_MAPPING, "csv")) {
            anonymizer = new SimpleAnonymizer();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(dataSource.newInputStream(SUFFIX_MAPPING, "csv"), StandardCharsets.UTF_8))) {
                anonymizer.read(reader);
            }
        }
        //Read the base file with the extensions declared in the extensions list
        try (InputStream isb = dataSource.newInputStream(null, dataSourceExt)) {
            network = NetworkXml.read(isb, options, anonymizer, networkFactory);
        }
        if (!options.withNoExtension()) {
            switch (options.getMode()) {
                case EXTENSIONS_IN_ONE_SEPARATED_FILE:
                    // in this case we have to read all extensions from one  file
                    try (InputStream ise = dataSource.newInputStream("-ext", dataSourceExt)) {
                        readExtensions(network, ise, anonymizer, options);
                    } catch (IOException e) {
                        LOGGER.warn(String.format("the extensions file wasn't found while importing, please ensure that the file name respect the naming convention baseFileName-ext.%s", dataSourceExt));
                    }
                    break;
                case ONE_SEPARATED_FILE_PER_EXTENSION_TYPE:
                    String ext = dataSourceExt.isEmpty() ? "" : "." + dataSourceExt;
                    // here we'll read all extensions declared in the extensions set
                    readExtensions(network, dataSource, anonymizer, options, ext);
                    break;
                default:
                    break;
            }
        }
        return network;
    }

    public static Network read(Path xmlFile, ImportOptions options) throws IOException {
        DataSource dataSource = getDataSourceFromPath(xmlFile);
        String ext = getFileExtensionFromPath(xmlFile);
        return read(dataSource, NetworkFactory.findDefault(), options, ext);
    }

    public static Network validateAndRead(Path xmlFile) {
        try {
            return validateAndRead(xmlFile, new ImportOptions());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validate(Path xmlFile, IidmImportExportMode mode) throws IOException {
        DataSource dataSource = getDataSourceFromPath(xmlFile);
        String ext =  getFileExtensionFromPath(xmlFile);

        if (mode == IidmImportExportMode.EXTENSIONS_IN_ONE_SEPARATED_FILE) {
            try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + ext)) {
                validateWithExtensions(ise);
            }
            try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + "-ext." + ext)) {
                validateWithExtensions(ise);
            }
        } else {
            Set<String> listNames = dataSource.listNames(".*\\." + ext);
            for (String fileName : listNames) {
                try (InputStream ise = dataSource.newInputStream(fileName)) {
                    validateWithExtensions(ise);
                }
            }
        }
    }

    public static Network validateAndRead(Path xmlFile, ImportOptions options) throws IOException {
        if (options.getMode() == IidmImportExportMode.UNIQUE_FILE) {
            validateWithExtensions(xmlFile);
            return read(xmlFile);
        }
        validate(xmlFile, options.getMode());
        return read(xmlFile, options);
    }

    // To read extensions from multiple extension files
    static void readExtensions(Network network, ReadOnlyDataSource dataSource, Anonymizer anonymizer, ImportOptions options, String ext) throws IOException {
        options.getExtensions().ifPresent(extensions -> {
            for (String extension : extensions) {
                try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + "-" + extension + ext)) {
                    readExtensions(network, ise, anonymizer, options);
                } catch (IOException e) {
                    LOGGER.warn(String.format("the %s extension file is not found despite it was declared in the extensions list", extension));
                }
            }
        });

        if (!options.getExtensions().isPresent()) {
            Set<String> listNames = dataSource.listNames(".*" + ext);
            listNames.remove(dataSource.getBaseName() + ext);
            for (String fileName : listNames) {
                try (InputStream ise = dataSource.newInputStream(fileName)) {
                    readExtensions(network, ise, anonymizer, options);
                } catch (IOException e) {
                    LOGGER.warn(String.format("the %s file is not found ", fileName));
                }
            }
        }
    }

    // To read extensions from an extensions file
    static Network readExtensions(Network network, InputStream ise, Anonymizer anonymizer, ImportOptions options) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(ise);
            int state = reader.next();
            while (state == XMLStreamReader.COMMENT) {
                state = reader.next();
            }
            String id = reader.getAttributeValue(null, "id");
            DateTime date = DateTime.parse(reader.getAttributeValue(null, CASE_DATE));

            //verify that the extensions file matches with the same network
            if (!network.getId().equals(id) || !network.getCaseDate().equals(date)) {
                throw new PowsyblException("Extension file do not match with the base file !");
            }

            NetworkXmlReaderContext context = new NetworkXmlReaderContext(anonymizer, reader, options);
            Set<String> extensionNamesNotFound = new TreeSet<>();

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {
                if (reader.getLocalName().equals(EXTENSION_ELEMENT_NAME)) {
                    String id2 = context.getAnonymizer().deanonymizeString(reader.getAttributeValue(null, "id"));
                    Identifiable identifiable = network.getIdentifiable(id2);
                    if (identifiable == null) {
                        throw new PowsyblException("Identifiable " + id2 + " not found");
                    }
                    readExtensions(identifiable, context, extensionNamesNotFound);
                } else {
                    throw new PowsyblException("Unexpected element: " +  reader.getLocalName());
                }
            });
            return network;
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private static void readExtensions(Identifiable identifiable, NetworkXmlReaderContext context,
                                       Set<String> extensionNamesNotFound) throws XMLStreamException {

        XmlUtil.readUntilEndElement(EXTENSION_ELEMENT_NAME, context.getReader(), new XmlUtil.XmlEventHandler() {

            private boolean topLevel = true;

            @Override
            public void onStartElement() throws XMLStreamException {
                if (topLevel) {
                    String extensionName = context.getReader().getLocalName();
                    if (!context.getOptions().withExtension(extensionName)) {
                        return;
                    }

                    ExtensionXmlSerializer extensionXmlSerializer = EXTENSIONS_SUPPLIER.get().findProvider(extensionName);
                    if (extensionXmlSerializer != null) {
                        Extension<? extends Identifiable<?>> extension = extensionXmlSerializer.read(identifiable, context);
                        identifiable.addExtension(extensionXmlSerializer.getExtensionClass(), extension);
                        topLevel = true;
                    } else {
                        extensionNamesNotFound.add(extensionName);
                        topLevel = false;
                    }
                }
            }
        });

        if (!extensionNamesNotFound.isEmpty()) {
            if (context.getOptions().isThrowExceptionIfExtensionNotFound()) {
                throw new PowsyblException("Extensions " + extensionNamesNotFound + " " +
                        "not found !");
            } else {
                LOGGER.error("Extensions {} not found", extensionNamesNotFound);
            }
        }
    }

    static void update(Network network, ReadOnlyDataSource dataSource) throws IOException {
        try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + STATE_SUFFIX)) {
            update(network, ise, IncrementalIidmFiles.STATE);
        } catch (IOException e) {
            LOGGER.warn(String.format(FILE_NOT_FOUND, dataSource.getBaseName() + STATE_SUFFIX));
        }
        try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + CONTROL_SUFFIX)) {
            update(network, ise, IncrementalIidmFiles.CONTROL);
        } catch (IOException e) {
            LOGGER.warn(String.format(FILE_NOT_FOUND, dataSource.getBaseName() + CONTROL_SUFFIX));
        }
        try (InputStream ise = dataSource.newInputStream(dataSource.getBaseName() + TOPO_SUFFIX)) {
            update(network, ise, IncrementalIidmFiles.TOPO);
        } catch (IOException e) {
            LOGGER.warn(String.format(FILE_NOT_FOUND, dataSource.getBaseName() + TOPO_SUFFIX));
        }
    }

    public static void update(Network network, InputStream is) {
        update(network, is, IncrementalIidmFiles.STATE);
    }

    public static void update(Network network, InputStream is, IncrementalIidmFiles targetFile) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(is);
            reader.next();

            final VoltageLevel[] vl = new VoltageLevel[1];
            final TwoWindingsTransformer[] twt = new TwoWindingsTransformer[1];

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {
                switch (reader.getLocalName()) {
                    case VoltageLevelXml.ROOT_ELEMENT_NAME:
                        VoltageLevelUpdaterXml.updateVoltageLevel(reader, network, vl);
                        break;
                    case BusXml.ROOT_ELEMENT_NAME:
                        BusUpdaterXml.updateBusStateValues(reader, vl, targetFile);
                        break;
                    case BusbarSectionXml.ROOT_ELEMENT_NAME:
                        BusbarSectionUpdaterXml.updateBusbarSectionStateValues(reader, vl, targetFile);
                        break;
                    case NodeBreakerViewSwitchXml.ROOT_ELEMENT_NAME:
                        SwitchUpdaterXml.updateSwitchTopoValues(reader, network, targetFile);
                        // Nothing to do
                        break;
                    case GeneratorXml.ROOT_ELEMENT_NAME:
                        InjectionUpdaterXml.updateInjectionTopoValues(reader, network, vl, targetFile);
                        InjectionUpdaterXml.updateInjectionStateValues(reader, network, targetFile);
                        GeneratorUpdaterXml.updateGeneratorControlValues(reader, network, targetFile);
                        break;
                    case ShuntXml.ROOT_ELEMENT_NAME:
                        InjectionUpdaterXml.updateInjectionTopoValues(reader, network, vl, targetFile);
                        InjectionUpdaterXml.updateInjectionStateValues(reader, network, targetFile);
                        ShuntUpdaterXml.updateShuntControlValues(reader, network, targetFile);
                        break;
                    case StaticVarCompensatorXml.ROOT_ELEMENT_NAME:
                        InjectionUpdaterXml.updateInjectionStateValues(reader, network, targetFile);
                        StaticVarCompensatorUpdaterXml.updateStaticVarControlValues(reader, network, targetFile);
                        break;
                    case LoadXml.ROOT_ELEMENT_NAME:
                    case BatteryXml.ROOT_ELEMENT_NAME:
                    case DanglingLineXml.ROOT_ELEMENT_NAME:
                    case LccConverterStationXml.ROOT_ELEMENT_NAME:
                        InjectionUpdaterXml.updateInjectionTopoValues(reader, network, vl, targetFile);
                        InjectionUpdaterXml.updateInjectionStateValues(reader, network, targetFile);
                        break;
                    case VscConverterStationXml.ROOT_ELEMENT_NAME:
                        InjectionUpdaterXml.updateInjectionTopoValues(reader, network, vl, targetFile);
                        InjectionUpdaterXml.updateInjectionStateValues(reader, network, targetFile);
                        VscConverterStationUpdaterXml.updateVscConverterStationControlValues(reader, network, targetFile);
                        break;
                    case LineXml.ROOT_ELEMENT_NAME:
                        BranchUpdaterXml.updateBranchTopoValues(reader, network, vl, targetFile);
                        BranchUpdaterXml.updateBranchStateValues(reader, network, targetFile);
                        break;
                    case TwoWindingsTransformerXml.ROOT_ELEMENT_NAME:
                        BranchUpdaterXml.updateBranchTopoValues(reader, network, vl, targetFile);
                        BranchUpdaterXml.updateBranchStateValues(reader, network, targetFile);
                        TwoWindingsTransformerUpdaterXml.updateTwoWindingsTransformer(reader, network, twt);
                        break;
                    case HvdcLineXml.ROOT_ELEMENT_NAME:
                        HvdcLineUpdaterXml.updateHvdcLineControlValues(reader, network, targetFile);
                        break;
                    case SubstationXml.ROOT_ELEMENT_NAME:
                    case VoltageLevelXml.BUS_BREAKER_TOPOLOGY_ELEMENT_NAME:
                    case VoltageLevelXml.NODE_BREAKER_TOPOLOGY_ELEMENT_NAME:
                        // Nothing to do
                        break;
                    case TwoWindingsTransformerXml.RATIO_TAP_CHANGER_ELEMENT_NAME:
                        TwoWindingsTransformerUpdaterXml.updateRatioTapChangerControlValues(reader, twt, targetFile);
                        break;
                    case TwoWindingsTransformerXml.PHASE_TAP_CHANGER_ELEMENT_NAME:
                        TwoWindingsTransformerUpdaterXml.updatePhaseTapChangerControlValues(reader, twt, targetFile);
                        break;
                    case TwoWindingsTransformerXml.TERMINAL_REF_ELEMENT_NAME:
                        // to do
                        break;
                    case ThreeWindingsTransformerXml.ROOT_ELEMENT_NAME:
                        throw new AssertionError();

                    default:
                        throw new AssertionError("Unexpected element: " + reader.getLocalName());
                }
            });
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static void update(Network network, Path xmlFile) {
        try (InputStream is = Files.newInputStream(xmlFile)) {
            update(network, is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] gzip(Network network) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            write(network, gzos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    public static Network gunzip(byte[] networkXmlGz) {
        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(networkXmlGz))) {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deep copy of the network using XML converter.
     * @param network the network to copy
     * @return the copy of the network
     */
    public static Network copy(Network network) {
        return copy(network, ForkJoinPool.commonPool());
    }

    public static Network copy(Network network, ExecutorService executor) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(executor);
        PipedOutputStream pos = new PipedOutputStream();
        try (InputStream is = new PipedInputStream(pos)) {
            executor.execute(() -> {
                try {
                    write(network, pos);
                } catch (Exception t) {
                    LOGGER.error(t.toString(), t);
                } finally {
                    try {
                        pos.close();
                    } catch (IOException e) {
                        LOGGER.error(e.toString(), e);
                    }
                }
            });
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
