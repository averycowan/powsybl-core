/**
 * Copyright (c) 2017-2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.cgmes.conversion.test;

import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.cgmes.conversion.CgmesExport;
import com.powsybl.cgmes.conversion.CgmesImport;
import com.powsybl.cgmes.conversion.CgmesModelExtension;
import com.powsybl.cgmes.conversion.Conversion;
import com.powsybl.cgmes.conversion.test.network.compare.Comparison;
import com.powsybl.cgmes.conversion.test.network.compare.ComparisonConfig;
import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.cgmes.model.CgmesModelException;
import com.powsybl.cgmes.model.GridModelReference;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.iidm.network.Branch.Side;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.impl.NetworkFactoryImpl;
import com.powsybl.iidm.xml.XMLExporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.resultscompletion.LoadFlowResultsCompletion;
import com.powsybl.loadflow.resultscompletion.LoadFlowResultsCompletionParameters;
import com.powsybl.loadflow.validation.ValidationConfig;
import com.powsybl.loadflow.validation.ValidationType;
import com.powsybl.triplestore.api.TripleStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Luma Zamarreño {@literal <zamarrenolm at aia.es>}
 */
public class ConversionTester {

    public ConversionTester(Properties importParams, Properties exportParams, List<String> tripleStoreImplementations,
                            ComparisonConfig networkComparison) {
        this.importParams = importParams;
        this.exportParams = exportParams;
        this.tripleStoreImplementations = tripleStoreImplementations;
        this.networkComparison = networkComparison;
        this.onlyReport = false;
        this.strictTopologyTest = true;
        this.exportXiidm = false;
        this.exportCgmes = false;
        this.testExportImportCgmes = false;
    }

    public ConversionTester(Properties importParams, List<String> tripleStoreImplementations,
        ComparisonConfig networkComparison) {
        this(importParams, null, tripleStoreImplementations, networkComparison);
    }

    public ConversionTester(List<String> tripleStoreImplementations, ComparisonConfig networkComparison) {
        this(null, tripleStoreImplementations, networkComparison);
    }

    public void setOnlyReport(boolean onlyReport) {
        this.onlyReport = onlyReport;
    }

    public void setReportConsumer(Consumer<String> reportConsumer) {
        this.reportConsumer = reportConsumer;
    }

    public void setStrictTopologyTest(boolean strictTopologyTest) {
        this.strictTopologyTest = strictTopologyTest;
    }

    public void setExportXiidm(boolean exportXiidm) {
        this.exportXiidm = exportXiidm;
    }

    public void setExportCgmes(boolean exportCgmes) {
        this.exportCgmes = exportCgmes;
    }

    public void setTestExportImportCgmes(boolean testExportImportCgmes) {
        this.testExportImportCgmes = testExportImportCgmes;
    }

    public void setValidateBusBalances(boolean b) {
        this.validateBusBalances = b;
    }

    public void setValidateBusBalancesUsingThreshold(double threshold) {
        this.validateBusBalances = true;
        this.validateBusBalancesThreshold = threshold;
    }

    public void testConversion(Network expected, GridModelReference gm) throws IOException {
        testConversion(expected, gm, this.networkComparison);
    }

    public void testConversion(Network expected, GridModelReference gm, ComparisonConfig config)
        throws IOException {
        if (onlyReport) {
            testConversionOnlyReport(gm);
        } else {
            for (String impl : tripleStoreImplementations) {
                LOG.info("testConversion. TS implementation {}, grid model {}", impl, gm.name());
                testConversion(expected, gm, config, impl);
            }
        }
    }

    public Network lastConvertedNetwork() {
        return lastConvertedNetwork;
    }

