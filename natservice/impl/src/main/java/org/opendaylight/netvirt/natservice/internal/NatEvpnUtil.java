/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NatEvpnUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NatEvpnUtil.class);

    private NatEvpnUtil() { }

    static long getLPortTagForRouter(String routerIdKey, IdManagerService idManager) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerIdKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("getLPortTagForRouter : ID manager failed while allocating lport_tag for router {}.",
                    routerIdKey, e);
        }
        return 0;
    }

    public static void releaseLPortTagForRouter(DataBroker dataBroker, IdManagerService idManager, String routerName) {

        String rd = NatUtil.getVpnRd(dataBroker, routerName);
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (!NatEvpnUtil.isL3VpnOverVxLan(l3Vni)) {
            LOG.info("releaseLPortTagForRouter : Router:{} is not part of L3VPNOverVxlan", routerName);
            return;
        }
        ReleaseIdInput getIdInput = new ReleaseIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerName)
                .build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManager.releaseId(getIdInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("releaseLPortTagForRouter:ID manager failed while releasing allocated lport_tag "
                        + "for router {}. Exception {} ", routerName, rpcResult.getErrors());
                return;
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("releaseLPortTagForRouter:ID : ID manager failed while releasing allocated lport_tag "
                    + "for router {}.", routerName, e);
        }
    }

    public static long getTunnelIdForRouter(IdManagerService idManager, DataBroker dataBroker, String routerName,
                                            long routerId) {
        /* Only if the router is part of an L3VPN-Over-VXLAN, Router_lPort_Tag which will be carved out per router
         from 'interfaces' POOL and used as tunnel_id. Otherwise we will continue to use router-id as the tunnel-id
         in the following Flows.

         1) PSNAT_TABLE (on Non-NAPT) -> Send to Remote Group
         2) INTERNAL_TUNNEL_TABLE (on NAPT) -> Send to OUTBOUND_NAPT_TABLE */
        String rd = NatUtil.getVpnRd(dataBroker, routerName);
        long l3Vni = getL3Vni(dataBroker, rd);
        if (isL3VpnOverVxLan(l3Vni)) {
            long routerLportTag = getLPortTagForRouter(routerName, idManager);
            if (routerLportTag != 0) {
                LOG.trace("getTunnelIdForRouter : Successfully allocated Router_lPort_Tag = {} from ID Manager for "
                        + "Router ID = {}", routerLportTag, routerId);
                return routerLportTag;
            } else {
                LOG.warn("getTunnelIdForRouter : Failed to allocate Router_lPort_Tag from ID Manager for Router ID:{} "
                        + "Continue to use router-id as tunnel-id", routerId);
                return routerId;
            }
        }
        return routerId;
    }

    static long getL3Vni(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpData(broker, rd);
        if (vpnInstanceOpDataEntry != null && vpnInstanceOpDataEntry.getL3vni() != null) {
            return vpnInstanceOpDataEntry.getL3vni();
        }
        return NatConstants.DEFAULT_L3VNI_VALUE;
    }

    private static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(rd);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.OPERATIONAL, id).orNull();
    }

    private static boolean isL3VpnOverVxLan(Long l3Vni) {
        return l3Vni != null && l3Vni != 0;
    }

    static ProviderTypes getExtNwProvTypeFromRouterName(DataBroker dataBroker, String routerName,
            Uuid externalNetworkId) {
        ProviderTypes extNwProviderType = null;
        if (externalNetworkId == null) {
            LOG.error("getExtNwProvTypeFromRouterName : external network UUID is not available for router {}",
                    routerName);
            return extNwProviderType;
        }
        extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, externalNetworkId);
        if (extNwProviderType == null) {
            LOG.error("getExtNwProvTypeFromRouterName : Could not retrieve provider type for external network {}",
                    externalNetworkId);
            return extNwProviderType;
        }
        return extNwProviderType;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addRoutesForVxLanProvType(DataBroker broker,
                                                 IBgpManager bgpManager,
                                                 IFibManager fibManager,
                                                 String vpnName,
                                                 String rd,
                                                 String prefix,
                                                 String nextHopIp,
                                                 long l3Vni,
                                                 @Nullable String interfaceName,
                                                 String gwMacAddress,
                                                 TypedWriteTransaction<Configuration> writeTx,
                                                 RouteOrigin origin, BigInteger dpId,
                                                 Uuid networkId) {
        try {
            LOG.info("addRoutesForVxLanProvType : Adding Fib entry rd {} prefix {} nextHop {} l3Vni {}",
                    rd, prefix, nextHopIp, l3Vni);
            if (nextHopIp == null) {
                LOG.error("addRoutesForVxLanProvType : addPrefix failed since nextHopIp cannot be null for prefix {}",
                        prefix);
                return;
            }
            NatUtil.addPrefixToInterface(broker, NatUtil.getVpnId(broker, vpnName), interfaceName, prefix,
                    networkId.getValue(), dpId, Prefixes.PrefixCue.Nat);

            fibManager.addOrUpdateFibEntry(rd, null /*macAddress*/, prefix,
                    Collections.singletonList(nextHopIp), VrfEntry.EncapType.Vxlan, NatConstants.DEFAULT_LABEL_VALUE,
                l3Vni, gwMacAddress, null /* parent-vpn-rd */, origin, writeTx);
            /* Publish to Bgp only if its an INTERNET VPN */
            if (rd != null && !rd.equalsIgnoreCase(vpnName)) {
                bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, Collections.singletonList(nextHopIp),
                        VrfEntry.EncapType.Vxlan, NatConstants.DEFAULT_LABEL_VALUE, l3Vni, 0 /*l2vni*/,
                        gwMacAddress);
            }
            LOG.info("addRoutesForVxLanProvType : Added Fib entry rd {} prefix {} nextHop {} l3Vni {}", rd, prefix,
                        nextHopIp, l3Vni);
        } catch (Exception e) {
            LOG.error("addRoutesForVxLanProvType : Failed while adding routes for prefix {}", prefix, e);
        }
    }

    static void makeL3GwMacTableEntry(final BigInteger dpnId, final long vpnId, String macAddress,
        List<Instruction> customInstructions, IMdsalApiManager mdsalManager,
        TypedWriteTransaction<Configuration> confTx) {

        List<MatchInfo> matchInfo = new ArrayList<>();
        matchInfo.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matchInfo.add(new MatchEthernetDestination(new MacAddress(macAddress)));
        LOG.debug("makeL3GwMacTableEntry : Create flow table {} -> table {} for External Vpn Id = {} "
                + "and MacAddress = {} on DpnId = {}",
                NwConstants.L3_GW_MAC_TABLE, NwConstants.INBOUND_NAPT_TABLE, vpnId, macAddress, dpnId);
        // Install the flow entry in L3_GW_MAC_TABLE
        String flowRef = NatUtil.getFlowRef(dpnId, NwConstants.L3_GW_MAC_TABLE, vpnId, macAddress);
        Flow l3GwMacTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_GW_MAC_TABLE,
                flowRef, 21, flowRef, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, matchInfo, customInstructions);

        mdsalManager.addFlow(confTx, dpnId, l3GwMacTableFlowEntity);
        LOG.debug("makeL3GwMacTableEntry : Successfully created flow entity {} on DPN = {}",
                l3GwMacTableFlowEntity, dpnId);
    }

    static void removeL3GwMacTableEntry(final BigInteger dpnId, final long vpnId, final String macAddress,
            IMdsalApiManager mdsalManager, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        List<MatchInfo> matchInfo = new ArrayList<>();
        matchInfo.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matchInfo.add(new MatchEthernetSource(new MacAddress(macAddress)));
        LOG.debug("removeL3GwMacTableEntry : Remove flow table {} -> table {} for External Vpn Id = {} "
                + "and MacAddress = {} on DpnId = {}",
                NwConstants.L3_GW_MAC_TABLE, NwConstants.INBOUND_NAPT_TABLE, vpnId, macAddress, dpnId);
        // Remove the flow entry in L3_GW_MAC_TABLE
        String flowRef = NatUtil.getFlowRef(dpnId, NwConstants.L3_GW_MAC_TABLE, vpnId, macAddress);
        Flow l3GwMacTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_GW_MAC_TABLE,
                flowRef, 21, flowRef, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, matchInfo, null);

        mdsalManager.removeFlow(confTx, dpnId, l3GwMacTableFlowEntity);
        LOG.debug("removeL3GwMacTableEntry : Successfully removed flow entity {} on DPN = {}",
                l3GwMacTableFlowEntity, dpnId);
    }

    public static String getFlowRef(BigInteger dpnId, short tableId, long l3Vni, String flowName) {
        return flowName + NwConstants.FLOWID_SEPARATOR + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants
                .FLOWID_SEPARATOR + l3Vni;
    }

    @Nullable
    public static Uuid getFloatingIpInterfaceIdFromFloatingIpId(DataBroker broker, Uuid floatingIpId) {
        InstanceIdentifier<FloatingIpIdToPortMapping> id =
                NatUtil.buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                FloatingIpIdToPortMapping::getFloatingIpPortId).orElse(null);
    }
}
