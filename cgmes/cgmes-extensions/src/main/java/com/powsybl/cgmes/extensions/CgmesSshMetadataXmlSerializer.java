/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.cgmes.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.AbstractExtensionXmlSerializer;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.io.TreeDataReader;
import com.powsybl.commons.io.TreeDataWriter;
import com.powsybl.commons.extensions.XmlReaderContext;
import com.powsybl.commons.extensions.XmlWriterContext;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.NetworkXmlReaderContext;
import com.powsybl.iidm.xml.NetworkXmlWriterContext;

/**
 * @author Luma Zamarreño {@literal <zamarrenolm at aia.es>}
 * @author José Antonio Marqués {@literal <marquesja at aia.es>}
 */
@AutoService(ExtensionXmlSerializer.class)
public class CgmesSshMetadataXmlSerializer extends AbstractExtensionXmlSerializer<Network, CgmesSshMetadata> {

    public CgmesSshMetadataXmlSerializer() {
        super("cgmesSshMetadata", "network", CgmesSshMetadata.class, "cgmesSshMetadata.xsd",
                "http://www.powsybl.org/schema/iidm/ext/cgmes_ssh_metadata/1_0", "csshm");
    }

    @Override
    public void write(CgmesSshMetadata extension, XmlWriterContext context) {
        NetworkXmlWriterContext networkContext = (NetworkXmlWriterContext) context;
        TreeDataWriter writer = networkContext.getWriter();
        writer.writeStringAttribute("id", extension.getId());
        writer.writeStringAttribute("description", extension.getDescription());
        writer.writeIntAttribute("sshVersion", extension.getSshVersion());
        writer.writeStringAttribute("modelingAuthoritySet", extension.getModelingAuthoritySet());
        for (String dep : extension.getDependencies()) {
            writer.writeStartNode(getNamespaceUri(), "dependentOn");
            writer.writeNodeContent(dep);
            writer.writeEndNode();
        }
    }

    @Override
    public CgmesSshMetadata read(Network extendable, XmlReaderContext context) {
        NetworkXmlReaderContext networkContext = (NetworkXmlReaderContext) context;
        TreeDataReader reader = networkContext.getReader();
        CgmesSshMetadataAdder adder = extendable.newExtension(CgmesSshMetadataAdder.class);
        adder.setDescription(reader.readStringAttribute("description"))
                .setId(reader.readStringAttribute("id"))
                .setSshVersion(reader.readIntAttribute("sshVersion"))
                .setModelingAuthoritySet(reader.readStringAttribute("modelingAuthoritySet"));
        reader.readChildNodes(elementName -> {
            if (elementName.equals("dependentOn")) {
                adder.addDependency(reader.readContent());
            } else {
                throw new PowsyblException("Unknown element name '" + elementName + "' in 'cgmesSshMetadata'");
            }
        });
        adder.add();
        return extendable.getExtension(CgmesSshMetadata.class);
    }
}
