/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.extensions;

import com.powsybl.commons.extensions.ExtensionAdder;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Miora Ralambotiana {@literal <miora.ralambotiana at rte-france.com>}
 */
public interface TwoWindingsTransformerToBeEstimatedAdder extends ExtensionAdder<TwoWindingsTransformer, TwoWindingsTransformerToBeEstimated> {

    @Override
    default Class<? super TwoWindingsTransformerToBeEstimated> getExtensionClass() {
        return TwoWindingsTransformerToBeEstimated.class;
    }

    TwoWindingsTransformerToBeEstimatedAdder withRatioTapChangerStatus(boolean toBeEstimated);

    TwoWindingsTransformerToBeEstimatedAdder withPhaseTapChangerStatus(boolean toBeEstimated);
}
