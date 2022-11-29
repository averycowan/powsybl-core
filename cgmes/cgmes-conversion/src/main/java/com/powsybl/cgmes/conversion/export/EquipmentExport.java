/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.cgmes.conversion.export;

import com.powsybl.cgmes.conversion.Conversion;
import com.powsybl.cgmes.conversion.NamingStrategy;
import com.powsybl.cgmes.conversion.export.elements.*;
import com.powsybl.cgmes.extensions.*;
import com.powsybl.cgmes.model.CgmesNames;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.exceptions.UncheckedXmlStreamException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.*;

/**
 * @author Marcos de Miguel <demiguelm at aia.es>
 */
public final class EquipmentExport extends AbstractCgmesExporter {

    private static final String AC_DC_CONVERTER_DC_TERMINAL = "ACDCConverterDCTerminal";
    private static final String CONNECTIVITY_NODE_SUFFIX = "CN";
    private static final String PHASE_TAP_CHANGER_REGULATION_MODE_ACTIVE_POWER = "activePower";
    private static final String PHASE_TAP_CHANGER_REGULATION_MODE_CURRENT_FLOW = "currentFlow";
    private static final String RATIO_TAP_CHANGER_REGULATION_MODE_VOLTAGE = "voltage";
    private static final Logger LOG = LoggerFactory.getLogger(EquipmentExport.class);

    private final String euNamespace;
    private final String limitValueAttributeName;
    private final String limitTypeAttributeName;
    private final String limitKindClassName;
    private final boolean writeInfiniteDuration;
    private final boolean writeInitialP;

    private final Map<String, String> mapNodeKey2NodeId = new HashMap<>();
    private final Map<Terminal, String> mapTerminal2Id = new HashMap<>();
    private final Set<String> regulatingControlsWritten = new HashSet<>();

    EquipmentExport(CgmesExportContext context, XMLStreamWriter xmlWriter) {
        super(context, xmlWriter);
        euNamespace = context.getCim().getEuNamespace();
        limitValueAttributeName = context.getCim().getLimitValueAttributeName();
        limitTypeAttributeName = context.getCim().getLimitTypeAttributeName();
        limitKindClassName = context.getCim().getLimitKindClassName();
        writeInfiniteDuration = context.getCim().writeLimitInfiniteDuration();
        writeInitialP = context.getCim().writeGeneratingUnitInitialP();
    }

