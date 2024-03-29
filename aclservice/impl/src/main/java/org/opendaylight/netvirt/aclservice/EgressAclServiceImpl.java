/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import static org.opendaylight.controller.md.sal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfo;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class EgressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(EgressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     */
    public EgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator, AclInterfaceCache aclInterfaceCache) {
        // Service mode is w.rt. switch
        super(ServiceModeIngress.class, dataBroker, mdsalManager, aclDataUtil, aclServiceUtils,
                jobCoordinator, aclInterfaceCache);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    public void bindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        jobCoordinator.enqueueJob(interfaceName, () -> {
            int instructionKey = 0;
            List<Instruction> instructions = new ArrayList<>();
            instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(getAclAntiSpoofingTable(), ++instructionKey));
            short serviceIndex = ServiceIndex.getIndex(AclConstants.INGRESS_ACL_SERVICE_NAME,
                    AclConstants.INGRESS_ACL_SERVICE_INDEX);
            int flowPriority = AclConstants.INGRESS_ACL_SERVICE_INDEX;
            BoundServices serviceInfo =
                    AclServiceUtils.getBoundServices(String.format("%s.%s.%s", "acl", "ingressacl", interfaceName),
                            serviceIndex, flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
            InstanceIdentifier<BoundServices> path =
                    AclServiceUtils.buildServiceId(interfaceName, serviceIndex, serviceMode);

            return Collections.singletonList(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> tx.put(
                            path, serviceInfo, CREATE_MISSING_PARENTS)));
        });
    }

    /**
     * Unbind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    protected void unbindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                ServiceIndex.getIndex(NwConstants.ACL_SERVICE_NAME, NwConstants.ACL_SERVICE_INDEX), serviceMode);

        LOG.debug("UnBinding ACL service for interface {}", interfaceName);
        jobCoordinator.enqueueJob(interfaceName, () -> Collections.singletonList(txRunner
                .callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> tx.delete(path))));
    }

    @Override
    protected void programAntiSpoofingRules(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove) {
        LOG.info("{} programAntiSpoofingRules for port {}, AAPs={}, action={}, addOrRemove={}", this.directionString,
                port.getInterfaceId(), allowedAddresses, action, addOrRemove);

        BigInteger dpid = port.getDpId();
        int lportTag = port.getLPortTag();
        if (action != Action.UPDATE) {
            programCommitterDropFlow(flowEntries, dpid, lportTag, addOrRemove);
            egressAclIcmpv6AllowedList(flowEntries, dpid, lportTag, addOrRemove);
        }
        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(allowedAddresses);
        if (!hasDuplicateMac(port.getAllowedAddressPairs(), filteredAAPs, action)) {
            programL2BroadcastAllowRule(flowEntries, port, filteredAAPs, addOrRemove);
            egressAclDhcpAllowClientTraffic(flowEntries, port, filteredAAPs, lportTag, addOrRemove);
            egressAclDhcpv6AllowClientTraffic(flowEntries, port, filteredAAPs, lportTag, addOrRemove);
        }
        programArpRule(flowEntries, dpid, filteredAAPs, lportTag, addOrRemove);
    }

    private void programCommitterDropFlow(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        BigInteger metaData = MetaDataUtil.getLportTagMetaData(lportTag)
                .or(MetaDataUtil.getAclDropMetaData(AclConstants.METADATA_DROP_FLAG));
        BigInteger metaDataMask =
                MetaDataUtil.METADATA_MASK_LPORT_TAG.or(MetaDataUtil.METADATA_MASK_ACL_DROP);
        matches.add(new MatchMetadata(metaData, metaDataMask));

        String flowName = "Egress_" + dpId + "_" + lportTag + "_Drop";
        addFlowEntryToList(flowEntries, dpId, getAclCommitterTable(), flowName,
                AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, 0, 0, AclServiceUtils.getDropFlowCookie(lportTag),
                matches, instructions, addOrRemove);
    }

    @Override
    protected void programRemoteAclTableFlow(List<FlowEntity> flowEntries, BigInteger dpId, Integer aclTag,
            AllowedAddressPairs aap, int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.addAll(AclServiceUtils.buildIpAndDstServiceMatch(aclTag, aap));

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
        String flowNameAdded = "Acl_Filter_Egress_" + aap.getIpAddress().stringValue() + "_" + aclTag;

        addFlowEntryToList(flowEntries, dpId, getAclRemoteAclTable(), flowNameAdded, AclConstants.ACL_DEFAULT_PRIORITY,
                0, 0, AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }

    @Override
    protected void programGotoClassifierTableRules(List<FlowEntity> flowEntries, BigInteger dpId,
            List<AllowedAddressPairs> aaps, int lportTag, int addOrRemove) {
        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(aaps);
        for (AllowedAddressPairs aap : filteredAAPs) {
            IpPrefixOrAddress attachIp = aap.getIpAddress();
            MacAddress mac = aap.getMacAddress();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
            matches.add(new MatchEthernetSource(mac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_SOURCE));

            List<InstructionInfo> gotoInstructions = new ArrayList<>();
            gotoInstructions.add(new InstructionGotoTable(getAclConntrackClassifierTable()));

            String flowName = "Egress_Fixed_Goto_Classifier_" + dpId + "_" + lportTag + "_" + mac.getValue() + "_"
                    + attachIp.stringValue();
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches, gotoInstructions,
                    addOrRemove);
        }
    }

    /**
     * Add rule to allow certain ICMPv6 traffic from VM ports.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclIcmpv6AllowedList(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

        for (Integer icmpv6Type: AclConstants.allowedIcmpv6NdList()) {
            List<MatchInfoBase> matches = AclServiceUtils.buildIcmpV6Matches(icmpv6Type, 0, lportTag, serviceMode);
            String flowName = "Egress_ICMPv6" + "_" + dpId + "_" + lportTag + "_" + icmpv6Type + "_Permit_";
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_IPV6_ALLOWED_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                    instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpAllowClientTraffic(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        for (AllowedAddressPairs aap : allowedAddresses) {
            if (!AclServiceUtils.isIPv4Address(aap)) {
                continue;
            }
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.addAll(AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_CLIENT_PORT_IPV4,
                AclConstants.DHCP_SERVER_PORT_IPV4, lportTag, serviceMode));
            matches.add(new MatchEthernetSource(aap.getMacAddress()));

            String flowName =
                    "Egress_DHCP_Client_v4" + dpId + "_" + lportTag + "_" + aap.getMacAddress().getValue() + "_Permit_";
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE,
                    matches, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpv6AllowClientTraffic(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        for (AllowedAddressPairs aap : allowedAddresses) {
            if (AclServiceUtils.isIPv4Address(aap)) {
                continue;
            }
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.addAll(AclServiceUtils.buildDhcpV6Matches(AclConstants.DHCP_CLIENT_PORT_IPV6,
                AclConstants.DHCP_SERVER_PORT_IPV6, lportTag, serviceMode));
            matches.add(new MatchEthernetSource(aap.getMacAddress()));

            String flowName = "Egress_DHCP_Client_v6" + "_" + dpId + "_" + lportTag + "_"
                    + aap.getMacAddress().getValue() + "_Permit_";
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE,
                    matches, instructions, addOrRemove);
        }
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programArpRule(List<FlowEntity> flowEntries, BigInteger dpId,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            if (!AclServiceUtils.isIPv4Address(allowedAddress)) {
                continue; // For IPv6 allowed addresses
            }

            IpPrefixOrAddress allowedAddressIp = allowedAddress.getIpAddress();
            MacAddress allowedAddressMac = allowedAddress.getMacAddress();
            List<MatchInfoBase> arpIpMatches = AclServiceUtils.buildArpIpMatches(allowedAddressIp);
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(MatchEthernetType.ARP);
            matches.add(new MatchArpSha(allowedAddressMac));
            matches.add(new MatchEthernetSource(allowedAddressMac));
            matches.addAll(arpIpMatches);
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
            LOG.debug("{} ARP Rule on DPID {}, lportTag {}",
                    addOrRemove == NwConstants.DEL_FLOW ? "Deleting" : "Adding", dpId, lportTag);
            String flowName = "Egress_ARP_" + dpId + "_" + lportTag + "_" + allowedAddress.getMacAddress().getValue()
                    + allowedAddressIp.stringValue();
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_ARP_TRAFFIC_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                    instructions, addOrRemove);
        }
    }

    @Override
    protected void programIcmpv6RARule(List<FlowEntity> flowEntries, AclInterface port, List<SubnetInfo> subnets,
            int addOrRemove) {
        // No action required on egress.
    }

    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    @Override
    protected void programBroadcastRules(List<FlowEntity> flowEntries, AclInterface port, Action action,
             int addOrRemove) {
        List<AllowedAddressPairs> allowedAddressPairs = port.getAllowedAddressPairs();
        List<AllowedAddressPairs> filteredAAPs = AclServiceUtils.excludeMulticastAAPs(allowedAddressPairs);
        if (!hasDuplicateMac(allowedAddressPairs, filteredAAPs, action)) {
            programL2BroadcastAllowRule(flowEntries, port, filteredAAPs, addOrRemove);
        }
    }

    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param subnetInfoList the port subnet info list
     * @param addOrRemove whether to delete or add flow
     */
    protected void programSubnetBroadcastRules(List<FlowEntity> flowEntries, AclInterface port,
            List<SubnetInfo> subnetInfoList, int addOrRemove) {
        // No action required on egress.
    }

    /**
     * Programs Non-IP broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param filteredAAPs the filtered AAPs list
     * @param addOrRemove whether to delete or add flow
     */
    private void programL2BroadcastAllowRule(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> filteredAAPs, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        Set<MacAddress> macs = filteredAAPs.stream().map(AllowedAddressPairs::getMacAddress)
                .collect(Collectors.toSet());
        for (MacAddress mac : macs) {
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchEthernetSource(mac));
            matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();

            String flowName = "Egress_L2Broadcast_" + dpId + "_" + lportTag + "_" + mac.getValue();
            addFlowEntryToList(flowEntries, dpId, getAclAntiSpoofingTable(), flowName,
                    AclConstants.PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE,
                    matches, instructions, addOrRemove);
        }
    }

    private boolean hasDuplicateMac(List<AllowedAddressPairs> allowedAddresses,
            List<AllowedAddressPairs> filteredAAPs, Action action) {
        // Do not proceed further if Port Up OR down event.
        if (action != Action.UPDATE) {
            return false;
        }
        // exclude multicastAAPs from PortBefore/PortAfter AAPs
        List<AllowedAddressPairs> excludeMulticastAAPs = AclServiceUtils.excludeMulticastAAPs(allowedAddresses);
        // GET Delta of ADDED/DELETED aaps with PortBefore/PortAfter-AAPs
        List<AllowedAddressPairs> filteredAllowedAddresses = excludeMulticastAAPs.stream().filter(
            aap -> !filteredAAPs.contains(aap)).collect(Collectors.toList());
        Set<MacAddress> macs = filteredAAPs.stream().map(aap -> aap.getMacAddress()).collect(Collectors.toSet());
        List<AllowedAddressPairs> aapWithDuplicateMac = filteredAllowedAddresses.stream()
            .filter(entry -> macs.contains(entry.getMacAddress())).collect(Collectors.toList());

        return !aapWithDuplicateMac.isEmpty();
    }

    @Override
    protected boolean isValidDirection(Class<? extends DirectionBase> direction) {
        return direction.equals(DirectionEgress.class);
    }

    private short getAclAntiSpoofingTable() {
        return NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE;
    }

    private short getAclConntrackClassifierTable() {
        return NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE;
    }

    @Override
    protected short getAclConntrackSenderTable() {
        return NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE;
    }

    @Override
    protected short getAclForExistingTrafficTable() {
        return NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE;
    }

    @Override
    protected short getAclFilterCumDispatcherTable() {
        return NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE;
    }

    @Override
    protected short getAclRuleBasedFilterTable() {
        return NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE;
    }

    @Override
    protected short getAclRemoteAclTable() {
        return NwConstants.INGRESS_REMOTE_ACL_TABLE;
    }

    @Override
    protected short getAclCommitterTable() {
        return NwConstants.INGRESS_ACL_COMMITTER_TABLE;
    }
}
