/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.commons.exceptions.UncheckedXmlStreamException;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.util.Networks;
import com.powsybl.iidm.xml.util.IidmXmlUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.powsybl.iidm.xml.PropertiesXml.NAME;
import static com.powsybl.iidm.xml.PropertiesXml.VALUE;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class VoltageLevelXml extends AbstractSimpleIdentifiableXml<VoltageLevel, VoltageLevelAdder, Container<? extends Identifiable<?>>> {

    static final VoltageLevelXml INSTANCE = new VoltageLevelXml();

    static final String ROOT_ELEMENT_NAME = "voltageLevel";

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageLevelXml.class);

    private static final String NODE_BREAKER_TOPOLOGY_ELEMENT_NAME = "nodeBreakerTopology";
    private static final String BUS_BREAKER_TOPOLOGY_ELEMENT_NAME = "busBreakerTopology";
    private static final String NODE_COUNT = "nodeCount";
    private static final String UNEXPECTED_ELEMENT = "Unexpected element: ";

    @Override
    protected String getRootElementName() {
        return ROOT_ELEMENT_NAME;
    }

    @Override
    protected boolean hasSubElements(VoltageLevel vl) {
        return true;
    }

    @Override
    protected void writeRootElementAttributes(VoltageLevel vl, Container<? extends Identifiable<?>> c, NetworkXmlWriterContext context) throws XMLStreamException {
        XmlUtil.writeDouble("nominalV", vl.getNominalV(), context.getWriter());
        XmlUtil.writeDouble("lowVoltageLimit", vl.getLowVoltageLimit(), context.getWriter());
        XmlUtil.writeDouble("highVoltageLimit", vl.getHighVoltageLimit(), context.getWriter());

        TopologyLevel topologyLevel = TopologyLevel.min(vl.getTopologyKind(), context.getOptions().getTopologyLevel());
        context.getWriter().writeAttribute("topologyKind", topologyLevel.getTopologyKind().name());
    }

    @Override
    protected void writeSubElements(VoltageLevel vl, Container<? extends Identifiable<?>> c, NetworkXmlWriterContext context) throws XMLStreamException {
        TopologyLevel topologyLevel = TopologyLevel.min(vl.getTopologyKind(), context.getOptions().getTopologyLevel());
        switch (topologyLevel) {
            case NODE_BREAKER:
                writeNodeBreakerTopology(vl, context);
                break;

            case BUS_BREAKER:
                writeBusBreakerTopology(vl, context);
                break;

            case BUS_BRANCH:
                writeBusBranchTopology(vl, context);
                break;

            default:
                throw new IllegalStateException("Unexpected TopologyLevel value: " + topologyLevel);
        }

        writeGenerators(vl, context);
        writeBatteries(vl, context);
        writeLoads(vl, context);
        writeShuntCompensators(vl, context);
        writeDanglingLines(vl, context);
        writeStaticVarCompensators(vl, context);
        writeVscConverterStations(vl, context);
        writeLccConverterStations(vl, context);
    }

    private void writeNodeBreakerTopology(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        context.getWriter().writeStartElement(context.getVersion().getNamespaceURI(context.isValid()), NODE_BREAKER_TOPOLOGY_ELEMENT_NAME);
        IidmXmlUtil.writeIntAttributeUntilMaximumVersion(NODE_COUNT, vl.getNodeBreakerView().getMaximumNodeIndex() + 1, IidmXmlVersion.V_1_1, context);
        for (BusbarSection bs : IidmXmlUtil.sorted(vl.getNodeBreakerView().getBusbarSections(), context.getOptions())) {
            BusbarSectionXml.INSTANCE.write(bs, null, context);
        }
        for (Switch sw : IidmXmlUtil.sorted(vl.getNodeBreakerView().getSwitches(), context.getOptions())) {
            NodeBreakerViewSwitchXml.INSTANCE.write(sw, vl, context);
        }
        writeNodeBreakerTopologyInternalConnections(vl, context);

        IidmXmlUtil.runFromMinimumVersion(IidmXmlVersion.V_1_1, context, () -> {
            Map<String, Set<Integer>> nodesByBus = Networks.getNodesByBus(vl);
            IidmXmlUtil.sorted(vl.getBusView().getBusStream(), context.getOptions())
                    .filter(bus -> !Double.isNaN(bus.getV()) || !Double.isNaN(bus.getAngle()))
                    .forEach(bus -> {
                        Set<Integer> nodes = nodesByBus.get(bus.getId());
                        writeCalculatedBus(bus, nodes, context);
                    });
        });
        IidmXmlUtil.runFromMinimumVersion(IidmXmlVersion.V_1_8, context, () -> {
            for (int node : vl.getNodeBreakerView().getNodes()) {
                double fictP0 = vl.getNodeBreakerView().getFictitiousP0(node);
                double fictQ0 = vl.getNodeBreakerView().getFictitiousQ0(node);
                if (fictP0 != 0.0 || fictQ0 != 0.0) {
                    context.getWriter().writeEmptyElement(context.getVersion().getNamespaceURI(context.isValid()), "inj");
                    XmlUtil.writeInt("node", node, context.getWriter());
                    XmlUtil.writeOptionalDouble("fictitiousP0", fictP0, 0.0, context.getWriter());
                    XmlUtil.writeOptionalDouble("fictitiousQ0", fictQ0, 0.0, context.getWriter());
                }
            }
        });
        context.getWriter().writeEndElement();
    }

    private static void writeCalculatedBus(Bus bus, Set<Integer> nodes, NetworkXmlWriterContext context) {
        try {
            boolean writeProperties = context.getVersion().compareTo(IidmXmlVersion.V_1_11) >= 0 && bus.hasProperty();
            if (writeProperties) {
                context.getWriter().writeStartElement(context.getVersion().getNamespaceURI(context.isValid()), "bus");
            } else {
                context.getWriter().writeEmptyElement(context.getVersion().getNamespaceURI(context.isValid()), "bus");
            }
            XmlUtil.writeDouble("v", bus.getV(), context.getWriter());
            XmlUtil.writeDouble("angle", bus.getAngle(), context.getWriter());
            context.getWriter().writeAttribute("nodes", StringUtils.join(nodes.toArray(), ','));
            if (writeProperties) {
                PropertiesXml.write(bus, context);
                context.getWriter().writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private void writeNodeBreakerTopologyInternalConnections(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (VoltageLevel.NodeBreakerView.InternalConnection ic : IidmXmlUtil.sortedInternalConnections(vl.getNodeBreakerView().getInternalConnections(), context.getOptions())) {
            NodeBreakerViewInternalConnectionXml.INSTANCE.write(ic.getNode1(), ic.getNode2(), context);
        }
    }

    private void writeBusBreakerTopology(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        context.getWriter().writeStartElement(context.getVersion().getNamespaceURI(context.isValid()), BUS_BREAKER_TOPOLOGY_ELEMENT_NAME);
        for (Bus b : IidmXmlUtil.sorted(vl.getBusBreakerView().getBuses(), context.getOptions())) {
            if (!context.getFilter().test(b)) {
                continue;
            }
            BusXml.INSTANCE.write(b, null, context);
        }
        for (Switch sw : IidmXmlUtil.sorted(vl.getBusBreakerView().getSwitches(), context.getOptions())) {
            Bus b1 = vl.getBusBreakerView().getBus1(sw.getId());
            Bus b2 = vl.getBusBreakerView().getBus2(sw.getId());
            if (!context.getFilter().test(b1) || !context.getFilter().test(b2)) {
                continue;
            }
            BusBreakerViewSwitchXml.INSTANCE.write(sw, vl, context);
        }
        context.getWriter().writeEndElement();
    }

    private void writeBusBranchTopology(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        context.getWriter().writeStartElement(context.getVersion().getNamespaceURI(context.isValid()), BUS_BREAKER_TOPOLOGY_ELEMENT_NAME);
        for (Bus b : IidmXmlUtil.sorted(vl.getBusView().getBuses(), context.getOptions())) {
            if (!context.getFilter().test(b)) {
                continue;
            }
            BusXml.INSTANCE.write(b, null, context);
        }
        context.getWriter().writeEndElement();
    }

    private void writeGenerators(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (Generator g : IidmXmlUtil.sorted(vl.getGenerators(), context.getOptions())) {
            if (!context.getFilter().test(g)) {
                continue;
            }
            GeneratorXml.INSTANCE.write(g, vl, context);
        }
    }

    private void writeBatteries(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (Battery b : IidmXmlUtil.sorted(vl.getBatteries(), context.getOptions())) {
            if (!context.getFilter().test(b)) {
                continue;
            }
            BatteryXml.INSTANCE.write(b, vl, context);
        }
    }

    private void writeLoads(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (Load l : IidmXmlUtil.sorted(vl.getLoads(), context.getOptions())) {
            if (!context.getFilter().test(l)) {
                continue;
            }
            LoadXml.INSTANCE.write(l, vl, context);
        }
    }

    private void writeShuntCompensators(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (ShuntCompensator sc : IidmXmlUtil.sorted(vl.getShuntCompensators(), context.getOptions())) {
            if (!context.getFilter().test(sc)) {
                continue;
            }
            ShuntXml.INSTANCE.write(sc, vl, context);
        }
    }

    private void writeDanglingLines(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (DanglingLine dl : IidmXmlUtil.sorted(vl.getDanglingLines(DanglingLineFilter.ALL), context.getOptions())) {
            if (!context.getFilter().test(dl) || context.getVersion().compareTo(IidmXmlVersion.V_1_10) < 0 && dl.isPaired()) {
                continue;
            }
            DanglingLineXml.INSTANCE.write(dl, vl, context);
        }
    }

    private void writeStaticVarCompensators(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (StaticVarCompensator svc : IidmXmlUtil.sorted(vl.getStaticVarCompensators(), context.getOptions())) {
            if (!context.getFilter().test(svc)) {
                continue;
            }
            StaticVarCompensatorXml.INSTANCE.write(svc, vl, context);
        }
    }

    private void writeVscConverterStations(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (VscConverterStation cs : IidmXmlUtil.sorted(vl.getVscConverterStations(), context.getOptions())) {
            if (!context.getFilter().test(cs)) {
                continue;
            }
            VscConverterStationXml.INSTANCE.write(cs, vl, context);
        }
    }

    private void writeLccConverterStations(VoltageLevel vl, NetworkXmlWriterContext context) throws XMLStreamException {
        for (LccConverterStation cs : IidmXmlUtil.sorted(vl.getLccConverterStations(), context.getOptions())) {
            if (!context.getFilter().test(cs)) {
                continue;
            }
            LccConverterStationXml.INSTANCE.write(cs, vl, context);
        }
    }

    @Override
    protected VoltageLevelAdder createAdder(Container<? extends Identifiable<?>> c) {
        if (c instanceof Network network) {
            return network.newVoltageLevel();
        }
        if (c instanceof Substation substation) {
            return substation.newVoltageLevel();
        }
        throw new IllegalStateException();
    }

    @Override
    protected VoltageLevel readRootElementAttributes(VoltageLevelAdder adder, Container<? extends Identifiable<?>> c, NetworkXmlReaderContext context) {
        double nominalV = XmlUtil.readDoubleAttribute(context.getReader(), "nominalV");
        double lowVoltageLimit = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "lowVoltageLimit");
        double highVoltageLimit = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "highVoltageLimit");
        TopologyKind topologyKind = TopologyKind.valueOf(context.getReader().getAttributeValue(null, "topologyKind"));
        return adder
                .setNominalV(nominalV)
                .setLowVoltageLimit(lowVoltageLimit)
                .setHighVoltageLimit(highVoltageLimit)
                .setTopologyKind(topologyKind)
                .add();
    }

    @Override
    protected void readSubElements(VoltageLevel vl, NetworkXmlReaderContext context) throws XMLStreamException {
        readUntilEndRootElement(context.getReader(), () -> {
            switch (context.getReader().getLocalName()) {
                case NODE_BREAKER_TOPOLOGY_ELEMENT_NAME:
                    readNodeBreakerTopology(vl, context);
                    break;

                case BUS_BREAKER_TOPOLOGY_ELEMENT_NAME:
                    readBusBreakerTopology(vl, context);
                    break;

                case GeneratorXml.ROOT_ELEMENT_NAME:
                    GeneratorXml.INSTANCE.read(vl, context);
                    break;

                case BatteryXml.ROOT_ELEMENT_NAME:
                    BatteryXml.INSTANCE.read(vl, context);
                    break;

                case LoadXml.ROOT_ELEMENT_NAME:
                    LoadXml.INSTANCE.read(vl, context);
                    break;

                case ShuntXml.ROOT_ELEMENT_NAME:
                    ShuntXml.INSTANCE.read(vl, context);
                    break;

                case DanglingLineXml.ROOT_ELEMENT_NAME:
                    DanglingLineXml.INSTANCE.read(vl, context);
                    break;

                case StaticVarCompensatorXml.ROOT_ELEMENT_NAME:
                    StaticVarCompensatorXml.INSTANCE.read(vl, context);
                    break;

                case VscConverterStationXml.ROOT_ELEMENT_NAME:
                    VscConverterStationXml.INSTANCE.read(vl, context);
                    break;

                case LccConverterStationXml.ROOT_ELEMENT_NAME:
                    LccConverterStationXml.INSTANCE.read(vl, context);
                    break;

                default:
                    super.readSubElements(vl, context);
            }
        });
    }

    private void readNodeBreakerTopology(VoltageLevel vl, NetworkXmlReaderContext context) throws XMLStreamException {
        IidmXmlUtil.runUntilMaximumVersion(IidmXmlVersion.V_1_1, context, () -> LOGGER.trace("attribute " + NODE_BREAKER_TOPOLOGY_ELEMENT_NAME + ".nodeCount is ignored."));
        XmlUtil.readUntilEndElement(NODE_BREAKER_TOPOLOGY_ELEMENT_NAME, context.getReader(), () -> {
            switch (context.getReader().getLocalName()) {
                case BusbarSectionXml.ROOT_ELEMENT_NAME:
                    BusbarSectionXml.INSTANCE.read(vl, context);
                    break;

                case AbstractSwitchXml.ROOT_ELEMENT_NAME:
                    NodeBreakerViewSwitchXml.INSTANCE.read(vl, context);
                    break;

                case NodeBreakerViewInternalConnectionXml.ROOT_ELEMENT_NAME:
                    NodeBreakerViewInternalConnectionXml.INSTANCE.read(vl, context);
                    break;

                case BusXml.ROOT_ELEMENT_NAME:
                    readCalculatedBus(vl, context);
                    break;

                case "inj":
                    readFictitiousInjection(vl, context);
                    break;

                default:
                    throw new IllegalStateException(UNEXPECTED_ELEMENT + context.getReader().getLocalName());
            }
        });
    }

    private void readCalculatedBus(VoltageLevel vl, NetworkXmlReaderContext context) throws XMLStreamException {
        IidmXmlUtil.assertMinimumVersion(ROOT_ELEMENT_NAME, BusXml.ROOT_ELEMENT_NAME, IidmXmlUtil.ErrorMessage.NOT_SUPPORTED, IidmXmlVersion.V_1_1, context);
        double v = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "v");
        double angle = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "angle");
        String nodesString = context.getReader().getAttributeValue(null, "nodes");
        Map<String, String> properties = new HashMap<>();
        XmlUtil.readUntilEndElement(BusXml.ROOT_ELEMENT_NAME, context.getReader(), () -> {
            if (context.getReader().getLocalName().equals(PropertiesXml.PROPERTY)) {
                String name = context.getReader().getAttributeValue(null, NAME);
                String value = context.getReader().getAttributeValue(null, VALUE);
                properties.put(name, value);
            } else {
                throw new IllegalStateException(UNEXPECTED_ELEMENT + context.getReader().getLocalName());
            }
        });
        context.getEndTasks().add(() -> {
            for (String str : nodesString.split(",")) {
                int node = Integer.parseInt(str);
                Terminal terminal = vl.getNodeBreakerView().getTerminal(node);
                if (terminal != null) {
                    Bus b = terminal.getBusView().getBus();
                    if (b != null) {
                        b.setV(v).setAngle(angle);
                        properties.forEach(b::setProperty);
                        break;
                    }
                }
            }
        });
    }

    private void readFictitiousInjection(VoltageLevel vl, NetworkXmlReaderContext context) {
        IidmXmlUtil.assertMinimumVersion(ROOT_ELEMENT_NAME, "inj", IidmXmlUtil.ErrorMessage.NOT_SUPPORTED, IidmXmlVersion.V_1_8, context);
        int node = XmlUtil.readIntAttribute(context.getReader(), "node");
        double p0 = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "fictitiousP0");
        double q0 = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "fictitiousQ0");
        if (!Double.isNaN(p0)) {
            vl.getNodeBreakerView().setFictitiousP0(node, p0);
        }
        if (!Double.isNaN(q0)) {
            vl.getNodeBreakerView().setFictitiousQ0(node, q0);
        }
    }

    private void readBusBreakerTopology(VoltageLevel vl, NetworkXmlReaderContext context) throws XMLStreamException {
        XmlUtil.readUntilEndElement(BUS_BREAKER_TOPOLOGY_ELEMENT_NAME, context.getReader(), () -> {
            switch (context.getReader().getLocalName()) {
                case BusXml.ROOT_ELEMENT_NAME:
                    BusXml.INSTANCE.read(vl, context);
                    break;

                case AbstractSwitchXml.ROOT_ELEMENT_NAME:
                    BusBreakerViewSwitchXml.INSTANCE.read(vl, context);
                    break;

                default:
                    throw new IllegalStateException(UNEXPECTED_ELEMENT + context.getReader().getLocalName());
            }
        });
    }
}
