/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.psse.converter.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Network;
import com.powsybl.psse.model.pf.PssePowerFlowModel;

import java.util.Objects;

/**
 * @author Luma Zamarreño {@literal <zamarrenolm at aia.es>}
 * @author José Antonio Marqués {@literal <marquesja at aia.es>}
 */
class PsseModelExtensionImpl extends AbstractExtension<Network> implements PsseModelExtension {

    private final PssePowerFlowModel psseModel;

    PsseModelExtensionImpl(PssePowerFlowModel psseModel) {
        this.psseModel = Objects.requireNonNull(psseModel);
    }

    @Override
    public PssePowerFlowModel getPsseModel() {
        return psseModel;
    }
}
