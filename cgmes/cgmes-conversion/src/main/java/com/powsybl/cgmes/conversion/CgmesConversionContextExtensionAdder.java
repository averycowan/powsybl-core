/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.cgmes.conversion;

import com.powsybl.commons.extensions.ExtensionAdder;
import com.powsybl.iidm.network.Network;

/**
 * @author Jérémy Labous {@literal <jlabous at silicom.fr>}
 */
public interface CgmesConversionContextExtensionAdder extends ExtensionAdder<Network, CgmesConversionContextExtension> {

    @Override
    default Class<CgmesConversionContextExtension> getExtensionClass() {
        return CgmesConversionContextExtension.class;
    }

    CgmesConversionContextExtensionAdder withContext(Context context);
}
