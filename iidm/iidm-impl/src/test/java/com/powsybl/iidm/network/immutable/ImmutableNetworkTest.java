/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.immutable;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.*;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static com.powsybl.iidm.network.immutable.ImmutableTestHelper.testInvalidMethods;
import static org.junit.Assert.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ImmutableNetworkTest {

    @Test
    public void test() {
        Network n = EurostagTutorialExample1Factory.create();
        Network network = ImmutableNetwork.of(n);
        Set<String> expectedInvalidMethods = new HashSet<>();
        expectedInvalidMethods.add("setForecastDistance");
        expectedInvalidMethods.add("setCaseDate");
        expectedInvalidMethods.add("newSubstation");
        expectedInvalidMethods.add("newLine");
        expectedInvalidMethods.add("newTieLine");
        expectedInvalidMethods.add("newHvdcLine");
        testInvalidMethods(network, expectedInvalidMethods);

        Substation sub = network.getSubstation("P1");
        Substation sub2 = network.getSubstation("P1");
        assertSame(sub, sub2);
        Identifiable<?> p1 = network.getIdentifiable("P1");
        assertSame(sub, p1);
        assertTrue(sub instanceof ImmutableSubstation);
        assertSame(network, sub.getNetwork());
        Set<String> mutalbeMethods = new HashSet<>();
        mutalbeMethods.add("addGeographicalTag");
        Set<String> invalidSubMethods = new HashSet<>(mutalbeMethods);
        invalidSubMethods.add("setTso");
        invalidSubMethods.add("setCountry");
        invalidSubMethods.add("newVoltageLevel");
        invalidSubMethods.add("newTwoWindingsTransformer");
        invalidSubMethods.add("newThreeWindingsTransformer");
        invalidSubMethods.add("remove");
        testInvalidMethods(sub, invalidSubMethods, mutalbeMethods);

        VoltageLevel vl = network.getVoltageLevel("VLGEN");
        assertTrue(vl instanceof ImmutableVoltageLevel);
        assertSame(sub, vl.getSubstation());
        Identifiable<?> vlgen = network.getIdentifiable("VLGEN");
        assertSame(vl, vlgen);
        Set<String> invalidVlMethods = new HashSet<>();
        invalidVlMethods.add("setNominalV");
        invalidVlMethods.add("setLowVoltageLimit");
        invalidVlMethods.add("setHighVoltageLimit");
        invalidVlMethods.add("newGenerator");
        invalidVlMethods.add("newBattery");
        invalidVlMethods.add("newLoad");
        invalidVlMethods.add("newShuntCompensator");
        invalidVlMethods.add("newDanglingLine");
        invalidVlMethods.add("newStaticVarCompensator");
        invalidVlMethods.add("newVscConverterStation");
        invalidVlMethods.add("newLccConverterStation");
        invalidVlMethods.add("remove");
        testInvalidMethods(vl, invalidVlMethods);

        VoltageLevel.NodeBreakerView nodeBreakerView = vl.getNodeBreakerView();
        Set<String> nbvMutableMethods = new HashSet<>();
        nbvMutableMethods.add("removeSwitch");
        Set<String> invalidVlNbvMethods = new HashSet<>();
        invalidVlNbvMethods.add("setNodeCount");
        invalidVlNbvMethods.add("newSwitch");
        invalidVlNbvMethods.add("newInternalConnection");
        invalidVlNbvMethods.add("newBreaker");
        invalidVlNbvMethods.add("newDisconnector");
        invalidVlNbvMethods.add("removeSwitch");
        invalidVlNbvMethods.add("newBusbarSection");
        testInvalidMethods(nodeBreakerView, invalidVlNbvMethods, nbvMutableMethods);

        VoltageLevel.BusBreakerView busBreakerView = vl.getBusBreakerView();
        Set<String> bbvMutableMethods = new HashSet<>();
        bbvMutableMethods.add("removeSwitch");
        bbvMutableMethods.add("removeAllSwitches");
        bbvMutableMethods.add("removeBus");
        bbvMutableMethods.add("removeAllBuses");
        Set<String> invalidBbvMethods = new HashSet<>(bbvMutableMethods);
        invalidBbvMethods.add("newBus");
        invalidBbvMethods.add("newSwitch");
        testInvalidMethods(busBreakerView, invalidBbvMethods, bbvMutableMethods);

        assertEquals(n.getIdentifiables().size(), network.getIdentifiables().size());
    }

    @Test
    public void testVariantManager() {
        Network n = EurostagTutorialExample1Factory.create();
        Network network = ImmutableNetwork.of(n);
        n.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "foo");
        n.getVariantManager().setWorkingVariant("foo");

        try {
            network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "bar");
        } catch (Exception e) {
            assertEquals("Unmodifiable identifiable", e.getMessage());
        }
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
    }

    @Test
    public void testImmutableTerminalAndBus() {
        Network n = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        Terminal t = n.getLine("NHV1_NHV2_1").getTerminal1();
        Bus b = t.getBusView().getBus();
        Component c = b.getConnectedComponent();

        Network immutableNetwork = ImmutableNetwork.of(n);
        Terminal immutableTerminal = immutableNetwork.getLine("NHV1_NHV2_1").getTerminal1();
        assertTrue(immutableTerminal instanceof ImmutableTerminal);
        Bus immutableBus = immutableTerminal.getBusView().getBus();
        assertTrue(immutableBus instanceof ImmutableBus);
        Component immutableComponent = immutableBus.getConnectedComponent();
        assertTrue(immutableComponent instanceof ImmutableComponent);
        assertTrue(immutableBus.getVoltageLevel() instanceof ImmutableVoltageLevel);

        Set<String> invalidsTerminalMethods = new HashSet<>();
        invalidsTerminalMethods.add("setP");
        invalidsTerminalMethods.add("setQ");
        invalidsTerminalMethods.add("connect");
        invalidsTerminalMethods.add("disconnect");
        ImmutableTestHelper.testInvalidMethods(immutableTerminal, invalidsTerminalMethods);

        Set<String> invalidsBusMethods = new HashSet<>();
        invalidsBusMethods.add("setV");
        invalidsBusMethods.add("setAngle");
        ImmutableTestHelper.testInvalidMethods(immutableBus, invalidsBusMethods);

        assertTrue(immutableTerminal.getBusView().getBus() instanceof ImmutableBus);
        assertTrue(immutableTerminal.getBusView().getConnectableBus() instanceof ImmutableBus);
    }

    @Test
    public void withExtentsions() {
        Network network = MultipleExtensionsTestNetworkFactory.create();
        network.getSubstation("S").addExtension(SubstationShutdownExtension.class, new SubstationShutdownExtension());
        network.getLoad("LOAD").getExtension(LoadFooExt.class).setUsername("foo");
        ImmutableNetwork immutableNetwork = ImmutableNetwork.of(network);
        Load immutableLoad = immutableNetwork.getLoad("LOAD");
        LoadFooExt immutableExt = immutableLoad.getExtension(LoadFooExt.class);
        assertEquals("foo", immutableExt.getUsername());
        assertEquals(immutableLoad, immutableExt.getExtendable());
        try {
            immutableExt.setUsername("foo");
            fail();
        } catch (Exception e) {
            // ignore
        }
        try {
            immutableLoad.addExtension(LoadBarExt.class, new LoadBarExt(immutableLoad));
            fail();
        } catch (Exception e) {
            // ignore
        }
        try {
            immutableExt.setExtendable(network.getLoad("LOAD"));
            fail();
        } catch (Exception e) {
            // ignore
        }
        assertEquals(2, immutableLoad.getExtensions().size());
        SubstationShutdownExtension sdExt = immutableNetwork.getSubstation("S").getExtensionByName("subShutdownExt");
        try {
            sdExt.shutdown();
            fail();
        } catch (Exception e) {
            // ignore
        }
    }
}
