/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml.anonymizer;

import com.powsybl.iidm.network.Country;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class FakeAnonymizer implements Anonymizer {
    @Override
    public String anonymizeString(String str) {
        return str;
    }

    @Override
    public String deanonymizeString(String str) {
        return str;
    }

    @Override
    public Country anonymizeCountry(Country country) {
        return country;
    }

    @Override
    public Country deanonymizeCountry(Country country) {
        return country;
    }

    @Override
    public void read(BufferedReader reader) {
        // nothing to do in fake impl
    }

    @Override
    public void write(BufferedWriter writer) {
        // nothing to do in fake impl
    }
}
