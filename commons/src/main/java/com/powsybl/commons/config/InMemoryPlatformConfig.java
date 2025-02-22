/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.commons.config;

import com.powsybl.commons.io.FileUtil;

import java.nio.file.FileSystem;

/**
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class InMemoryPlatformConfig extends PlatformConfig {

    public InMemoryPlatformConfig(FileSystem fileSystem) {
        super(new InMemoryModuleConfigRepository(fileSystem),
                FileUtil.createDirectory(fileSystem.getPath("inmemory").toAbsolutePath()));
    }

    public MapModuleConfig createModuleConfig(String name) {
        return ((InMemoryModuleConfigRepository) getRepository()).createModuleConfig(name);
    }
}
