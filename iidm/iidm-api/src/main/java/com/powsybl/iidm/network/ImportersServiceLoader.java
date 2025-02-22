/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network;

import com.powsybl.commons.util.ServiceLoaderCache;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ImportersServiceLoader implements ImportersLoader {

    private static final ServiceLoaderCache<Importer> IMPORTER_LOADER = new ServiceLoaderCache<>(Importer.class);

    private static final ServiceLoaderCache<ImportPostProcessor> POST_PROCESSOR_LOADER = new ServiceLoaderCache<>(ImportPostProcessor.class);

    @Override
    public List<Importer> loadImporters() {
        return IMPORTER_LOADER.getServices();
    }

    @Override
    public List<ImportPostProcessor> loadPostProcessors() {
        return POST_PROCESSOR_LOADER.getServices();
    }
}