    @Override
    public void export() {
        context.setExportEquipment(true);
        try {
            CgmesExportUtil.writeRdfRoot(cimNamespace, context.getCim().getEuPrefix(), euNamespace, xmlWriter);

            // TODO fill EQ Model Description
            if (context.getCimVersion() >= 16) {
                ModelDescriptionEq.write(xmlWriter, context.getEqModelDescription(), context);
            }

            if (context.writeConnectivityNodes()) {
                writeConnectivityNodes();
            }
            writeTerminals();
            writeSwitches();

            writeSubstations();
            writeVoltageLevels();
            writeBusbarSections();
            writeLoads();
            writeLoadGroups();
            writeGenerators();
            writeShuntCompensators();
            writeStaticVarCompensators();
            writeLines();
            writeTwoWindingsTransformers();
            writeThreeWindingsTransformers();

            writeDanglingLines();
            writeHvdcLines();

            writeControlAreas();

            xmlWriter.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private void writeConnectivityNodes() throws XMLStreamException {
        for (VoltageLevel vl : context.getNetwork().getVoltageLevels()) {
            if (vl.getTopologyKind().equals(TopologyKind.NODE_BREAKER)) {
                writeNodes(vl, new VoltageLevelAdjacency(vl, context));
            } else {
                writeBuses(vl);
            }
            writeSwitchesConnectivity(vl);
        }
        writeBusbarSectionsConnectivity();
    }

    private void writeSwitchesConnectivity(VoltageLevel vl) {
        String[] nodeKeys = new String[2];
        for (Switch sw : vl.getSwitches()) {
            fillSwitchNodeKeys(vl, sw, nodeKeys);
            // We have to go through all switches, even if they are not exported as equipment,
            // to be sure that all required mappings between IIDM node number and CGMES Connectivity Node are created
            writeSwitchConnectivity(nodeKeys[0], vl);
            writeSwitchConnectivity(nodeKeys[1], vl);
        }
    }

    private void writeSwitchConnectivity(String nodeKey, VoltageLevel vl) {
        mapNodeKey2NodeId.computeIfAbsent(nodeKey, k -> {
            try {
                String node = CgmesExportUtil.getUniqueId();
                exportConnectivityNode(node, nodeKey, context.getNamingStrategy().getCgmesId(vl));
                return node;
            } catch (XMLStreamException e) {
                throw new UncheckedXmlStreamException(e);
            }
        });
    }

    private static String buildNodeKey(VoltageLevel vl, int node) {
        return vl.getId() + "_" + node + "_" + CONNECTIVITY_NODE_SUFFIX;
    }

    private static String buildNodeKey(Bus bus) {
        return bus.getId() + "_" + CONNECTIVITY_NODE_SUFFIX;
    }

    private void exportConnectivityNode(String id, String nodeName, String connectivityNodeContainerId) throws XMLStreamException {
        writeStartIdName("ConnectivityNode", id, nodeName);
        writeReference("ConnectivityNode.ConnectivityNodeContainer", connectivityNodeContainerId);
        xmlWriter.writeEndElement();
    }

    private void writeBusbarSectionsConnectivity() throws XMLStreamException {
        for (BusbarSection bus : context.getNetwork().getBusbarSections()) {
            String connectivityNodeId = connectivityNodeId(bus.getTerminal());
            if (connectivityNodeId == null) {
                VoltageLevel vl = bus.getTerminal().getVoltageLevel();
                String node = CgmesExportUtil.getUniqueId();
                exportConnectivityNode(node, bus.getNameOrId(), context.getNamingStrategy().getCgmesId(vl));
                String key = buildNodeKey(vl, bus.getTerminal().getNodeBreakerView().getNode());
                mapNodeKey2NodeId.put(key, node);
            }
        }
    }

    private void writeNodes(VoltageLevel vl, VoltageLevelAdjacency vlAdjacencies) throws XMLStreamException {
        for (List<Integer> nodes : vlAdjacencies.getNodes()) {
            String cgmesNodeId = CgmesExportUtil.getUniqueId();
            exportConnectivityNode(cgmesNodeId, CgmesExportUtil.format(nodes.get(0)), context.getNamingStrategy().getCgmesId(vl));
            for (Integer nodeNumber : nodes) {
                mapNodeKey2NodeId.put(buildNodeKey(vl, nodeNumber), cgmesNodeId);
            }
        }
    }

    private void writeBuses(VoltageLevel vl) throws XMLStreamException {
        for (Bus bus : vl.getBusBreakerView().getBuses()) {
            String cgmesNodeId = context.getNamingStrategy().getCgmesId(bus, CONNECTIVITY_NODE_SUFFIX);
            exportConnectivityNode(cgmesNodeId, bus.getNameOrId(), context.getNamingStrategy().getCgmesId(vl));
            mapNodeKey2NodeId.put(buildNodeKey(bus), cgmesNodeId);
        }
    }

    private void writeSwitches() throws XMLStreamException {
        for (Switch sw : context.getNetwork().getSwitches()) {
            if (context.isExportedEquipment(sw)) {
                VoltageLevel vl = sw.getVoltageLevel();
                SwitchEq.write(context.getNamingStrategy().getCgmesId(sw), sw.getNameOrId(), sw.getKind(), context.getNamingStrategy().getCgmesId(vl), sw.isOpen(), sw.isRetained(), cimNamespace, xmlWriter);
            }
        }
    }

    private static void fillSwitchNodeKeys(VoltageLevel vl, Switch sw, String[] nodeKeys) {
        if (vl.getTopologyKind().equals(TopologyKind.NODE_BREAKER)) {
            nodeKeys[0] = buildNodeKey(vl, vl.getNodeBreakerView().getNode1(sw.getId()));
            nodeKeys[1] = buildNodeKey(vl, vl.getNodeBreakerView().getNode2(sw.getId()));
        } else {
            nodeKeys[0] = buildNodeKey(vl.getBusBreakerView().getBus1(sw.getId()));
            nodeKeys[1] = buildNodeKey(vl.getBusBreakerView().getBus2(sw.getId()));
        }
    }

    private void writeSubstations() throws XMLStreamException {
        for (String geographicalRegionId : context.getRegionsIds()) {
            // To ensure we always export valid mRIDs, even if input CGMES used invalid ones
            String cgmesRegionId = context.getNamingStrategy().getCgmesId(geographicalRegionId);
            writeGeographicalRegion(cgmesRegionId, context.getRegionName(geographicalRegionId));
        }
        List<String> writtenSubRegions = new ArrayList<>();
        for (Substation substation : context.getNetwork().getSubstations()) {
            String subGeographicalRegionId = context.getNamingStrategy().getCgmesIdFromProperty(substation, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "subRegionId");
            String geographicalRegionId = context.getNamingStrategy().getCgmesIdFromProperty(substation, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "regionId");
            if (!writtenSubRegions.contains(subGeographicalRegionId)) {
                writeSubGeographicalRegion(subGeographicalRegionId, context.getSubRegionName(subGeographicalRegionId), geographicalRegionId);
                writtenSubRegions.add(subGeographicalRegionId);
            }
            SubstationEq.write(context.getNamingStrategy().getCgmesId(substation), substation.getNameOrId(), subGeographicalRegionId, cimNamespace, xmlWriter);
        }
    }

    private void writeGeographicalRegion(String geographicalRegionId, String geoName) {
        try {
            GeographicalRegionEq.write(geographicalRegionId, geoName, cimNamespace, xmlWriter);
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private void writeSubGeographicalRegion(String subGeographicalRegionId, String subGeographicalRegionName, String geographicalRegionId) {
        try {
            SubGeographicalRegionEq.write(subGeographicalRegionId, subGeographicalRegionName, geographicalRegionId, cimNamespace, xmlWriter);
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private void writeVoltageLevels() throws XMLStreamException {
        for (VoltageLevel voltageLevel : context.getNetwork().getVoltageLevels()) {
            double nominalV = voltageLevel.getNominalV();
            BaseVoltageMapping.BaseVoltageSource baseVoltage = context.getBaseVoltageByNominalVoltage(nominalV);
            if (baseVoltage.getSource().equals(Source.IGM) && !context.isExportedBaseVoltageFor(nominalV)) {
                BaseVoltageEq.write(baseVoltage.getId(), nominalV, cimNamespace, xmlWriter);
                context.exportedBaseVoltageFor(nominalV);
            }
            VoltageLevelEq.write(context.getNamingStrategy().getCgmesId(voltageLevel), voltageLevel.getNameOrId(), voltageLevel.getLowVoltageLimit(), voltageLevel.getHighVoltageLimit(),
                    context.getNamingStrategy().getCgmesId(voltageLevel.getNullableSubstation()), baseVoltage.getId(), cimNamespace, xmlWriter);
        }
    }

    private void writeBusbarSections() throws XMLStreamException {
        for (BusbarSection bbs : context.getNetwork().getBusbarSections()) {
            BusbarSectionEq.write(context.getNamingStrategy().getCgmesId(bbs), bbs.getNameOrId(),
                    context.getNamingStrategy().getCgmesId(bbs.getTerminal().getVoltageLevel()),
                    context.getBaseVoltageByNominalVoltage(bbs.getTerminal().getVoltageLevel().getNominalV()).getId(), cimNamespace, xmlWriter);
        }
    }

    // We may receive a warning if we define an empty load group,
    // So we will output only the load groups that have been found during export of loads
    private void writeLoadGroups() throws XMLStreamException {
        for (LoadGroup loadGroup : context.getLoadGroups().found()) {
            CgmesExportUtil.writeStartIdName(loadGroup.className, loadGroup.id, loadGroup.name, cimNamespace, xmlWriter);
            // LoadArea and SubLoadArea are inside the Operation profile
            // In principle they are not required, but we may have to add them
            // and write here a reference to "LoadGroup.SubLoadArea"
            xmlWriter.writeEndElement();
        }
    }

    private void writeLoads() throws XMLStreamException {
        for (Load load : context.getNetwork().getLoads()) {
            if (context.isExportedEquipment(load)) {
                LoadDetail loadDetail = load.getExtension(LoadDetail.class);
                String className = CgmesExportUtil.loadClassName(loadDetail);
                String loadGroup = context.getLoadGroups().groupFor(className);
                EnergyConsumerEq.write(className,
                        context.getNamingStrategy().getCgmesId(load),
                        load.getNameOrId(), loadGroup,
                        context.getNamingStrategy().getCgmesId(load.getTerminal().getVoltageLevel()),
                        cimNamespace, xmlWriter);
            }
        }
    }

    private void writeGenerators() throws XMLStreamException {
        // Multiple synchronous machines may be grouped in the same generating unit
        // We have to write each generating unit only once
        // FIXME(Luma) move this to a class field, maybe other objects (asynchronous machines could also be part of a generating unit?)
        Set<String> generatingUnitsWritten = new HashSet<>();
        for (Generator generator : context.getNetwork().getGenerators()) {
            String generatingUnit = context.getNamingStrategy().getCgmesIdFromProperty(generator, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "GeneratingUnit");
            String regulatingControlId = RegulatingControlEq.writeKindVoltage(generator, exportedTerminalId(generator.getRegulatingTerminal()), regulatingControlsWritten, cimNamespace, xmlWriter, context);
            String reactiveLimitsId = null;
            double minQ = 0.0;
            double maxQ = 0.0;
            switch (generator.getReactiveLimits().getKind()) {
                case CURVE:
                    reactiveLimitsId = CgmesExportUtil.getUniqueId();
                    ReactiveCapabilityCurve curve = generator.getReactiveLimits(ReactiveCapabilityCurve.class);
                    for (ReactiveCapabilityCurve.Point point : curve.getPoints()) {
                        CurveDataEq.write(CgmesExportUtil.getUniqueId(), point.getP(), point.getMinQ(), point.getMaxQ(), reactiveLimitsId, cimNamespace, xmlWriter);
                    }
                    String reactiveCapabilityCurveName = "RCC_" + generator.getNameOrId();
                    ReactiveCapabilityCurveEq.write(reactiveLimitsId, reactiveCapabilityCurveName, generator, cimNamespace, xmlWriter);
                    break;

                case MIN_MAX:
                    minQ = generator.getReactiveLimits(MinMaxReactiveLimits.class).getMinQ();
                    maxQ = generator.getReactiveLimits(MinMaxReactiveLimits.class).getMaxQ();
                    break;

                default:
                    throw new PowsyblException("Unexpected type of ReactiveLimits on the generator " + generator.getNameOrId());
            }
            SynchronousMachineEq.write(context.getNamingStrategy().getCgmesId(generator), generator.getNameOrId(),
                    context.getNamingStrategy().getCgmesId(generator.getTerminal().getVoltageLevel()),
                    generatingUnit, regulatingControlId, reactiveLimitsId, minQ, maxQ, generator.getRatedS(), cimNamespace, xmlWriter);
            if (!generatingUnitsWritten.contains(generatingUnit)) {
                // We have not preserved the names of generating units
                // We name generating units based on the first machine found
                String generatingUnitName = "GU_" + generator.getNameOrId();
                GeneratingUnitEq.write(generatingUnit, generatingUnitName, generator.getEnergySource(), generator.getMinP(), generator.getMaxP(), generator.getTargetP(), cimNamespace, writeInitialP,
                        generator.getTerminal().getVoltageLevel().getSubstation().map(s -> context.getNamingStrategy().getCgmesId(s)).orElse(null), xmlWriter);
                generatingUnitsWritten.add(generatingUnit);
            }
        }
    }

    private void writeShuntCompensators() throws XMLStreamException {
        for (ShuntCompensator s : context.getNetwork().getShuntCompensators()) {
            double bPerSection = 0.0;
            double gPerSection = Double.NaN;
            if (s.getModelType().equals(ShuntCompensatorModelType.LINEAR)) {
                bPerSection = ((ShuntCompensatorLinearModel) s.getModel()).getBPerSection();
                gPerSection = ((ShuntCompensatorLinearModel) s.getModel()).getGPerSection();
            }
            String regulatingControlId = RegulatingControlEq.writeKindVoltage(s, exportedTerminalId(s.getRegulatingTerminal()), regulatingControlsWritten, cimNamespace, xmlWriter, context);
            ShuntCompensatorEq.write(context.getNamingStrategy().getCgmesId(s), s.getNameOrId(), s.getSectionCount(), s.getMaximumSectionCount(), s.getTerminal().getVoltageLevel().getNominalV(), s.getModelType(), bPerSection, gPerSection, regulatingControlId,
                    context.getNamingStrategy().getCgmesId(s.getTerminal().getVoltageLevel()), cimNamespace, xmlWriter);
            if (s.getModelType().equals(ShuntCompensatorModelType.NON_LINEAR)) {
                double b = 0.0;
                double g = 0.0;
                for (int section = 1; section <= s.getMaximumSectionCount(); section++) {
                    ShuntCompensatorEq.writePoint(CgmesExportUtil.getUniqueId(), context.getNamingStrategy().getCgmesId(s), section, s.getB(section) - b, s.getG(section) - g, cimNamespace, xmlWriter);
                    b = s.getB(section);
                    g = s.getG(section);
                }
            }
        }
    }

    private void writeStaticVarCompensators() throws XMLStreamException {
        for (StaticVarCompensator svc : context.getNetwork().getStaticVarCompensators()) {
            String regulatingControlId = RegulatingControlEq.writeKindVoltage(svc, exportedTerminalId(svc.getRegulatingTerminal()), regulatingControlsWritten, cimNamespace, xmlWriter, context);
            StaticVarCompensatorEq.write(context.getNamingStrategy().getCgmesId(svc), svc.getNameOrId(), context.getNamingStrategy().getCgmesId(svc.getTerminal().getVoltageLevel()), regulatingControlId, 1 / svc.getBmin(), 1 / svc.getBmax(), svc.getExtension(VoltagePerReactivePowerControl.class), svc.getRegulationMode(), svc.getVoltageSetpoint(), cimNamespace, xmlWriter);
        }
    }

    private void writeLines() throws XMLStreamException {
        for (Line line : context.getNetwork().getLines()) {
            String baseVoltage = null;
            if (line.getTerminal1().getVoltageLevel().getNominalV() == line.getTerminal2().getVoltageLevel().getNominalV()) {
                baseVoltage = context.getBaseVoltageByNominalVoltage(line.getTerminal1().getVoltageLevel().getNominalV()).getId();
            }
            AcLineSegmentEq.write(context.getNamingStrategy().getCgmesId(line), line.getNameOrId(), baseVoltage, line.getR(), line.getX(), line.getG1() + line.getG2(), line.getB1() + line.getB2(), cimNamespace, xmlWriter);
            writeBranchLimits(line, exportedTerminalId(line.getTerminal1()), exportedTerminalId(line.getTerminal2()));
        }
    }

    private void writeTwoWindingsTransformers() throws XMLStreamException {
        for (TwoWindingsTransformer twt : context.getNetwork().getTwoWindingsTransformers()) {
            PowerTransformerEq.write(context.getNamingStrategy().getCgmesId(twt), twt.getNameOrId(), twt.getSubstation().map(s -> context.getNamingStrategy().getCgmesId(s)).orElse(null), cimNamespace, xmlWriter);
            String end1Id = context.getNamingStrategy().getCgmesIdFromAlias(twt, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TRANSFORMER_END + 1);
            // structural ratio at end1
            double a0 = twt.getRatedU1() / twt.getRatedU2();
            // move structural ratio from end1 to end2
            double a02 = a0 * a0;
            double r = twt.getR() * a02;
            double x = twt.getX() * a02;
            double g = twt.getG() / a02;
            double b = twt.getB() / a02;
            BaseVoltageMapping.BaseVoltageSource baseVoltage1 = context.getBaseVoltageByNominalVoltage(twt.getTerminal1().getVoltageLevel().getNominalV());
            PowerTransformerEq.writeEnd(end1Id, twt.getNameOrId() + "_1", context.getNamingStrategy().getCgmesId(twt), 1, r, x, g, b,
                    twt.getRatedS(), twt.getRatedU1(), exportedTerminalId(twt.getTerminal1()), baseVoltage1.getId(), cimNamespace, xmlWriter);
            String end2Id = context.getNamingStrategy().getCgmesIdFromAlias(twt, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TRANSFORMER_END + 2);
            BaseVoltageMapping.BaseVoltageSource baseVoltage2 = context.getBaseVoltageByNominalVoltage(twt.getTerminal2().getVoltageLevel().getNominalV());
            PowerTransformerEq.writeEnd(end2Id, twt.getNameOrId() + "_2", context.getNamingStrategy().getCgmesId(twt), 2, 0.0, 0.0, 0.0, 0.0,
                    twt.getRatedS(), twt.getRatedU2(), exportedTerminalId(twt.getTerminal2()), baseVoltage2.getId(), cimNamespace, xmlWriter);

            // Export tap changers:
            // We are exporting the tap changer as it is modelled in IIDM, always at end 1
            int endNumber = 1;
            // IIDM model always has tap changers (ratio and/or phase) at end 1, and only at end 1.
            // We have to adjust the aliases for potential original tap changers coming from end 1 or end 2.
            // Potential tc2 is always converted to a tc at end 1.
            // If both tc1 and tc2 were present, tc2 was combined during import (fixed at current step) with tc1. Steps from tc1 were kept.
            // If we only had tc2, it mas moved to end 1.
            //
            // When we had only tc2, the alias for tc1 if we do EQ export should contain the identifier of original tc2.
            // In the rest of situations, we keep the same id under alias for tc1.
            adjustTapChangerAliases2wt(twt, twt.getPhaseTapChanger(), CgmesNames.PHASE_TAP_CHANGER);
            adjustTapChangerAliases2wt(twt, twt.getRatioTapChanger(), CgmesNames.RATIO_TAP_CHANGER);
            writePhaseTapChanger(twt, twt.getPhaseTapChanger(), twt.getNameOrId(), endNumber, end1Id, twt.getTerminal1().getVoltageLevel().getNominalV());
            writeRatioTapChanger(twt, twt.getRatioTapChanger(), twt.getNameOrId(), endNumber, end1Id);
            writeBranchLimits(twt, exportedTerminalId(twt.getTerminal1()), exportedTerminalId(twt.getTerminal2()));
        }
    }

    private static void adjustTapChangerAliases2wt(TwoWindingsTransformer transformer, TapChanger<?, ?> tc, String tapChangerKind) {
        // If we had alias only for tc1, is ok, we will export only tc1 at end 1
        // If we had alias for tc1 and tc2, is ok, tc2 has been moved to end 1 and combined with tc1, but we preserve id for tc1
        // Only if we had tc at end 2 has been moved to end 1 and its identifier must be preserved
        if (tc != null) {
            String aliasType1 = Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + tapChangerKind + 1;
            if (transformer.getAliasFromType(aliasType1).isEmpty()) {
                // At this point, if we have a tap changer,
                // the alias for type 2 should be non-empty, but we check it anyway
                String aliasType2 = Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + tapChangerKind + 2;
                Optional<String> tc2id = transformer.getAliasFromType(aliasType2);
                if (tc2id.isPresent()) {
                    transformer.removeAlias(tc2id.get());
                    transformer.addAlias(tc2id.get(), aliasType1);
                }
            }
        }
    }

    private void writeThreeWindingsTransformers() throws XMLStreamException {
        for (ThreeWindingsTransformer twt : context.getNetwork().getThreeWindingsTransformers()) {
            PowerTransformerEq.write(context.getNamingStrategy().getCgmesId(twt), twt.getNameOrId(), twt.getSubstation().map(s -> context.getNamingStrategy().getCgmesId(s)).orElse(null), cimNamespace, xmlWriter);
            double ratedU0 = twt.getLeg1().getRatedU();
            String end1Id = context.getNamingStrategy().getCgmesIdFromAlias(twt, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TRANSFORMER_END + 1);
            writeThreeWindingsTransformerEnd(twt, context.getNamingStrategy().getCgmesId(twt), twt.getNameOrId() + "_1", end1Id, 1, twt.getLeg1(), ratedU0, exportedTerminalId(twt.getLeg1().getTerminal()));
            String end2Id = context.getNamingStrategy().getCgmesIdFromAlias(twt, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TRANSFORMER_END + 2);
            writeThreeWindingsTransformerEnd(twt, context.getNamingStrategy().getCgmesId(twt), twt.getNameOrId() + "_2", end2Id, 2, twt.getLeg2(), ratedU0, exportedTerminalId(twt.getLeg2().getTerminal()));
            String end3Id = context.getNamingStrategy().getCgmesIdFromAlias(twt, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TRANSFORMER_END + 3);
            writeThreeWindingsTransformerEnd(twt, context.getNamingStrategy().getCgmesId(twt), twt.getNameOrId() + "_3", end3Id, 3, twt.getLeg3(), ratedU0, exportedTerminalId(twt.getLeg3().getTerminal()));
        }
    }

    private void writeThreeWindingsTransformerEnd(ThreeWindingsTransformer twt, String twtId, String twtName, String endId, int endNumber, ThreeWindingsTransformer.Leg leg, double ratedU0, String terminalId) throws XMLStreamException {
        // structural ratio at end1
        double a0 = leg.getRatedU() / ratedU0;
        // move structural ratio from end1 to end2
        double a02 = a0 * a0;
        double r = leg.getR() * a02;
        double x = leg.getX() * a02;
        double g = leg.getG() / a02;
        double b = leg.getB() / a02;
        BaseVoltageMapping.BaseVoltageSource baseVoltage = context.getBaseVoltageByNominalVoltage(leg.getTerminal().getVoltageLevel().getNominalV());
        PowerTransformerEq.writeEnd(endId, twtName, twtId, endNumber, r, x, g, b, leg.getRatedS(), leg.getRatedU(), terminalId, baseVoltage.getId(), cimNamespace, xmlWriter);
        writePhaseTapChanger(twt, leg.getPhaseTapChanger(), twtName, endNumber, endId, leg.getTerminal().getVoltageLevel().getNominalV());
        writeRatioTapChanger(twt, leg.getRatioTapChanger(), twtName, endNumber, endId);
        writeFlowsLimits(leg, terminalId);
    }

    private <C extends Connectable<C>> void writePhaseTapChanger(C eq, PhaseTapChanger ptc, String twtName, int endNumber, String endId, double neutralU) throws XMLStreamException {
        if (ptc != null) {
            String aliasType = Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.PHASE_TAP_CHANGER + endNumber;
            String tapChangerId = eq.getAliasFromType(aliasType).orElseThrow();
            String cgmesTapChangerId = context.getNamingStrategy().getCgmesIdFromAlias(eq, aliasType);

            int neutralStep = getPhaseTapChangerNeutralStep(ptc);
            Optional<String> regulatingControlId = getTapChangerControlId(eq, tapChangerId);
            String cgmesRegulatingControlId = null;
            if (regulatingControlId.isPresent()) {
                String mode = getPhaseTapChangerRegulationMode(ptc);
                // Only export the regulating control if mode is valid
                if (mode != null) {
                    String controlName = twtName + "_PTC_RC";
                    String terminalId = CgmesExportUtil.getTerminalId(ptc.getRegulationTerminal(), context);
                    cgmesRegulatingControlId = context.getNamingStrategy().getCgmesId(regulatingControlId.get());
                    if (!regulatingControlsWritten.contains(cgmesRegulatingControlId)) {
                        TapChangerEq.writeControl(cgmesRegulatingControlId, controlName, mode, terminalId, cimNamespace, xmlWriter);
                        regulatingControlsWritten.add(cgmesRegulatingControlId);
                    }
                }
            }
            String phaseTapChangerTableId = CgmesExportUtil.getUniqueId();
            // If we write the EQ, we will always write the Tap Changer as tabular
            // We reset the phase tap changer type stored in the extensions
            String typeTabular = CgmesNames.PHASE_TAP_CHANGER_TABULAR;
            CgmesExportUtil.setCgmesTapChangerType(eq, tapChangerId, typeTabular);

            TapChangerEq.writePhase(typeTabular, cgmesTapChangerId, twtName + "_PTC", endId, ptc.getLowTapPosition(), ptc.getHighTapPosition(), neutralStep, ptc.getTapPosition(), neutralU, false, phaseTapChangerTableId, cgmesRegulatingControlId, cimNamespace, xmlWriter);
            TapChangerEq.writePhaseTable(phaseTapChangerTableId, twtName + "_TABLE", cimNamespace, xmlWriter);
            for (Map.Entry<Integer, PhaseTapChangerStep> step : ptc.getAllSteps().entrySet()) {
                TapChangerEq.writePhaseTablePoint(CgmesExportUtil.getUniqueId(), phaseTapChangerTableId, step.getValue().getR(), step.getValue().getX(), step.getValue().getG(), step.getValue().getB(), 1 / step.getValue().getRho(), -step.getValue().getAlpha(), step.getKey(), cimNamespace, xmlWriter);
            }
        }
    }

    private static <C extends Connectable<C>> Optional<String> getTapChangerControlId(C eq, String tcId) {
        CgmesTapChangers<C> cgmesTcs = eq.getExtension(CgmesTapChangers.class);
        if (cgmesTcs != null) {
            CgmesTapChanger cgmesTc = cgmesTcs.getTapChanger(tcId);
            if (cgmesTc != null) {
                return Optional.ofNullable(cgmesTc.getControlId());
            }
        }
        return Optional.empty();
    }

    private static String getPhaseTapChangerRegulationMode(PhaseTapChanger ptc) {
        switch (ptc.getRegulationMode()) {
            case CURRENT_LIMITER:
                return PHASE_TAP_CHANGER_REGULATION_MODE_CURRENT_FLOW;
            case ACTIVE_POWER_CONTROL:
                return PHASE_TAP_CHANGER_REGULATION_MODE_ACTIVE_POWER;
            case FIXED_TAP:
            default:
                return null;
        }
    }

    private static int getPhaseTapChangerNeutralStep(PhaseTapChanger ptc) {
        int neutralStep = ptc.getLowTapPosition();
        while (ptc.getStep(neutralStep).getAlpha() != 0.0) {
            neutralStep++;
            if (neutralStep > ptc.getHighTapPosition()) {
                return ptc.getHighTapPosition();
            }
        }
        return neutralStep;
    }

    private <C extends Connectable<C>> void writeRatioTapChanger(C eq, RatioTapChanger rtc, String twtName, int endNumber, String endId) throws XMLStreamException {
        if (rtc != null) {
            String aliasType = Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.RATIO_TAP_CHANGER + endNumber;
            String tapChangerId = eq.getAliasFromType(aliasType).orElseThrow();
            String cgmesTapChangerId = context.getNamingStrategy().getCgmesIdFromAlias(eq, aliasType);

            int neutralStep = getRatioTapChangerNeutralStep(rtc);
            double stepVoltageIncrement = 100.0 * (1.0 / rtc.getStep(rtc.getLowTapPosition()).getRho() - 1.0) / (rtc.getLowTapPosition() - neutralStep);
            String ratioTapChangerTableId = CgmesExportUtil.getUniqueId();
            Optional<String> regulatingControlId = getTapChangerControlId(eq, tapChangerId);
            String cgmesRegulatingControlId = null;
            String controlMode = rtc.isRegulating() ? "volt" : "reactive";
            if (regulatingControlId.isPresent()) {
                String controlName = twtName + "_RTC_RC";
                String terminalId = CgmesExportUtil.getTerminalId(rtc.getRegulationTerminal(), context);
                cgmesRegulatingControlId = context.getNamingStrategy().getCgmesId(regulatingControlId.get());
                if (!regulatingControlsWritten.contains(cgmesRegulatingControlId)) {
                    // Regulating control mode is always "voltage"
                    TapChangerEq.writeControl(cgmesRegulatingControlId, controlName, RATIO_TAP_CHANGER_REGULATION_MODE_VOLTAGE, terminalId, cimNamespace, xmlWriter);
                    regulatingControlsWritten.add(cgmesRegulatingControlId);
                }
            }
            TapChangerEq.writeRatio(cgmesTapChangerId, twtName + "_RTC", endId, rtc.getLowTapPosition(), rtc.getHighTapPosition(), neutralStep, rtc.getTapPosition(), rtc.getTargetV(), rtc.hasLoadTapChangingCapabilities(), stepVoltageIncrement,
                    ratioTapChangerTableId, cgmesRegulatingControlId, controlMode, cimNamespace, xmlWriter);
            TapChangerEq.writeRatioTable(ratioTapChangerTableId, twtName + "_TABLE", cimNamespace, xmlWriter);
            for (Map.Entry<Integer, RatioTapChangerStep> step : rtc.getAllSteps().entrySet()) {
                TapChangerEq.writeRatioTablePoint(CgmesExportUtil.getUniqueId(), ratioTapChangerTableId, step.getValue().getR(), step.getValue().getX(), step.getValue().getG(), step.getValue().getB(), 1 / step.getValue().getRho(), step.getKey(), cimNamespace, xmlWriter);
            }

        }
    }

    private static int getRatioTapChangerNeutralStep(RatioTapChanger rtc) {
        int neutralStep = rtc.getLowTapPosition();
        while (rtc.getStep(neutralStep).getRho() != 1.0) {
            neutralStep++;
            if (neutralStep > rtc.getHighTapPosition()) {
                return rtc.getHighTapPosition();
            }
        }
        return neutralStep;
    }

    private void writeDanglingLines() throws XMLStreamException {
        for (DanglingLine danglingLine : context.getNetwork().getDanglingLines()) {

            // We may create fictitious containers for boundary side of dangling lines,
            // and we consider the situation where the base voltage of a line lying at a boundary has a baseVoltage defined in the IGM,
            String baseVoltageId = writeDanglingLineBaseVoltage(danglingLine);
            String connectivityNodeId = writeDanglingLineConnectivity(danglingLine, baseVoltageId);

            // New Equivalent Injection
            double minP = 0.0;
            double maxP = 0.0;
            double minQ = 0.0;
            double maxQ = 0.0;
            if (danglingLine.getGeneration() != null) {
                minP = danglingLine.getGeneration().getMinP();
                maxP = danglingLine.getGeneration().getMaxP();
                if (danglingLine.getGeneration().getReactiveLimits().getKind().equals(ReactiveLimitsKind.MIN_MAX)) {
                    minQ = danglingLine.getGeneration().getReactiveLimits(MinMaxReactiveLimits.class).getMinQ();
                    maxQ = danglingLine.getGeneration().getReactiveLimits(MinMaxReactiveLimits.class).getMaxQ();
                } else {
                    throw new PowsyblException("Unexpected type of ReactiveLimits on the dangling line " + danglingLine.getNameOrId());
                }
            }
            String equivalentInjectionId = context.getNamingStrategy().getCgmesIdFromAlias(danglingLine, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "EquivalentInjection");
            EquivalentInjectionEq.write(equivalentInjectionId, danglingLine.getNameOrId() + "_EI", danglingLine.getGeneration() != null, danglingLine.getGeneration() != null, minP, maxP, minQ, maxQ, baseVoltageId, cimNamespace, xmlWriter);
            String equivalentInjectionTerminalId = context.getNamingStrategy().getCgmesIdFromAlias(danglingLine, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "EquivalentInjectionTerminal");
            TerminalEq.write(equivalentInjectionTerminalId, equivalentInjectionId, connectivityNodeId, 1, cimNamespace, xmlWriter);

            // Cast the danglingLine to an AcLineSegment
            AcLineSegmentEq.write(context.getNamingStrategy().getCgmesId(danglingLine), danglingLine.getNameOrId() + "_DL",
                    context.getBaseVoltageByNominalVoltage(danglingLine.getTerminal().getVoltageLevel().getNominalV()).getId(),
                    danglingLine.getR(), danglingLine.getX(), danglingLine.getG(), danglingLine.getB(), cimNamespace, xmlWriter);
            writeFlowsLimits(danglingLine, exportedTerminalId(danglingLine.getTerminal()));
        }
    }

    private String writeDanglingLineBaseVoltage(DanglingLine danglingLine) throws XMLStreamException {
        double nominalV = danglingLine.getTerminal().getVoltageLevel().getNominalV();
        BaseVoltageMapping.BaseVoltageSource baseVoltage = context.getBaseVoltageByNominalVoltage(nominalV);
        if (baseVoltage.getSource().equals(Source.IGM) && !context.isExportedBaseVoltageFor(nominalV)) {
            BaseVoltageEq.write(baseVoltage.getId(), nominalV, cimNamespace, xmlWriter);
            context.exportedBaseVoltageFor(nominalV);
        }

        return baseVoltage.getId();
    }

    private String writeDanglingLineConnectivity(DanglingLine danglingLine, String baseVoltageId) throws XMLStreamException {
        String connectivityNodeId = null;
        if (context.writeConnectivityNodes()) {
            // We keep the connectivity node from the boundary definition as an alias in the dangling line
            if (danglingLine.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.CONNECTIVITY_NODE_BOUNDARY).isPresent()) {
                connectivityNodeId = context.getNamingStrategy().getCgmesIdFromAlias(danglingLine, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.CONNECTIVITY_NODE_BOUNDARY);
            } else {
                // If no information about original boundary has been preserved in the IIDM model,
                // we create a new ConnectivityNode in a fictitious Substation and Voltage Level
                LOG.info("Dangling line {}{} is not connected to a connectivity node in boundaries files: a fictitious substation and voltage level are created",
                        danglingLine.getId(), danglingLine.getUcteXnodeCode() != null ? " linked to X-node " + danglingLine.getUcteXnodeCode() : "");
                connectivityNodeId = CgmesExportUtil.getUniqueId();
                String connectivityNodeContainerId = createFictitiousContainerFor(danglingLine, baseVoltageId);
                exportConnectivityNode(connectivityNodeId, danglingLine.getNameOrId() + "_NODE", connectivityNodeContainerId);
                danglingLine.addAlias(connectivityNodeId, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.CONNECTIVITY_NODE_BOUNDARY);
            }
        } else {
            if (danglingLine.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TOPOLOGICAL_NODE_BOUNDARY).isEmpty()) {
                // Also create a container if we will have to create a Topological Node for the boundary
                LOG.info("Dangling line {}{} is not connected to a topology node in boundaries files: a fictitious substation and voltage level are created",
                        danglingLine.getId(), danglingLine.getUcteXnodeCode() != null ? " linked to X-node " + danglingLine.getUcteXnodeCode() : "");
                createFictitiousContainerFor(danglingLine, baseVoltageId);
            }
        }
        // New Terminal
        String terminalId = context.getNamingStrategy().getCgmesIdFromAlias(danglingLine, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "Terminal_Boundary");
        TerminalEq.write(terminalId, context.getNamingStrategy().getCgmesId(danglingLine), connectivityNodeId, 2, cimNamespace, xmlWriter);

        return connectivityNodeId;
    }

    private String createFictitiousContainerFor(Identifiable<?> identifiable, String baseVoltageId) throws XMLStreamException {
        String substationId = writeFictitiousSubstationFor(identifiable);
        String containerId = writeFictitiousVoltageLevelFor(identifiable, substationId, baseVoltageId);
        context.setFictitiousContainerFor(identifiable, containerId);
        return containerId;
    }

    private String writeFictitiousSubstationFor(Identifiable<?> identifiable) throws XMLStreamException {
        // New Substation
        // We avoid using the name of the identifiable for the names of fictitious region and subregion
        // Because regions and subregions with the same name are merged
        String geographicalRegionId = CgmesExportUtil.getUniqueId();
        GeographicalRegionEq.write(geographicalRegionId, identifiable.getId() + "_GR", cimNamespace, xmlWriter);
        String subGeographicalRegionId = CgmesExportUtil.getUniqueId();
        SubGeographicalRegionEq.write(subGeographicalRegionId, identifiable.getId() + "_SGR", geographicalRegionId, cimNamespace, xmlWriter);
        String substationId = CgmesExportUtil.getUniqueId();
        SubstationEq.write(substationId, identifiable.getNameOrId() + "_SUBSTATION", subGeographicalRegionId, cimNamespace, xmlWriter);
        return substationId;
    }

    private String writeFictitiousVoltageLevelFor(Identifiable<?> identifiable, String substationId, String baseVoltageId) throws XMLStreamException {
        // New VoltageLevel
        String voltageLevelId = CgmesExportUtil.getUniqueId();
        VoltageLevelEq.write(voltageLevelId, identifiable.getNameOrId() + "_VL", Double.NaN, Double.NaN, substationId, baseVoltageId, cimNamespace, xmlWriter);
        return voltageLevelId;
    }

    private void writeBranchLimits(Branch<?> branch, String terminalId1, String terminalId2) throws XMLStreamException {
        Optional<ActivePowerLimits> activePowerLimits1 = branch.getActivePowerLimits1();
        if (activePowerLimits1.isPresent()) {
            writeLoadingLimits(activePowerLimits1.get(), terminalId1);
        }
        Optional<ActivePowerLimits> activePowerLimits2 = branch.getActivePowerLimits2();
        if (activePowerLimits2.isPresent()) {
            writeLoadingLimits(activePowerLimits2.get(), terminalId2);
        }
        Optional<ApparentPowerLimits> apparentPowerLimits1 = branch.getApparentPowerLimits1();
        if (apparentPowerLimits1.isPresent()) {
            writeLoadingLimits(apparentPowerLimits1.get(), terminalId1);
        }
        Optional<ApparentPowerLimits> apparentPowerLimits2 = branch.getApparentPowerLimits2();
        if (apparentPowerLimits2.isPresent()) {
            writeLoadingLimits(apparentPowerLimits2.get(), terminalId2);
        }
        Optional<CurrentLimits> currentLimits1 = branch.getCurrentLimits1();
        if (currentLimits1.isPresent()) {
            writeLoadingLimits(currentLimits1.get(), terminalId1);
        }
        Optional<CurrentLimits> currentLimits2 = branch.getCurrentLimits2();
        if (currentLimits2.isPresent()) {
            writeLoadingLimits(currentLimits2.get(), terminalId2);
        }
    }

    private void writeFlowsLimits(FlowsLimitsHolder holder, String terminalId) throws XMLStreamException {
        Optional<ActivePowerLimits> activePowerLimits = holder.getActivePowerLimits();
        if (activePowerLimits.isPresent()) {
            writeLoadingLimits(activePowerLimits.get(), terminalId);
        }
        Optional<ApparentPowerLimits> apparentPowerLimits = holder.getApparentPowerLimits();
        if (apparentPowerLimits.isPresent()) {
            writeLoadingLimits(apparentPowerLimits.get(), terminalId);
        }
        Optional<CurrentLimits> currentLimits = holder.getCurrentLimits();
        if (currentLimits.isPresent()) {
            writeLoadingLimits(currentLimits.get(), terminalId);
        }
    }

    private void writeLoadingLimits(LoadingLimits limits, String terminalId) throws XMLStreamException {
        if (!Double.isNaN(limits.getPermanentLimit())) {
            String operationalLimitTypeId = CgmesExportUtil.getUniqueId();
            OperationalLimitTypeEq.writePatl(operationalLimitTypeId, cimNamespace, euNamespace, limitTypeAttributeName, limitKindClassName, writeInfiniteDuration, xmlWriter);
            String operationalLimitSetId = CgmesExportUtil.getUniqueId();
            OperationalLimitSetEq.write(operationalLimitSetId, "operational limit patl", terminalId, cimNamespace, xmlWriter);
            LoadingLimitEq.write(CgmesExportUtil.getUniqueId(), limits.getClass(), "CurrentLimit", limits.getPermanentLimit(), operationalLimitTypeId, operationalLimitSetId, cimNamespace, limitValueAttributeName, xmlWriter);
        }
        if (!limits.getTemporaryLimits().isEmpty()) {
            Iterator<LoadingLimits.TemporaryLimit> iterator = limits.getTemporaryLimits().iterator();
            while (iterator.hasNext()) {
                LoadingLimits.TemporaryLimit temporaryLimit = iterator.next();
                String operationalLimitTypeId = CgmesExportUtil.getUniqueId();
                OperationalLimitTypeEq.writeTatl(operationalLimitTypeId, temporaryLimit.getName(), temporaryLimit.getAcceptableDuration(), cimNamespace, euNamespace, limitTypeAttributeName, limitKindClassName, writeInfiniteDuration, xmlWriter);
                String operationalLimitSetId = CgmesExportUtil.getUniqueId();
                OperationalLimitSetEq.write(operationalLimitSetId, "operational limit tatl", terminalId, cimNamespace, xmlWriter);
                LoadingLimitEq.write(CgmesExportUtil.getUniqueId(), limits.getClass(), "CurrentLimit", temporaryLimit.getValue(), operationalLimitTypeId, operationalLimitSetId, cimNamespace, limitValueAttributeName, xmlWriter);
            }
        }
    }

    private void writeHvdcLines() throws XMLStreamException {
        NamingStrategy namingStrategy = context.getNamingStrategy();
        for (HvdcLine line : context.getNetwork().getHvdcLines()) {
            String lineId = context.getNamingStrategy().getCgmesId(line);
            String converter1Id = namingStrategy.getCgmesId(line.getConverterStation1());
            String converter2Id = namingStrategy.getCgmesId(line.getConverterStation2());
            String substation1Id = namingStrategy.getCgmesId(line.getConverterStation1().getTerminal().getVoltageLevel().getNullableSubstation());
            String substation2Id = namingStrategy.getCgmesId(line.getConverterStation2().getTerminal().getVoltageLevel().getNullableSubstation());

            String dcConverterUnit1 = CgmesExportUtil.getUniqueId();
            writeDCConverterUnit(dcConverterUnit1, line.getNameOrId() + "_1", substation1Id);
            String dcNode1 = line.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "DCNode1").orElseThrow(PowsyblException::new);
            writeDCNode(dcNode1, line.getNameOrId() + "_1", dcConverterUnit1);

            String dcConverterUnit2 = CgmesExportUtil.getUniqueId();
            writeDCConverterUnit(dcConverterUnit2, line.getNameOrId() + "_1", substation2Id);
            String dcNode2 = line.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "DCNode2").orElseThrow(PowsyblException::new);
            writeDCNode(dcNode2, line.getNameOrId() + "_2", dcConverterUnit2);

            String dcTerminal1 = line.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "DCTerminal1").orElseThrow(PowsyblException::new);
            writeDCTerminal(dcTerminal1, lineId, dcNode1, 1);

            String dcTerminal2 = line.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "DCTerminal2").orElseThrow(PowsyblException::new);
            writeDCTerminal(dcTerminal2, lineId, dcNode2, 2);

            HvdcConverterStation<?> converter = line.getConverterStation1();
            writeTerminal(converter.getTerminal(), CgmesExportUtil.getUniqueId(), converter1Id, connectivityNodeId(converter.getTerminal()), 1);
            String capabilityCurveId1 = writeVsCapabilityCurve(converter);
            String acdcConverterDcTerminal1 = converter.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + AC_DC_CONVERTER_DC_TERMINAL).orElseThrow(PowsyblException::new);
            writeAcdcConverterDCTerminal(acdcConverterDcTerminal1, converter1Id, dcNode1, 2);

            converter = line.getConverterStation2();
            writeTerminal(converter.getTerminal(), CgmesExportUtil.getUniqueId(), converter2Id, connectivityNodeId(converter.getTerminal()), 1);
            String capabilityCurveId2 = writeVsCapabilityCurve(converter);
            String acdcConverterDcTerminal2 = converter.getAliasFromType(Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + AC_DC_CONVERTER_DC_TERMINAL).orElseThrow(PowsyblException::new);
            writeAcdcConverterDCTerminal(acdcConverterDcTerminal2, converter2Id, dcNode2, 2);

            DCLineSegmentEq.write(lineId, line.getNameOrId(), line.getR(), cimNamespace, xmlWriter);
            writeHvdcConverterStation(line.getConverterStation1(), line.getNominalV(), dcConverterUnit1, capabilityCurveId1);
            writeHvdcConverterStation(line.getConverterStation2(), line.getNominalV(), dcConverterUnit2, capabilityCurveId2);
        }
    }

    private String writeVsCapabilityCurve(HvdcConverterStation<?> converter) throws XMLStreamException {
        if (converter instanceof LccConverterStation) {
            return null;
        }
        VscConverterStation vscConverter = (VscConverterStation) converter;
        if (vscConverter.getReactiveLimits() == null) {
            return null;
        }
        String reactiveLimitsId = CgmesExportUtil.getUniqueId();
        switch (vscConverter.getReactiveLimits().getKind()) {
            case CURVE:
                ReactiveCapabilityCurve curve = vscConverter.getReactiveLimits(ReactiveCapabilityCurve.class);
                for (ReactiveCapabilityCurve.Point point : curve.getPoints()) {
                    CurveDataEq.write(CgmesExportUtil.getUniqueId(), point.getP(), point.getMinQ(), point.getMaxQ(), reactiveLimitsId, cimNamespace, xmlWriter);
                }
                String reactiveCapabilityCurveName = "RCC_" + vscConverter.getNameOrId();
                ReactiveCapabilityCurveEq.write(reactiveLimitsId, reactiveCapabilityCurveName, vscConverter, cimNamespace, xmlWriter);
                break;

            case MIN_MAX:
                //Do not have to export anything
                reactiveLimitsId = null;
                break;

            default:
                throw new PowsyblException("Unexpected type of ReactiveLimits on the VsConverter " + converter.getNameOrId());
        }
        return reactiveLimitsId;
    }

    private void writeDCConverterUnit(String id, String dcConverterUnitName, String substationId) throws XMLStreamException {
        DCConverterUnitEq.write(id, dcConverterUnitName, substationId, cimNamespace, xmlWriter);
    }

    private void writeHvdcConverterStation(HvdcConverterStation<?> converterStation, double ratedUdc, String dcEquipmentContainerId, String capabilityCurveId) throws XMLStreamException {
        String pccTerminal = getConverterStationPccTerminal(converterStation);
        HvdcConverterStationEq.write(context.getNamingStrategy().getCgmesId(converterStation), converterStation.getNameOrId(), converterStation.getHvdcType(), ratedUdc, dcEquipmentContainerId, pccTerminal, capabilityCurveId, cimNamespace, xmlWriter);
    }

    private String getConverterStationPccTerminal(HvdcConverterStation<?> converterStation) {
        if (converterStation.getHvdcType().equals(HvdcConverterStation.HvdcType.VSC)) {
            return exportedTerminalId(((VscConverterStation) converterStation).getRegulatingTerminal());
        }
        return null;
    }

    private void writeDCNode(String id, String dcNodeName, String dcEquipmentContainerId) throws XMLStreamException {
        DCNodeEq.write(id, dcNodeName, dcEquipmentContainerId, cimNamespace, xmlWriter);
    }

    private void writeDCTerminal(String id, String conductingEquipmentId, String dcNodeId, int sequenceNumber) throws XMLStreamException {
        DCTerminalEq.write("DCTerminal", id, conductingEquipmentId, dcNodeId, sequenceNumber, cimNamespace, xmlWriter);
    }

    private void writeAcdcConverterDCTerminal(String id, String conductingEquipmentId, String dcNodeId, int sequenceNumber) throws XMLStreamException {
        DCTerminalEq.write(AC_DC_CONVERTER_DC_TERMINAL, id, conductingEquipmentId, dcNodeId, sequenceNumber, cimNamespace, xmlWriter);
    }

    private void writeControlAreas() throws XMLStreamException {
        CgmesControlAreas cgmesControlAreas = context.getNetwork().getExtension(CgmesControlAreas.class);
        for (CgmesControlArea cgmesControlArea : cgmesControlAreas.getCgmesControlAreas()) {
            writeControlArea(cgmesControlArea);
        }
    }

    private void writeControlArea(CgmesControlArea cgmesControlArea) throws XMLStreamException {
        // Original control area identifiers may not respect mRID rules, so we pass it through naming strategy
        // to obtain always valid mRID identifiers
        String controlAreaCgmesId = context.getNamingStrategy().getCgmesId(cgmesControlArea.getId());
        ControlAreaEq.write(controlAreaCgmesId, cgmesControlArea.getName(), cgmesControlArea.getEnergyIdentificationCodeEIC(), cimNamespace, euNamespace, xmlWriter);
        for (Terminal terminal : cgmesControlArea.getTerminals()) {
            TieFlowEq.write(CgmesExportUtil.getUniqueId(), controlAreaCgmesId, exportedTerminalId(terminal), cimNamespace, xmlWriter);
        }
        for (Boundary boundary : cgmesControlArea.getBoundaries()) {
            String terminalId = getTieFlowBoundaryTerminal(boundary, context);
            if (terminalId != null) {
                TieFlowEq.write(CgmesExportUtil.getUniqueId(), controlAreaCgmesId, terminalId, cimNamespace, xmlWriter);
            }
        }
    }

    private static String getTieFlowBoundaryTerminal(Boundary boundary, CgmesExportContext context) {
        Connectable<?> c = boundary.getConnectable();
        if (c instanceof DanglingLine) {
            return context.getNamingStrategy().getCgmesIdFromAlias(c, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + "Terminal_Boundary");
        } else {
            // This means the boundary corresponds to a TieLine.
            // Because the network should not be a merging view,
            // the only way to have a TieLine in the model is that
            // the original data for the network contained both halves of the TieLine.
            // That is, the initial CGMES data contains the two ACLSs at each side of one boundary point.

            // Currently, we are exporting TieLines in the EQ as a single ACLS,
            // We are not exporting the individual halves of the tie line as separate equipment.
            // So we do not have terminals for the boundary points.

            // This error should be fixed exporting the two halves of the TieLine to the EQ,
            // with their corresponding terminals.
            // Also, the boundary node should not be exported but referenced,
            // as it should be defined in the boundary, not in the instance EQ file.

            LOG.error("Unsupported tie flow at TieLine boundary {}", c.getId());
            return null;
        }
    }

    private void writeTerminals() throws XMLStreamException {
        for (Connectable<?> c : context.getNetwork().getConnectables()) {
            if (context.isExportedEquipment(c)) {
                for (Terminal t : c.getTerminals()) {
                    writeTerminal(t);
                }
            }
        }

        String[] switchNodesKeys = new String[2];
        for (Switch sw : context.getNetwork().getSwitches()) {
            if (context.isExportedEquipment(sw)) {
                VoltageLevel vl = sw.getVoltageLevel();
                fillSwitchNodeKeys(vl, sw, switchNodesKeys);
                String nodeId1 = mapNodeKey2NodeId.get(switchNodesKeys[0]);
                String terminalId1 = context.getNamingStrategy().getCgmesIdFromAlias(sw, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TERMINAL + 1);
                TerminalEq.write(terminalId1, context.getNamingStrategy().getCgmesId(sw), nodeId1, 1, cimNamespace, xmlWriter);
                String nodeId2 = mapNodeKey2NodeId.get(switchNodesKeys[1]);
                String terminalId2 = context.getNamingStrategy().getCgmesIdFromAlias(sw, Conversion.CGMES_PREFIX_ALIAS_PROPERTIES + CgmesNames.TERMINAL + 2);
                TerminalEq.write(terminalId2, context.getNamingStrategy().getCgmesId(sw), nodeId2, 2, cimNamespace, xmlWriter);
            }
        }
    }

    private void writeTerminal(Terminal t) {
        String equipmentId = context.getNamingStrategy().getCgmesId(t.getConnectable());
        writeTerminal(t, CgmesExportUtil.getTerminalId(t, context), equipmentId, connectivityNodeId(t), CgmesExportUtil.getTerminalSequenceNumber(t));
    }

    private void writeTerminal(Terminal terminal, String id, String conductingEquipmentId, String connectivityNodeId, int sequenceNumber) {
        mapTerminal2Id.computeIfAbsent(terminal, k -> {
            try {
                TerminalEq.write(id, conductingEquipmentId, connectivityNodeId, sequenceNumber, cimNamespace, xmlWriter);
                return id;
            } catch (XMLStreamException e) {
                throw new UncheckedXmlStreamException(e);
            }
        });
    }

    private String exportedTerminalId(Terminal terminal) {
        if (mapTerminal2Id.containsKey(terminal)) {
            return mapTerminal2Id.get(terminal);
        } else {
            throw new PowsyblException("Terminal has not been exported");
        }
    }

    private String connectivityNodeId(Terminal terminal) {
        String key;
        if (terminal.getVoltageLevel().getTopologyKind().equals(TopologyKind.NODE_BREAKER)) {
            key = buildNodeKey(terminal.getVoltageLevel(), terminal.getNodeBreakerView().getNode());
        } else {
            key = buildNodeKey(terminal.getBusBreakerView().getConnectableBus());
        }
        return mapNodeKey2NodeId.get(key);
    }

    private static class VoltageLevelAdjacency {

        private final List<List<Integer>> voltageLevelNodes;

        VoltageLevelAdjacency(VoltageLevel vl, CgmesExportContext context) {
            voltageLevelNodes = new ArrayList<>();

            NodeAdjacency adjacency = new NodeAdjacency(vl, context);
            Set<Integer> visitedNodes = new HashSet<>();
            adjacency.get().keySet().forEach(node -> {
                if (visitedNodes.contains(node)) {
                    return;
                }
                List<Integer> adjacentNodes = computeAdjacentNodes(node, adjacency, visitedNodes);
                voltageLevelNodes.add(adjacentNodes);
            });
        }

        private List<Integer> computeAdjacentNodes(int nodeId, NodeAdjacency adjacency, Set<Integer> visitedNodes) {

            List<Integer> adjacentNodes = new ArrayList<>();
            adjacentNodes.add(nodeId);
            visitedNodes.add(nodeId);

            int k = 0;
            while (k < adjacentNodes.size()) {
                Integer node = adjacentNodes.get(k);
                if (adjacency.get().containsKey(node)) {
                    adjacency.get().get(node).forEach(adjacent -> {
                        if (visitedNodes.contains(adjacent)) {
                            return;
                        }
                        adjacentNodes.add(adjacent);
                        visitedNodes.add(adjacent);
                    });
                }
                k++;
            }
            return adjacentNodes;
        }

        List<List<Integer>> getNodes() {
            return voltageLevelNodes;
        }
    }

    private static class NodeAdjacency {

        private final Map<Integer, List<Integer>> adjacency;

        NodeAdjacency(VoltageLevel vl, CgmesExportContext context) {
            adjacency = new HashMap<>();
            if (vl.getTopologyKind().equals(TopologyKind.NODE_BREAKER)) {
                vl.getNodeBreakerView().getInternalConnections().forEach(this::addAdjacency);
                // When computing the connectivity nodes for the voltage level,
                // switches that are not exported as equipment (they are fictitious)
                // are equivalent to internal connections
                vl.getNodeBreakerView().getSwitchStream()
                        .filter(Objects::nonNull)
                        .filter(sw -> !context.isExportedEquipment(sw))
                        .forEach(this::addAdjacency);
            }
        }

        private void addAdjacency(VoltageLevel.NodeBreakerView.InternalConnection ic) {
            addAdjacency(ic.getNode1(), ic.getNode2());
        }

        private void addAdjacency(Switch sw) {
            addAdjacency(sw.getVoltageLevel().getNodeBreakerView().getNode1(sw.getId()), sw.getVoltageLevel().getNodeBreakerView().getNode2(sw.getId()));
        }

        private void addAdjacency(int node1, int node2) {
            adjacency.computeIfAbsent(node1, k -> new ArrayList<>()).add(node2);
            adjacency.computeIfAbsent(node2, k -> new ArrayList<>()).add(node1);
        }

        Map<Integer, List<Integer>> get() {
            return adjacency;
        }
    }
}