    private void testConversion(Network expected, GridModelReference gm, ComparisonConfig config, String impl)
        throws IOException {
        Properties iparams = importParams == null ? new Properties() : importParams;
        iparams.put("storeCgmesModelAsNetworkExtension", "true");
        iparams.put("powsyblTripleStore", impl);
        // This is to be able to easily compare the topology computed
        // by powsybl against the topology present in the CGMES model
        iparams.put("createBusbarSectionForEveryConnectivityNode", "true");
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            CgmesImport i = new CgmesImport();
            ReadOnlyDataSource ds = gm.dataSource();
            Network network = i.importData(ds, new NetworkFactoryImpl(), iparams);
            if (network.getSubstationCount() == 0) {
                fail("Model is empty");
            }
            CgmesModel cgmes = network.getExtension(CgmesModelExtension.class).getCgmesModel();
            if (!new TopologyTester(cgmes, network).test(strictTopologyTest)) {
                fail("Topology test failed");
            }
            if (expected != null) {
                new Comparison(expected, network, config).compare();
            }
            if (exportXiidm) {
                exportXiidm(gm.name(), impl, expected, network);
            }
            if (exportCgmes) {
                exportCgmes(gm.name(), impl, network);
            }
            if (testExportImportCgmes) {
                testExportImportCgmes(network, ds, fs, i, iparams, config);
            }
            if (validateBusBalances) {
                validateBusBalances(network);
            }
            lastConvertedNetwork = network;
        }
    }

    private void testConversionOnlyReport(GridModelReference gm) {
        String impl = TripleStoreFactory.defaultImplementation();
        CgmesImport i = new CgmesImport();
        Properties params = new Properties();
        params.put("storeCgmesModelAsNetworkExtension", "true");
        params.put("powsyblTripleStore", impl);
        ReadOnlyDataSource ds = gm.dataSource();
        LOG.info("Importer.exists() == {}", i.exists(ds));
        Network n = i.importData(ds, new NetworkFactoryImpl(), params);
        CgmesModel m = n.getExtension(CgmesModelExtension.class).getCgmesModel();
        new Conversion(m).report(reportConsumer);
    }

    private static void exportXiidm(String name, String impl, Network expected, Network actual) throws IOException {
        String name1 = name.replace('/', '-');
        Path path = Files.createTempDirectory("temp-conversion-" + name1 + "-" + impl + "-");
        XMLExporter xmlExporter = new XMLExporter();
        // Last component of the path is the name for the exported XML
        if (expected != null) {
            xmlExporter.export(expected, null, new FileDataSource(path, "expected"));
        }
        if (actual != null) {
            xmlExporter.export(actual, null, new FileDataSource(path, "actual"));
        }
    }

    private static void exportCgmes(String name, String impl, Network network) throws IOException {
        String name1 = name.replace('/', '-');
        Path path = Files.createTempDirectory("temp-export-cgmes-" + name1 + "-" + impl + "-");
        new CgmesExport().export(network, null, new FileDataSource(path, "foo"));
    }

    private void testExportImportCgmes(Network network, ReadOnlyDataSource originalDs, FileSystem fs, CgmesImport i, Properties iparams,
                                       ComparisonConfig config) throws IOException {

        Path path = fs.getPath("temp-export-cgmes");
        Files.createDirectories(path);
        String baseName = "bar";
        new CgmesExport().export(network, exportParams, new FileDataSource(path, baseName));

        DataSource ds = new FileDataSource(path, "bar");
        String expected = originalDs.listNames(".*EQ.*").stream().filter(name -> !name.contains("BD")).findFirst().orElseThrow(() -> new CgmesModelException("Should contain EQ profile"));
        try (OutputStream out = new BufferedOutputStream(ds.newOutputStream(baseName + "_EQ.xml", false));
             InputStream in = originalDs.newInputStream(expected)) {
            ByteStreams.copy(in, out);
        }
        expected = originalDs.listNames(".*TP.*").stream().filter(name -> !name.contains("BD")).findFirst().orElseThrow(() -> new CgmesModelException("Should contain TP profile"));
        try (OutputStream out = new BufferedOutputStream(ds.newOutputStream(baseName + "_TP.xml", false));
             InputStream in = originalDs.newInputStream(expected)) {
            ByteStreams.copy(in, out);
        }
        for (String boundary : originalDs.listNames(".*BD.*")) {
            try (OutputStream out = new BufferedOutputStream(ds.newOutputStream(baseName + boundary, false));
                 InputStream in = originalDs.newInputStream(boundary)) {
                ByteStreams.copy(in, out);
            }
        }
        Network actual = i.importData(ds, new NetworkFactoryImpl(), iparams);
        new Comparison(network, actual, config).compare();
    }

    public void validateBusBalances(Network network) throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            ValidationConfig config = loadFlowValidationConfig(validateBusBalancesThreshold);
            Path working = Files.createDirectories(fs.getPath("lf-validation"));

            computeMissingFlows(network, config.getLoadFlowParameters());
            assertTrue(ValidationType.BUSES.check(network, config, working));
        }
    }

    private static ValidationConfig loadFlowValidationConfig(double threshold) {
        ValidationConfig config = ValidationConfig.load();
        config.setVerbose(true);
        config.setThreshold(threshold);
        config.setOkMissingValues(false);
        config.setLoadFlowParameters(new LoadFlowParameters());
        LOG.info("twtSplitShuntAdmittance is {}", config.getLoadFlowParameters().isTwtSplitShuntAdmittance());
        return config;
    }

    public static void computeMissingFlows(Network network) {
        computeMissingFlows(network, new LoadFlowParameters());
    }

    public static void computeMissingFlows(Network network, LoadFlowParameters lfparams) {
        LoadFlowResultsCompletionParameters p = new LoadFlowResultsCompletionParameters(
            LoadFlowResultsCompletionParameters.EPSILON_X_DEFAULT,
            LoadFlowResultsCompletionParameters.APPLY_REACTANCE_CORRECTION_DEFAULT,
            LoadFlowResultsCompletionParameters.Z0_THRESHOLD_DIFF_VOLTAGE_ANGLE);
        LoadFlowResultsCompletion lf = new LoadFlowResultsCompletion(p, lfparams);
        try {
            lf.run(network, null);
        } catch (Exception e) {
            LOG.error("computeFlows, error {}", e.getMessage());
        }
    }

    public static void invalidateFlows(Network n) {
        n.getLineStream().forEach(line -> {
            invalidateFlow(line.getTerminal(Side.ONE));
            invalidateFlow(line.getTerminal(Side.TWO));
        });
        n.getTwoWindingsTransformerStream().forEach(twt -> {
            invalidateFlow(twt.getTerminal(Side.ONE));
            invalidateFlow(twt.getTerminal(Side.TWO));
        });
        n.getShuntCompensatorStream().forEach(sh -> {
            Terminal terminal = sh.getTerminal();
            terminal.setQ(Double.NaN);
        });
        n.getThreeWindingsTransformerStream().forEach(twt -> {
            invalidateFlow(twt.getLeg1().getTerminal());
            invalidateFlow(twt.getLeg2().getTerminal());
            invalidateFlow(twt.getLeg3().getTerminal());
        });
    }

    static void invalidateFlow(Terminal t) {
        t.setP(Double.NaN);
        t.setQ(Double.NaN);
    }

    private final Properties importParams;
    private final Properties exportParams;
    private final List<String> tripleStoreImplementations;
    private final ComparisonConfig networkComparison;
    private boolean onlyReport;
    private boolean exportXiidm;
    private boolean exportCgmes;
    private boolean testExportImportCgmes;
    private boolean validateBusBalances;
    private double validateBusBalancesThreshold = 0.01;
    private Consumer<String> reportConsumer;
    private boolean strictTopologyTest;
    private Network lastConvertedNetwork;

    private static final Logger LOG = LoggerFactory.getLogger(ConversionTester.class);
}
