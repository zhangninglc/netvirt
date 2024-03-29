/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.utils;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionPopMpls;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchMplsLabel;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.VpnToPseudoPortList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortDataKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg2;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VpnServiceChainUtils {

    private static final Logger LOG = LoggerFactory.getLogger(VpnServiceChainUtils.class);

    private VpnServiceChainUtils() { }

    public static BigInteger getMetadataSCF(long scfTag) { // TODO: Move to a common place
        return new BigInteger("FF", 16).and(BigInteger.valueOf(scfTag)).shiftLeft(32);
    }

    public static BigInteger getCookieL3(int vpnId) {
        return CloudServiceChainConstants.COOKIE_L3_BASE.add(new BigInteger("0610000", 16))
                                                      .add(BigInteger.valueOf(vpnId));
    }


    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    public static InstanceIdentifier<BoundServices> buildBoundServicesIid(short servicePrio, String ifaceName) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                                 .child(ServicesInfo.class, new ServicesInfoKey(ifaceName, ServiceModeIngress.class))
                                 .child(BoundServices.class, new BoundServicesKey(servicePrio))
                                 .build();
    }

    /**
     * Retrieves from MDSAL the Operational Data of the VPN specified by its
     * Route-Distinguisher.
     *
     * @param rd Route-Distinguisher of the VPN
     *
     * @return the Operational Data of the VPN or absent if no VPN could be
     *         found for the given RD or if the VPN does not have operational
     *         data.
     */
    public static Optional<VpnInstanceOpDataEntry> getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnServiceChainUtils.getVpnInstanceOpDataIdentifier(rd);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    }

    public static InstanceIdentifier<VrfTables> buildVrfId(String rd) {
        return InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
    }

    public static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
            .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    public static InstanceIdentifier<VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                                 .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    public static InstanceIdentifier<VpnToPseudoPortData> getVpnToPseudoPortTagIid(String rd) {
        VpnToPseudoPortDataKey key = new VpnToPseudoPortDataKey(rd);
        return InstanceIdentifier.builder(VpnToPseudoPortList.class).child(VpnToPseudoPortData.class, key).build();
    }

    public static Optional<VpnToPseudoPortData> getVpnPseudoPortData(DataBroker broker, String rd)
            throws ReadFailedException {
        InstanceIdentifier<VpnToPseudoPortData> id = getVpnToPseudoPortTagIid(rd);
        return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, id);
    }

    public static List<VpnToPseudoPortData> getAllVpnToPseudoPortData(DataBroker broker) throws ReadFailedException {
        InstanceIdentifier<VpnToPseudoPortList> path = InstanceIdentifier.builder(VpnToPseudoPortList.class).build();
        return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION, path)
                .toJavaUtil().map(VpnToPseudoPortList::getVpnToPseudoPortData).orElse(new ArrayList<>());
    }

    /**
     * Get fake VPNPseudoPort interface name.
     *
     * @param dpId Dpn Id
     * @param scfTag Service Function tag
     * @param scsTag Service Chain tag
     * @param lportTag Lport tag
     * @return the fake VpnPseudoPort interface name
     */
    public static String buildVpnPseudoPortIfName(Long dpId, long scfTag, int scsTag, int lportTag) {
        return new StringBuilder("VpnPseudo.").append(dpId).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(lportTag).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(scfTag).append(NwConstants.FLOWID_SEPARATOR)
                                              .append(scsTag).toString();
    }

    /**
     * Retrieves the VpnId (datapath id) searching by VpnInstanceName.
     *
     * @param broker Reference to the MDSAL Databroker service
     * @param vpnName The Vpn instance name
     * @return the datapath identifier for the specified VPN
     */
    public static long getVpnId(DataBroker broker, String vpnName) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
                .l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id =
                getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
                .l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        return vpnInstance.isPresent() ? vpnInstance.get().getVpnId() : CloudServiceChainConstants.INVALID_VPN_TAG;
    }

    /**
     * Retrieves the VPN's Route Distinguisher out from the VpnName.
     *
     * @param broker Reference to the MDSAL Databroker service
     * @param vpnName The Vpn Instance Name. Typically the UUID.
     * @return the RouteDistinguiser for the specified VPN
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                VpnInstance::getVrfId).orElse(null);
    }

    /**
     * Returns all the VrfEntries that belong to a given VPN.
     *
     * @param broker Reference to the MDSAL Databroker service
     * @param rd Route-distinguisher of the VPN
     * @return the list of the matching VrfEntries
     */
    public static List<VrfEntry> getAllVrfEntries(DataBroker broker, String rd) {
        InstanceIdentifier<VrfTables> vpnVrfTables =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTables).toJavaUtil().map(
                VrfTables::getVrfEntry).orElse(new ArrayList<>());
    }

    /**
     * Installs/removes a flow in LPortDispatcher table that is in charge of
     * handling packets that falls back from SCF Pipeline to L3Vpn.
     *
     * @param mdsalManager  MDSAL Util API accessor
     * @param vpnId Dataplane identifier of the VPN, the Vrf Tag.
     * @param dpId The DPN where the flow must be installed/removed
     * @param vpnPseudoLportTag Dataplane identifier for the VpnPseudoPort
     * @param addOrRemove States if the flow must be created or removed
     */
    public static void programLPortDispatcherFlowForScfToVpn(IMdsalApiManager mdsalManager, long vpnId, BigInteger dpId,
                                                             Integer vpnPseudoLportTag, int addOrRemove) {
        LOG.trace("programLPortDispatcherFlowForScfToVpn: vpnId={} dpId={}, vpnPseudoLportTag={} addOrRemove={}",
                  vpnId, dpId, vpnPseudoLportTag, addOrRemove);
        Flow flow = buildLPortDispFromScfToL3VpnFlow(vpnId, dpId, vpnPseudoLportTag, addOrRemove);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.installFlow(dpId, flow);
        } else {
            mdsalManager.removeFlow(dpId, flow);
        }
    }

    /**
     * Build the flow that must be inserted when there is a ScHop whose
     * egressPort is a VPN Pseudo Port. In that case, packets must be moved
     * from the SCF to VPN Pipeline.
     * <p>
     * Flow matches:  VpnPseudo port lPortTag + SI=L3VPN
     * Actions: Write vrfTag in Metadata + goto FIB Table
     * </p>
     * @param vpnId Dataplane identifier of the VPN, the Vrf Tag.
     * @param dpId The DPN where the flow must be installed/removed
     * @param lportTag Dataplane identifier for the VpnPseudoPort
     * @param addOrRemove States if it must build a Flow to be created or
     *     removed
     *
     * @return the Flow object
     */
    public static Flow buildLPortDispFromScfToL3VpnFlow(Long vpnId, BigInteger dpId, Integer lportTag,
                                                        int addOrRemove) {
        LOG.info("buildLPortDispFlowForScf vpnId={} dpId={} lportTag={} addOrRemove={} ",
                 vpnId, dpId, lportTag, addOrRemove);
        List<MatchInfo> matches = buildMatchOnLportTagAndSI(lportTag,
                                                            ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                                                  NwConstants.L3VPN_SERVICE_INDEX));
        List<Instruction> instructions = buildSetVrfTagAndGotoFibInstructions(vpnId.intValue());

        String flowRef = getScfToL3VpnLportDispatcherFlowRef(lportTag);

        Flow result;
        if (addOrRemove == NwConstants.ADD_FLOW) {
            result = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                            CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef,
                                            0, 0, VpnServiceChainUtils.getCookieL3(vpnId.intValue()),
                                            matches, instructions);

        } else {
            result = new FlowBuilder().setTableId(NwConstants.LPORT_DISPATCHER_TABLE)
                                      .setId(new FlowId(flowRef))
                                      .build();
        }
        return result;
    }


    /**
     * Builds the Match for flows that must match on a given lportTag and
     * serviceIndex.
     *
     * @return the Match as a list.
     */
    public static List<MatchInfo> buildMatchOnLportTagAndSI(Integer lportTag, short serviceIndex) {
        return Collections.singletonList(
                new MatchMetadata(MetaDataUtil.getMetaDataForLPortDispatcher(lportTag, serviceIndex),
                        MetaDataUtil.getMetaDataMaskForLPortDispatcher()));
    }

    /**
     * Builds a The Instructions that sets the VpnTag in metadata and sends to
     * FIB table.
     *
     * @param vpnTag Dataplane identifier of the VPN.
     * @return the list of Instructions
     */
    public static List<Instruction> buildSetVrfTagAndGotoFibInstructions(Integer vpnTag) {
        List<Instruction> result = new ArrayList<>();
        int instructionKey = 0;
        result.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnTag),
                                                               MetaDataUtil.METADATA_MASK_VRFID,
                                                               ++instructionKey));
        result.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_FIB_TABLE, ++instructionKey));
        return result;
    }


    /**
     * Builds a Flow for the LFIB table that sets the LPortTag of the
     * VpnPseudoPort and sends to LPortDispatcher table.
     * <ul>
     * <li>Matching: eth_type = MPLS, mpls_label = VPN MPLS label
     * <li>Actions: setMetadata LportTag and SI=2, pop MPLS, Go to
     *              LPortDispacherTable
     * </ul>
     *
     * @param dpId  DpnId
     * @param label MPLS label
     * @param nextHop Next Hop IP
     * @param lportTag Pseudo Logical Port tag
     * @return the FlowEntity
     */
    public static FlowEntity buildLFibVpnPseudoPortFlow(BigInteger dpId, Long label, int etherType, String nextHop,
                                                        int lportTag) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(label));

        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionPopMpls(etherType));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(
                       MetaDataUtil.getMetaDataForLPortDispatcher(lportTag,
                                                                  ServiceIndex.getIndex(NwConstants.SCF_SERVICE_NAME,
                                                                                        NwConstants.SCF_SERVICE_INDEX)),
                       MetaDataUtil.getMetaDataMaskForLPortDispatcher()
        ));

        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE));
        String flowRef = getLFibVpnPseudoPortFlowRef(lportTag, label, nextHop);
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef,
                                         CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef, 0, 0,
                                         NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);
    }


    /**
     * Modifies the LFIB table by adding/removing flows that redirects traffic
     * from a VPN into the SCF via the VpnPseudoLport.
     * Flows that match on the label and sets the VpnPseudoPort lportTag and
     * sends to LPortDispatcher (via table 80)
     *
     */
    public static void programLFibEntriesForSCF(IMdsalApiManager mdsalMgr, BigInteger dpId, List<VrfEntry> vrfEntries,
            int lportTag, int addOrRemove) {
        LOG.trace("programLFibEntriesForSCF:  dpId={}  lportTag={}  addOrRemove={}", dpId, lportTag, addOrRemove);
        if (vrfEntries != null) {
            vrfEntries.forEach(vrfEntry -> vrfEntry.getRoutePaths()
                    .forEach(routePath -> {
                        Long label = routePath.getLabel();
                        String nextHop = routePath.getNexthopAddress();
                        try {
                            int etherType = NWUtil.getEtherTypeFromIpPrefix(vrfEntry.getDestPrefix());
                            FlowEntity flowEntity = buildLFibVpnPseudoPortFlow(dpId, label, etherType, nextHop,
                                    lportTag);
                            if (addOrRemove == NwConstants.ADD_FLOW) {
                                mdsalMgr.installFlow(flowEntity);
                            } else {
                                mdsalMgr.removeFlow(flowEntity);
                            }
                            LOG.debug(
                                    "LFIBEntry for label={}, destination={}, nexthop={} {} successfully in dpn={}",
                                    label, vrfEntry.getDestPrefix(), nextHop,
                                    addOrRemove == NwConstants.DEL_FLOW ? "removed" : "installed", dpId);
                        } catch (IllegalArgumentException ex) {
                            LOG.warn("Unable to get etherType for IP Prefix {}", vrfEntry.getDestPrefix());
                        }
                    }));
        }
    }

    /**
     * Installs/removes a flow in LPortDispatcher table that is in charge
     * of sending the traffic to the SCF Pipeline.
     *
     */
    public static void programLPortDispatcherFlowForVpnToScf(IMdsalApiManager mdsalManager, BigInteger dpId,
                                                             int lportTag, long scfTag, short gotoTableId,
                                                             int addOrRemove) {
        LOG.trace("programLPortDispatcherFlowForVpnToScf: dpId={} lportTag={} scfTag={} gotoTableId={} addOrRemove={}",
                  dpId, lportTag, scfTag, gotoTableId, addOrRemove);
        FlowEntity flowEntity = VpnServiceChainUtils.buildLportFlowDispForVpnToScf(dpId, lportTag, scfTag, gotoTableId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }

    /**
     * Creates the flow that sends the packet from the VPN to the SCF pipeline.
     * This usually happens when there is an ScHop whose ingressPort is a
     * VpnPseudoPort.
     * <ul>
     * <li>Matches: lportTag = vpnPseudoLPortTag, SI = 1
     * <li>Actions: setMetadata(scfTag), Go to: UpSubFilter table
     * </ul>
     */
    public static FlowEntity buildLportFlowDispForVpnToScf(BigInteger dpId, int lportTag, long scfTag,
                                                           short gotoTableId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionRegLoad(NxmNxReg2.class, 0, 31, scfTag));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(gotoTableId));
        String flowRef = getL3VpnToScfLportDispatcherFlowRef(lportTag);

        List<MatchInfo> matches =
                buildMatchOnLportTagAndSI(lportTag, ServiceIndex.getIndex(NwConstants.SCF_SERVICE_NAME,
                        NwConstants.SCF_SERVICE_INDEX));
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                         CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY, flowRef, 0, 0,
                                         getCookieSCHop(scfTag), matches, instructions);

    }

    public static Optional<Long> getVpnPseudoLportTag(DataBroker broker, String rd) {
        InstanceIdentifier<VpnToPseudoPortData> path = getVpnToPseudoPortTagIid(rd);
        return Optional.fromJavaUtil(MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path).toJavaUtil().map(
                VpnToPseudoPortData::getVpnLportTag));
    }

    /**
     * Creates a Flow that does the trick of moving the packets from one VPN to
     * another VPN.
     *
     */
    public static Flow buildLPortDispFlowForVpntoVpn(Integer dstLportTag, Integer vpnTag) {
        List<MatchInfo> matches =
            buildMatchOnLportTagAndSI(dstLportTag, ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                                         NwConstants.L3VPN_SERVICE_INDEX));
        List<Instruction> instructions = buildSetVrfTagAndGotoFibInstructions(vpnTag);
        String flowRef = getL3VpnToL3VpnLportDispFlowRef(dstLportTag, vpnTag);

        return MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                                      CloudServiceChainConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY, flowRef,
                                      0, 0, VpnServiceChainUtils.getCookieL3(vpnTag),
                                      matches, instructions);
    }


    private static BigInteger getCookieSCHop(long scfInstanceTag) {
        return CloudServiceChainConstants.COOKIE_SCF_BASE.add(new BigInteger("0610000", 16))
                .add(BigInteger.valueOf(scfInstanceTag));
    }

    /*
     * Id for the Flow that is inserted in LFIB that is in charge of receiving packets coming from
     * DC-GW and setting the VpnPseudoLport tag in metadata and send to LPortDispatcher. There is
     * one of this entries per VrfEntry.
     */
    private static String getLFibVpnPseudoPortFlowRef(int vpnLportTag, long label, String nextHop) {
        return new StringBuilder(64).append(CloudServiceChainConstants.VPN_PSEUDO_PORT_FLOWID_PREFIX)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(vpnLportTag)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(label)
                                    .append(NwConstants.FLOWID_SEPARATOR).append(nextHop).toString();
    }

    private static String getL3VpnToL3VpnLportDispFlowRef(Integer lportTag, Integer vpnTag) {
        return new StringBuilder().append(CloudServiceChainConstants.FLOWID_PREFIX_L3).append(lportTag)
                                  .append(NwConstants.FLOWID_SEPARATOR).append(vpnTag)
                                  .append(NwConstants.FLOWID_SEPARATOR)
                                  .append(CloudServiceChainConstants.DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY).toString();
    }

    /**
     * Id for the Flow that is inserted in LPortDispatcher table that is in
     * charge of delivering packets from the L3VPN to the SCF Pipeline.
     * The VpnPseudoLPort tag and the SCF_SERVICE_INDEX is enough to identify
     * this kind of flows.
     */
    public static String getL3VpnToScfLportDispatcherFlowRef(Integer lportTag) {
        return new StringBuilder(64).append(CloudServiceChainConstants.VPN_PSEUDO_VPN2SCF_FLOWID_PREFIX)
                                   .append(lportTag)
                                   .append(NwConstants.FLOWID_SEPARATOR)
                                   .append(ServiceIndex.getIndex(NwConstants.SCF_SERVICE_NAME,
                                                                 NwConstants.SCF_SERVICE_INDEX))
                                   .append(NwConstants.FLOWID_SEPARATOR)
                                   .append(CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY).toString();
    }

    /**
     * Builds an identifier for the flow that is inserted in LPortDispatcher
     * table and that is in charge of handling packets that are delivered from
     * the SCF to the L3VPN Pipeline.
     *
     */
    public static String getScfToL3VpnLportDispatcherFlowRef(Integer lportTag) {
        return new StringBuilder().append(CloudServiceChainConstants.VPN_PSEUDO_SCF2VPN_FLOWID_PREFIX).append(lportTag)
                                 .append(NwConstants.FLOWID_SEPARATOR)
                                 .append(ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                               NwConstants.L3VPN_SERVICE_INDEX))
                                 .append(NwConstants.FLOWID_SEPARATOR)
                                 .append(CloudServiceChainConstants.DEFAULT_SCF_FLOW_PRIORITY).toString();
    }

    public static List<String> getAllVpnIfaceNames(DataBroker dataBroker, String vpnName) {

        String vpnRd = getVpnRd(dataBroker, vpnName);
        InstanceIdentifier<VpnInstanceOpDataEntry> vpnOpDataIid =
            InstanceIdentifier.builder(VpnInstanceOpData.class)
                              .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vpnRd)).build();

        try {
            VpnInstanceOpDataEntry vpnOpData =
                SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.OPERATIONAL, vpnOpDataIid);

            if (vpnOpData == null) {
                return Collections.emptyList();
            }
            List<VpnToDpnList> dpnToVpns = vpnOpData.getVpnToDpnList();
            if (dpnToVpns == null) {
                return Collections.emptyList();
            }

            return dpnToVpns.stream()
                            .filter(dpn -> dpn.getVpnInterfaces() != null)
                            .flatMap(dpn -> dpn.getVpnInterfaces().stream())
                            .map(VpnInterfaces::getInterfaceName)
                            .collect(Collectors.toList());
        } catch (ReadFailedException e) {
            LOG.warn("getAllVpnInterfaces for vpn {}: Failure on read operation", vpnName, e);
            return Collections.emptyList();
        }
    }
}
