/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.network.IdentifiableAdder;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.xml.util.IidmXmlUtil;

import javax.xml.stream.XMLStreamException;

/**
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
abstract class AbstractSwitchXml<A extends IdentifiableAdder<Switch, A>> extends AbstractSimpleIdentifiableXml<Switch, A, VoltageLevel> {

    static final String ROOT_ELEMENT_NAME = "switch";

    @Override
    protected String getRootElementName() {
        return ROOT_ELEMENT_NAME;
    }

    @Override
    protected boolean hasSubElements(Switch s) {
        return false;
    }

    @Override
    protected void writeRootElementAttributes(Switch s, VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        context.getWriter().writeAttribute("kind", s.getKind().name());
        context.getWriter().writeAttribute("retained", Boolean.toString(s.isRetained()));
        context.getWriter().writeAttribute("open", Boolean.toString(s.isOpen()));

        IidmXmlUtil.runUntilMaximumVersion(IidmXmlVersion.V_1_1, context, () -> XmlUtil.writeOptionalBoolean("fictitious", s.isFictitious(), false, context.getWriter()));
    }

    @Override
    protected void readSubElements(Switch s, NetworkXmlReaderContext context) throws XMLStreamException {
        readUntilEndRootElement(context.getReader(), () -> AbstractSwitchXml.super.readSubElements(s, context));
    }
}
