/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.impl.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.extensions.GeneratorEntsoeCategory;

/**
 * @author Jérémy Labous {@literal <jlabous at silicom.fr>}
 */
public class GeneratorEntsoeCategoryImpl extends AbstractExtension<Generator> implements GeneratorEntsoeCategory {

    private int code;

    private static int checkCode(int code) {
        if (code < 1) {
            throw new IllegalArgumentException("Bad generator ENTSO-E code " + code);
        }
        return code;
    }

    public GeneratorEntsoeCategoryImpl(Generator generator, int code) {
        super(generator);
        this.code = checkCode(code);
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public GeneratorEntsoeCategoryImpl setCode(int code) {
        this.code = checkCode(code);
        return this;
    }
}
