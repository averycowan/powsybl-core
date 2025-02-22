/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.test.ShuntTestCaseFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static com.powsybl.iidm.xml.IidmXmlConstants.CURRENT_IIDM_XML_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Miora Ralambotiana {@literal <miora.ralambotiana at rte-france.com>}
 */
class ShuntCompensatorXmlTest extends AbstractXmlConverterTest {

    @Test
    void linearShuntTest() throws IOException {
        Network network = ShuntTestCaseFactory.createWithActivePower();
        ShuntCompensator sc = network.getShuntCompensator("SHUNT");
        sc.setProperty("test", "test");
        roundTripXmlTest(network,
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                getVersionedNetworkPath("shuntRoundTripRef.xml", CURRENT_IIDM_XML_VERSION));

        // backward compatibility
        roundTripVersionedXmlFromMinToCurrentVersionTest("shuntRoundTripRef.xml", IidmXmlVersion.V_1_2);
    }

    @Test
    void nonLinearShuntTest() throws IOException {
        Network network = ShuntTestCaseFactory.createNonLinear();
        ShuntCompensator sc = network.getShuntCompensator("SHUNT");
        sc.setProperty("test", "test");
        roundTripXmlTest(network,
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                getVersionedNetworkPath("nonLinearShuntRoundTripRef.xml", CURRENT_IIDM_XML_VERSION));

        // backward compatibility from version 1.2
        roundTripVersionedXmlFromMinToCurrentVersionTest("nonLinearShuntRoundTripRef.xml", IidmXmlVersion.V_1_3);

        // check that it fails for versions previous to 1.2
        testForAllPreviousVersions(IidmXmlVersion.V_1_3, version -> {
            try {
                ExportOptions options = new ExportOptions().setVersion(version.toString("."));
                NetworkXml.write(network, options, tmpDir.resolve("fail"));
                fail();
            } catch (PowsyblException e) {
                assertEquals("shunt.shuntNonLinearModel is not supported for IIDM-XML version " + version.toString(".") + ". IIDM-XML version should be >= 1.3",
                        e.getMessage());
            }
        });

        // check that it doesn't fail for versions previous to 1.2 when log error is the IIDM version incompatibility behavior
        testForAllPreviousVersions(IidmXmlVersion.V_1_3, version -> {
            try {
                writeXmlTest(network, (n, p) -> write(n, p, version), getVersionedNetworkPath("nonLinearShuntRoundTripRef.xml", version));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void write(Network network, Path path, IidmXmlVersion version) {
        ExportOptions options = new ExportOptions().setIidmVersionIncompatibilityBehavior(ExportOptions.IidmVersionIncompatibilityBehavior.LOG_ERROR)
                .setVersion(version.toString("."));
        NetworkXml.write(network, options, path);
    }

    @Test
    void unsupportedWriteTest() {
        Network network = ShuntTestCaseFactory.create();
        testForAllPreviousVersions(IidmXmlVersion.V_1_2, v -> write(network, v.toString(".")));
    }

    @Test
    void nullBPerSection() {
        Network network = ShuntTestCaseFactory.create(0.0);
        Path path = tmpDir.resolve("shunt.xml");

        NetworkXml.write(network, new ExportOptions().setVersion(IidmXmlVersion.V_1_4.toString(".")), path);
        Network n = NetworkXml.read(path);
        ShuntCompensator sc = n.getShuntCompensator("SHUNT");
        assertEquals(Double.MIN_NORMAL, sc.getModel(ShuntCompensatorLinearModel.class).getBPerSection(), 0.0);

        network.getShuntCompensator("SHUNT").setVoltageRegulatorOn(false).setTargetV(Double.NaN).setTargetDeadband(Double.NaN).setRegulatingTerminal(null);
        NetworkXml.write(network, new ExportOptions().setVersion(IidmXmlVersion.V_1_1.toString(".")), path);
        Network n2 = NetworkXml.read(path);
        ShuntCompensator sc2 = n2.getShuntCompensator("SHUNT");
        assertEquals(Double.MIN_NORMAL, sc2.getModel(ShuntCompensatorLinearModel.class).getBPerSection(), 0.0);
    }

    private void write(Network network, String version) {
        try {
            ExportOptions options = new ExportOptions().setVersion(version);
            NetworkXml.write(network, options, tmpDir.resolve("fail.xml"));
            fail();
        } catch (PowsyblException e) {
            assertEquals("shunt.voltageRegulatorOn is not defined as default and not supported for IIDM-XML version " +
                            version + ". IIDM-XML version should be >= 1.2",
                    e.getMessage());
        }
    }
}
