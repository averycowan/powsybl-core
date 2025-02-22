/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.test;

import com.powsybl.iidm.network.*;
import org.joda.time.DateTime;

import java.util.Objects;

/**
 * @author Miora Ralambotiana {@literal <miora.ralambotiana at rte-france.com>}
 */
public final class ShuntTestCaseFactory {

    private static final String SHUNT = "SHUNT";

    private ShuntTestCaseFactory() {
    }

    public static Network create() {
        return create(NetworkFactory.findDefault());
    }

    public static Network create(double bPerSection) {
        return create(NetworkFactory.findDefault(), bPerSection);
    }

    public static Network create(NetworkFactory networkFactory) {
        return create(networkFactory, 1e-5);
    }

    public static Network create(NetworkFactory networkFactory, double bPerSection) {
        Network network = createBase(networkFactory);

        network.getVoltageLevel("VL1")
                .newShuntCompensator()
                .setId(SHUNT)
                .setBus("B1")
                .setConnectableBus("B1")
                .setSectionCount(1)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(network.getLoad("LOAD").getTerminal())
                .setTargetV(200)
                .setTargetDeadband(5.0)
                .newLinearModel()
                .setMaximumSectionCount(1)
                .setBPerSection(bPerSection)
                .add()
                .add()
                .addAlias("Alias");

        return network;
    }

    public static Network createWithActivePower(NetworkFactory networkFactory) {
        Network network = create(networkFactory);
        ShuntCompensator s = network.getShuntCompensator(SHUNT);
        s.getTerminal().setP(1.0);
        return network;
    }

    public static Network createWithActivePower() {
        return createWithActivePower(NetworkFactory.findDefault());
    }

    public static Network createNonLinear() {
        return createNonLinear(NetworkFactory.findDefault());
    }

    public static Network createNonLinear(NetworkFactory networkFactory) {
        Network network = createBase(networkFactory);

        network.getVoltageLevel("VL1")
                .newShuntCompensator()
                    .setId(SHUNT)
                    .setBus("B1")
                    .setConnectableBus("B1")
                    .setSectionCount(1)
                    .setVoltageRegulatorOn(true)
                    .setRegulatingTerminal(network.getLoad("LOAD").getTerminal())
                    .setTargetV(200)
                    .setTargetDeadband(5.0)
                    .newNonLinearModel()
                        .beginSection()
                            .setB(1e-5)
                            .setG(0.0)
                        .endSection()
                        .beginSection()
                            .setB(2e-2)
                            .setG(3e-1)
                        .endSection()
                    .add()
                .add();

        return network;
    }

    private static Network createBase(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        Network network = networkFactory.createNetwork("shuntTestCase", "test")
                .setCaseDate(DateTime.parse("2019-09-30T16:29:18.263+02:00"));

        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();

        Substation s2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("VL2")
                .setNominalV(220)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();

        vl2.newLoad()
                .setId("LOAD")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(100.0)
                .setQ0(50.0)
                .add();
        return network;
    }
}
