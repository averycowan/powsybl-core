/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.powsybl.iidm.xml.IidmXmlConstants.CURRENT_IIDM_XML_VERSION;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
class BatteryXmlTest extends AbstractXmlConverterTest {

    @Test
    void batteryRoundTripTest() throws IOException {
        roundTripXmlTest(BatteryNetworkFactory.create(),
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                getVersionedNetworkPath("batteryRoundTripRef.xml", CURRENT_IIDM_XML_VERSION));

        //backward compatibility
        roundTripAllPreviousVersionedXmlTest("batteryRoundTripRef.xml");
    }
}
