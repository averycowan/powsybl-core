/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.impl.util.Ref;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class StaticVarCompensatorAdderImpl extends AbstractInjectionAdder<StaticVarCompensatorAdderImpl> implements StaticVarCompensatorAdder {

    private final VoltageLevelExt vl;

    private double bMin = Double.NaN;

    private double bMax = Double.NaN;

    private double voltageSetpoint = Double.NaN;

    private double reactivePowerSetpoint = Double.NaN;

    private StaticVarCompensator.RegulationMode regulationMode;

    private TerminalExt regulatingTerminal;

    StaticVarCompensatorAdderImpl(VoltageLevelExt vl) {
        this.vl = Objects.requireNonNull(vl);
    }

    @Override
    protected NetworkImpl getNetwork() {
        return vl.getNetwork();
    }

    @Override
    protected String getTypeDescription() {
        return StaticVarCompensatorImpl.TYPE_DESCRIPTION;
    }

    @Override
    public StaticVarCompensatorAdderImpl setBmin(double bMin) {
        this.bMin = bMin;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setBmax(double bMax) {
        this.bMax = bMax;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setVoltageSetpoint(double voltageSetpoint) {
        this.voltageSetpoint = voltageSetpoint;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setReactivePowerSetpoint(double reactivePowerSetpoint) {
        this.reactivePowerSetpoint = reactivePowerSetpoint;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setRegulationMode(StaticVarCompensator.RegulationMode regulationMode) {
        this.regulationMode = regulationMode;
        return this;
    }

    @Override
    public StaticVarCompensatorAdderImpl setRegulatingTerminal(Terminal regulatingTerminal) {
        this.regulatingTerminal = (TerminalExt) regulatingTerminal;
        return this;
    }

    @Override
    protected Ref<? extends VariantManagerHolder> getVariantManagerHolder() {
        return getNetworkRef();
    }

    private Ref<NetworkImpl> getNetworkRef() {
        return vl.getNetworkRef();
    }

    @Override
    public StaticVarCompensatorImpl add() {
        NetworkImpl network = getNetwork();
        String id = checkAndGetUniqueId();
        String name = getName();
        TerminalExt terminal = checkAndGetTerminal();
        ValidationUtil.checkBmin(this, bMin);
        ValidationUtil.checkBmax(this, bMax);
        ValidationUtil.checkRegulatingTerminal(this, regulatingTerminal, network);
        network.setValidationLevelIfGreaterThan(ValidationUtil.checkSvcRegulator(this, voltageSetpoint, reactivePowerSetpoint, regulationMode, network.getMinValidationLevel()));
        StaticVarCompensatorImpl svc = new StaticVarCompensatorImpl(id, name, isFictitious(), bMin, bMax, voltageSetpoint, reactivePowerSetpoint,
                regulationMode, regulatingTerminal != null ? regulatingTerminal : terminal,
                getNetworkRef());
        svc.addTerminal(terminal);
        vl.attach(terminal, false);
        network.getIndex().checkAndAdd(svc);
        network.getListeners().notifyCreation(svc);
        return svc;
    }

}
