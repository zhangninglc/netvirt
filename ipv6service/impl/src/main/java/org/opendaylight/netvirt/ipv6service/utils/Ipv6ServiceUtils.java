/*
 * Copyright © 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.ipv6util.api.Icmpv6Type;
import org.opendaylight.genius.ipv6util.api.Ipv6Constants;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NwConstants.NxmOfFieldType;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.FlowMod;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6NdTarget;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.ipv6service.VirtualSubnet;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev181010.Ipv6serviceConfig;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceUtils.class);
    public static final Ipv6Address ALL_NODES_MCAST_ADDR = newIpv6Address(Ipv6Constants.ALL_NODES_MCAST_ADDRESS);
    public static final Ipv6Address UNSPECIFIED_ADDR = newIpv6Address("0:0:0:0:0:0:0:0");

    @Nullable
    private static Ipv6Address newIpv6Address(String ip) {
        try {
            return Ipv6Address.getDefaultInstance(InetAddress.getByName(ip).getHostAddress());
        } catch (UnknownHostException e) {
            LOG.error("Ipv6ServiceUtils: Error instantiating ipv6 address", e);
            return null;
        }
    }

    private final DataBroker broker;
    private final IMdsalApiManager mdsalUtil;
    private final ManagedNewTransactionRunner txRunner;
    private final Ipv6serviceConfig ipv6serviceConfig;

    @Inject
    public Ipv6ServiceUtils(DataBroker broker, IMdsalApiManager mdsalUtil, Ipv6serviceConfig ipv6ServiceConfig) {
        this.broker = broker;
        this.mdsalUtil = mdsalUtil;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.ipv6serviceConfig = ipv6ServiceConfig;
    }

    /**
     * Retrieves the object from the datastore.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @return the required object.
     */
    public <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the Interface from the datastore.
     * @param interfaceName the interface name
     * @return the interface.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .@Nullable Interface getInterface(String interfaceName) {
        return read(LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName)).orNull();
    }

    /**
     * Builds the interface identifier.
     * @param interfaceName the interface name.
     * @return the interface identifier.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
                .child(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                                .Interface.class, new InterfaceKey(interfaceName)).build();
    }

    /**
     * Build the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                        .rev140508.interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    /**
     * Retrieves the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .@Nullable Interface getInterfaceStateFromOperDS(String interfaceName) {
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, buildStateInterfaceId(interfaceName), broker).orNull();
    }

    private static List<MatchInfo> getIcmpv6RSMatch(Long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.ROUTER_SOLICITATION.getValue(), (short) 0));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NSMatch(Long elanTag, Ipv6Address ipv6Address) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_SOLICITATION.getValue(), (short) 0));
        matches.add(new MatchIpv6NdTarget(ipv6Address));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NAMatch(Long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Icmpv6Type.NEIGHBOR_ADVERTISEMENT.getValue(), (short) 0));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private static String getIPv6FlowRef(BigInteger dpId, Long elanTag, String flowType) {
        return new StringBuilder().append(Ipv6ServiceConstants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(elanTag).append(Ipv6ServiceConstants.FLOWID_SEPARATOR)
                .append(flowType).toString();
    }

    public void installIcmpv6NsPuntFlow(short tableId, BigInteger dpId, Long elanTag, Ipv6Address ipv6Address,
            int addOrRemove) {
        String flowId = getIPv6FlowRef(dpId, elanTag, Ipv6Util.getFormattedIpv6Address(ipv6Address));

        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Neighbor Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.removeFlow(tx, dpId, flowId, tableId);
                    }), LOG, "Error while removing flow={}", flowId);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionPuntToController());

            int ndPuntTimeout = ipv6serviceConfig.getNeighborDiscoveryPuntTimeout();
            if (isNdPuntProtectionEnabled(ndPuntTimeout)) {
                actionsInfos.add(getLearnActionForNsPuntProtection(ndPuntTimeout));
            }
            List<InstructionInfo> instructions = Arrays.asList(new InstructionApplyActions(actionsInfos));
            List<MatchInfo> nsMatch = getIcmpv6NSMatch(elanTag, ipv6Address);
            FlowEntity nsFlowEntity =
                    MDSALUtil.buildFlowEntity(dpId, tableId, flowId, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                            "IPv6NS", 0, 0, NwConstants.COOKIE_IPV6_TABLE, nsMatch, instructions);

            LOG.trace("Installing IPv6 Neighbor Solicitation Flow DpId={}, elanTag={} ipv6Address={}", dpId, elanTag,
                    ipv6Address.getValue());
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.addFlow(tx, nsFlowEntity);
                    }), LOG, "Error while adding flow={}", nsFlowEntity);
        }
    }

    private ActionLearn getLearnActionForNsPuntProtection(int ndPuntTimeout) {
        List<FlowMod> flowMods = getFlowModsForIpv6PuntProtection(Icmpv6Type.NEIGHBOR_SOLICITATION);
        flowMods.add(new ActionLearn.MatchFromField(NxmOfFieldType.NXM_NX_ND_TARGET.getType(),
                NxmOfFieldType.NXM_NX_ND_TARGET.getType(), NxmOfFieldType.NXM_NX_ND_TARGET.getFlowModHeaderLenInt()));

        return new ActionLearn(0, ndPuntTimeout, Ipv6ServiceConstants.NS_PUNT_PROTECTION_FLOW_PRIORITY,
                NwConstants.COOKIE_IPV6_TABLE, 0, NwConstants.IPV6_TABLE, 0, 0, flowMods);
    }

    public void installIcmpv6RsPuntFlow(short tableId, BigInteger dpId, Long elanTag, int addOrRemove) {
        if (dpId == null || dpId.equals(Ipv6ServiceConstants.INVALID_DPID)) {
            return;
        }
        String flowId = getIPv6FlowRef(dpId, elanTag, "IPv6RS");
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.removeFlow(tx, dpId, flowId, tableId);
                    }), LOG, "Error while removing flow={}", flowId);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            // Punt to controller
            actionsInfos.add(new ActionPuntToController());

            int rdPuntTimeout = ipv6serviceConfig.getRouterDiscoveryPuntTimeout();
            if (isRdPuntProtectionEnabled(rdPuntTimeout)) {
                actionsInfos.add(getLearnActionForRsPuntProtection(rdPuntTimeout));
            }
            List<InstructionInfo> instructions = Arrays.asList(new InstructionApplyActions(actionsInfos));
            List<MatchInfo> routerSolicitationMatch = getIcmpv6RSMatch(elanTag);
            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    flowId,Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY, "IPv6RS", 0, 0,
                    NwConstants.COOKIE_IPV6_TABLE, routerSolicitationMatch, instructions);

            LOG.trace("Installing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            LoggingFutures
                    .addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                        mdsalUtil.addFlow(tx, rsFlowEntity);
                    }), LOG, "Error while adding flow={}", rsFlowEntity);
        }
    }

    private ActionLearn getLearnActionForRsPuntProtection(int rdPuntTimeout) {
        return new ActionLearn(0, rdPuntTimeout, Ipv6ServiceConstants.RS_PUNT_PROTECTION_FLOW_PRIORITY,
                NwConstants.COOKIE_IPV6_TABLE, 0, NwConstants.IPV6_TABLE, 0, 0,
                getFlowModsForIpv6PuntProtection(Icmpv6Type.ROUTER_SOLICITATION));
    }

    private List<FlowMod> getFlowModsForIpv6PuntProtection(Icmpv6Type icmpv6Type) {
        return new ArrayList<>(Arrays.asList(
                new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV6, NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                        NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromValue(IPProtocols.IPV6ICMP.shortValue(),
                        NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
                        NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromValue(icmpv6Type.getValue(), NxmOfFieldType.NXM_OF_ICMPv6_TYPE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_TYPE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NxmOfFieldType.NXM_OF_ICMPv6_CODE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_CODE.getType(),
                        NxmOfFieldType.NXM_OF_ICMPv6_CODE.getFlowModHeaderLenInt()),
                new ActionLearn.MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_LPORT_TAG_OFFSET, NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_LPORT_TAG_OFFSET, MetaDataUtil.METADATA_LPORT_TAG_BITLEN)));
    }

    private boolean isRdPuntProtectionEnabled(int rdPuntTimeout) {
        return rdPuntTimeout != 0;
    }

    private boolean isNdPuntProtectionEnabled(int ndPuntTimeout) {
        return ndPuntTimeout != 0;
    }

    public void installIcmpv6NaForwardFlow(short tableId, IVirtualPort vmPort, BigInteger dpId, Long elanTag,
            int addOrRemove) {
        List<MatchInfo> matches = getIcmpv6NAMatch(elanTag);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        for (Ipv6Address ipv6Address : vmPort.getIpv6Addresses()) {
            matches.add(new MatchIpv6Source(ipv6Address.getValue() + NwConstants.IPV6PREFIX));
            String flowId = getIPv6FlowRef(dpId, elanTag,
                    vmPort.getIntfUUID().getValue() + Ipv6ServiceConstants.FLOWID_SEPARATOR + ipv6Address.getValue());
            FlowEntity rsFlowEntity =
                    MDSALUtil.buildFlowEntity(dpId, tableId, flowId, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                            "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE, matches, instructions);
            if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
                LOG.trace("Removing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}, ipv6Address {}", dpId,
                        elanTag, ipv6Address.getValue());
                mdsalUtil.removeFlow(rsFlowEntity);
            } else {
                LOG.trace("Installing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}, ipv6Address {}", dpId,
                        elanTag, ipv6Address.getValue());
                mdsalUtil.installFlow(rsFlowEntity);
            }
        }
    }

    public void installIcmpv6NaPuntFlow(short tableId, Ipv6Prefix ipv6Prefix, BigInteger dpId, Long elanTag,
            int addOrRemove) {
        List<MatchInfo> naMatch = getIcmpv6NAMatch(elanTag);
        naMatch.add(new MatchIpv6Source(ipv6Prefix));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowId = getIPv6FlowRef(dpId, elanTag, "IPv6NA." + ipv6Prefix.getValue());
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                flowId, Ipv6ServiceConstants.PUNT_NA_FLOW_PRIORITY,
                "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE, naMatch, instructions);
        if (addOrRemove == Ipv6ServiceConstants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.removeFlow(rsFlowEntity);
        } else {
            LOG.trace("Installing IPv6 Neighbor Advertisement Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.installFlow(rsFlowEntity);
        }
    }

    public BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie)
                .setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().withKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    private InstanceIdentifier<BoundServices> buildServiceId(String interfaceName,
                                              short priority) {
        return InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class,
                new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(priority)).build();
    }

    public void bindIpv6Service(String interfaceName, Long elanTag, short tableId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        short serviceIndex = ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME, NwConstants.IPV6_SERVICE_INDEX);
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "ipv6", interfaceName),
                        serviceIndex, Ipv6ServiceConstants.DEFAULT_FLOW_PRIORITY,
                        NwConstants.COOKIE_IPV6_TABLE, instructions);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, serviceIndex), serviceInfo);
    }

    public void unbindIpv6Service(String interfaceName) {
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME,
                        NwConstants.IPV6_SERVICE_INDEX)));
    }

    @Nullable
    public BigInteger getDpIdFromInterfaceState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.Interface interfaceState) {
        BigInteger dpId = null;
        List<String> ofportIds = interfaceState.getLowerLayerIf();
        if (ofportIds != null && !ofportIds.isEmpty()) {
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        }
        return dpId;
    }

    public static long getRemoteBCGroup(long elanTag) {
        return Ipv6ServiceConstants.ELAN_GID_MIN + elanTag % Ipv6ServiceConstants.ELAN_GID_MIN * 2;
    }

    public static boolean isVmPort(String deviceOwner) {
        // FIXME: Currently for VM ports, Neutron is sending deviceOwner as empty instead of "compute:nova".
        // return Ipv6ServiceConstants.DEVICE_OWNER_COMPUTE_NOVA.equalsIgnoreCase(deviceOwner);
        return Ipv6ServiceConstants.DEVICE_OWNER_COMPUTE_NOVA.equalsIgnoreCase(deviceOwner)
                || StringUtils.isEmpty(deviceOwner);
    }

    public static boolean isIpv6Subnet(VirtualSubnet subnet) {
        if (subnet == null) {
            return false;
        }
        return subnet.getIpVersion().equals(Ipv6ServiceConstants.IP_VERSION_V6) ? true : false;
    }
}
