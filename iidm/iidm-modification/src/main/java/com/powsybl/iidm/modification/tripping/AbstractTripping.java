/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.modification.tripping;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.modification.AbstractNetworkModification;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Mathieu Bague {@literal <mathieu.bague at rte-france.com>}
 */
public abstract class AbstractTripping extends AbstractNetworkModification implements Tripping {

    protected final String id;

    protected AbstractTripping(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public String getId() {
        return id;
    }

    @Override
    public void apply(Network network, boolean throwException, ComputationManager computationManager,
                      Reporter reporter) {
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Terminal> terminalsToDisconnect = new HashSet<>();

        traverse(network, switchesToOpen, terminalsToDisconnect);

        switchesToOpen.forEach(s -> s.setOpen(true));
        terminalsToDisconnect.forEach(Terminal::disconnect);
    }

    public void traverseDoubleSidedEquipment(String voltageLevelId, Terminal terminal1, Terminal terminal2, Set<Switch> switchesToOpen, Set<Terminal> terminalsToDisconnect, Set<Terminal> traversedTerminals, String equipmentType) {
        if (voltageLevelId != null) {
            if (voltageLevelId.equals(terminal1.getVoltageLevel().getId())) {
                TrippingTopologyTraverser.traverse(terminal1, switchesToOpen, terminalsToDisconnect, traversedTerminals);
            } else if (voltageLevelId.equals(terminal2.getVoltageLevel().getId())) {
                TrippingTopologyTraverser.traverse(terminal2, switchesToOpen, terminalsToDisconnect, traversedTerminals);
            } else {
                throw new PowsyblException("VoltageLevel '" + voltageLevelId + "' not connected to " + equipmentType + " '" + id + "'");
            }
        } else {
            TrippingTopologyTraverser.traverse(terminal1, switchesToOpen, terminalsToDisconnect, traversedTerminals);
            TrippingTopologyTraverser.traverse(terminal2, switchesToOpen, terminalsToDisconnect, traversedTerminals);
        }
    }
}
