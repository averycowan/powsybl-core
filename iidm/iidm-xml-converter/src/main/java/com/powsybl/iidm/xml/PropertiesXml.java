/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.ValidationLevel;
import com.powsybl.iidm.xml.util.IidmXmlUtil;

import javax.xml.stream.XMLStreamException;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Mathieu Bague {@literal <mathieu.bague@rte-france.com>}
 */
public final class PropertiesXml {

    static final String PROPERTY = "property";

    static final String NAME = "name";
    static final String VALUE = "value";

    public static void write(Identifiable<?> identifiable, NetworkXmlWriterContext context) throws XMLStreamException {
        if (identifiable.hasProperty()) {
            for (String name : IidmXmlUtil.sortedNames(identifiable.getPropertyNames(), context.getOptions())) {
                String value = identifiable.getProperty(name);
                context.getWriter().writeEmptyElement(context.getVersion().getNamespaceURI(identifiable.getNetwork().getValidationLevel() == ValidationLevel.STEADY_STATE_HYPOTHESIS), PROPERTY);
                context.getWriter().writeAttribute(NAME, name);
                context.getWriter().writeAttribute(VALUE, value);
            }
        }
    }

    public static void read(Identifiable identifiable, NetworkXmlReaderContext context) {
        read(context).accept(identifiable);
    }

    public static <T extends Identifiable> void read(List<Consumer<T>> toApply, NetworkXmlReaderContext context) {
        toApply.add(read(context));
    }

    private static <T extends Identifiable> Consumer<T> read(NetworkXmlReaderContext context) {
        if (!context.getReader().getLocalName().equals(PROPERTY)) {
            throw new IllegalStateException();
        }
        String name = context.getReader().getAttributeValue(null, NAME);
        String value = context.getReader().getAttributeValue(null, VALUE);
        return identifiable -> identifiable.setProperty(name, value);
    }

    private PropertiesXml() {
    }
}
