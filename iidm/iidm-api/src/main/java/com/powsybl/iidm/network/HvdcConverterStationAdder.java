/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network;

/**
 * HVDC converter station builder and adder.
 *
 * @author Mathieu Bague {@literal <mathieu.bague at rte-france.com>}
 */
public interface HvdcConverterStationAdder<T extends HvdcConverterStation<T>, A extends HvdcConverterStationAdder> extends InjectionAdder<T, A> {

    A setLossFactor(float lossFactor);

    @Override
    T add();
}
