/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.commons.datasource;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class MemFileDataSourceTest extends AbstractDataSourceTest {

    @Override
    protected String getBaseName() {
        return ""; // because undefined in a memory impl datasource
    }

    @Override
    protected DataSource createDataSource() {
        return new MemDataSource();
    }
}
