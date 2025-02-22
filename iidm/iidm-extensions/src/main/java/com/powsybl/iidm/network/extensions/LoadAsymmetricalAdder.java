/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.extensions;

import com.powsybl.commons.extensions.ExtensionAdder;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LoadAsymmetricalAdder extends ExtensionAdder<Load, LoadAsymmetrical> {

    LoadAsymmetricalAdder withConnectionType(LoadConnectionType connectionType);

    LoadAsymmetricalAdder withDeltaPa(double deltaPa);

    LoadAsymmetricalAdder withDeltaQa(double deltaQa);

    LoadAsymmetricalAdder withDeltaPb(double deltaPb);

    LoadAsymmetricalAdder withDeltaQb(double deltaQb);

    LoadAsymmetricalAdder withDeltaPc(double deltaPc);

    LoadAsymmetricalAdder withDeltaQc(double deltaQc);
}
