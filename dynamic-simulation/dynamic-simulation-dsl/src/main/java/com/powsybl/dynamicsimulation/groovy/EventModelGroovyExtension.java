/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.dynamicsimulation.groovy;

import com.powsybl.dynamicsimulation.EventModel;

import java.util.List;

/**
 * @author Marcos de Miguel {@literal <demiguelm at aia.es>}
 */
public interface EventModelGroovyExtension extends GroovyExtension<EventModel> {

    List<String> getModelNames();
}
