/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.ampl.converter;

import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.DanglingLine;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.test.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.powsybl.commons.test.ComparisonUtils.compareTxt;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AmplNetworkWriterTest extends AbstractConverterTest {

    private void assertEqualsToRef(MemDataSource dataSource, String suffix, String refFileName) throws IOException {
        try (InputStream actual = new ByteArrayInputStream(dataSource.getData(suffix, "txt"))) {
            compareTxt(getClass().getResourceAsStream("/" + refFileName), actual);
        }
    }

    @Test
    void test() {
        AmplExporter exporter = new AmplExporter();
        assertEquals("AMPL", exporter.getFormat());
        assertEquals("IIDM to AMPL converter", exporter.getComment());
        assertEquals(5, exporter.getParameters().size());
    }

    @Test
    void writeEurostag() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithMoreGenerators();

        MemDataSource dataSource = new MemDataSource();
        AmplExporter exporter = new AmplExporter();
        exporter.export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_substations", "inputs/eurostag-tutorial-example1-substations.txt");
        assertEqualsToRef(dataSource, "_network_buses", "inputs/eurostag-tutorial-example1-buses.txt");
        assertEqualsToRef(dataSource, "_network_tct", "inputs/eurostag-tutorial-example1-tct.txt");
        assertEqualsToRef(dataSource, "_network_rtc", "inputs/eurostag-tutorial-example1-rtc.txt");
        assertEqualsToRef(dataSource, "_network_ptc", "inputs/eurostag-tutorial-example1-ptc.txt");
        assertEqualsToRef(dataSource, "_network_loads", "inputs/eurostag-tutorial-example1-loads.txt");
        assertEqualsToRef(dataSource, "_network_generators", "inputs/eurostag-tutorial-example1-generators.txt");
        assertEqualsToRef(dataSource, "_network_limits", "inputs/eurostag-tutorial-example1-limits.txt");
    }

    @Test
    void writeNetworkWithExtension() throws IOException {
        Network network = Network.create("sim1", "test");

        network.addExtension(FooNetworkExtension.class, new FooNetworkExtension());

        MemDataSource dataSource = new MemDataSource();
        AmplExporter exporter = new AmplExporter();
        exporter.export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "foo-network-extension", "inputs/foo-network-extension.txt");
    }

    @Test
    void writeShunt() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithMultipleConnectedComponents();

        MemDataSource dataSource = new MemDataSource();
        AmplExporter exporter = new AmplExporter();
        exporter.export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_shunts", "inputs/eurostag-tutorial-example1-shunts.txt");
    }

    @Test
    void writeShunt2() throws IOException {
        Network network = ShuntTestCaseFactory.createNonLinear();
        ShuntCompensator sc = network.getShuntCompensator("SHUNT");
        sc.setSectionCount(2);

        MemDataSource dataSource = new MemDataSource();
        AmplExporter exporter = new AmplExporter();
        exporter.export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_shunts", "inputs/shunt-test-case-shunts.txt");
    }

    @Test
    void writeLcc() throws IOException {
        Network network = HvdcTestNetwork.createLcc();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_hvdc", "inputs/hvdc-lcc-test-case.txt");
        assertEqualsToRef(dataSource, "_network_lcc_converter_stations", "inputs/lcc-test-case.txt");

    }

    @Test
    void writePhaseTapChanger() throws IOException {
        Network network = PhaseShifterTestCaseFactory.create();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_ptc", "inputs/ptc-test-case.txt");
    }

    @Test
    void writeSVC() throws IOException {
        Network network = SvcTestCaseFactory.createWithMoreSVCs();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_static_var_compensators", "inputs/svc-test-case.txt");
    }

    @Test
    void writeBattery() throws IOException {
        Network network = BatteryNetworkFactory.create();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_batteries", "inputs/battery-test-batteries.txt");
    }

    @Test
    void writeThreeWindingsTransformer() throws IOException {
        Network network = ThreeWindingsTransformerNetworkFactory.createWithCurrentLimits();
        network.getThreeWindingsTransformer("3WT").getLeg1()
                .newPhaseTapChanger()
                .beginStep()
                .setRho(1)
                .setR(0.1)
                .setX(1.)
                .setB(0.)
                .setG(0.)
                .setAlpha(0)
                .endStep()
                .setTapPosition(0)
                .setLowTapPosition(0)
                .add();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_branches", "inputs/three-windings-transformers-branches.txt");
        assertEqualsToRef(dataSource, "_network_buses", "inputs/three-windings-transformers-buses.txt");
        assertEqualsToRef(dataSource, "_network_rtc", "inputs/three-windings-transformers-rtc.txt");
        assertEqualsToRef(dataSource, "_network_substations", "inputs/three-windings-transformers-substations.txt");
        assertEqualsToRef(dataSource, "_network_tct", "inputs/three-windings-transformers-tct.txt");
        assertEqualsToRef(dataSource, "_network_limits", "inputs/three-windings-transformers-limits.txt");
    }

    @Test
    void writeVsc() throws IOException {
        Network network = HvdcTestNetwork.createVsc();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_hvdc", "inputs/hvdc-vsc-test-case.txt");
        assertEqualsToRef(dataSource, "_network_vsc_converter_stations", "inputs/vsc-test-case.txt");

    }

    @Test
    void writeCurrentLimits() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithCurrentLimits();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_limits", "inputs/current-limits-test-case.txt");
    }

    @Test
    void writeTieLine() throws IOException {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        for (DanglingLine danglingLine : network.getDanglingLines()) {
            danglingLine.newCurrentLimits()
                    .setPermanentLimit(100.0)
                    .beginTemporaryLimit().setName("20'").setValue(120.0).setAcceptableDuration(20 * 60).endTemporaryLimit()
                    .beginTemporaryLimit().setName("10'").setValue(140.0).setAcceptableDuration(10 * 60).endTemporaryLimit()
                    .add();
        }

        Properties properties = new Properties();
        properties.put("iidm.export.ampl.with-xnodes", "true");

        MemDataSource dataSource = new MemDataSource();
        export(network, properties, dataSource);

        assertEqualsToRef(dataSource, "_network_substations", "inputs/eurostag-tutorial-example1-substations-tl.txt");
        assertEqualsToRef(dataSource, "_network_buses", "inputs/eurostag-tutorial-example1-buses-tl.txt");
        assertEqualsToRef(dataSource, "_network_branches", "inputs/eurostag-tutorial-example1-branches-tl.txt");
        assertEqualsToRef(dataSource, "_network_limits", "inputs/eurostag-tutorial-example1-limits-tl.txt");
    }

    @Test
    void writeDanglingLines() throws IOException {
        Network network = DanglingLineNetworkFactory.create();

        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "_network_branches", "inputs/dangling-line-branches.txt");
        assertEqualsToRef(dataSource, "_network_buses", "inputs/dangling-line-buses.txt");
        assertEqualsToRef(dataSource, "_network_limits", "inputs/dangling-line-limits.txt");
        assertEqualsToRef(dataSource, "_network_loads", "inputs/dangling-line-loads.txt");
        assertEqualsToRef(dataSource, "_network_substations", "inputs/dangling-line-substations.txt");
    }

    @Test
    void writeExtensions() throws IOException {
        Network network = HvdcTestNetwork.createLcc();
        HvdcLine l = network.getHvdcLine("L");
        l.addExtension(FooExtension.class, new FooExtension());
        MemDataSource dataSource = new MemDataSource();
        export(network, new Properties(), dataSource);

        assertEqualsToRef(dataSource, "foo-extension", "inputs/foo-extension.txt");
    }

    private void export(Network network, Properties properties, DataSource dataSource) {
        AmplExporter exporter = new AmplExporter();
        exporter.export(network, properties, dataSource);
    }
}
