/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.ipv6util.api.Icmpv6Type;
import org.opendaylight.genius.ipv6util.api.Ipv6Constants;
import org.opendaylight.genius.ipv6util.api.Ipv6Constants.Ipv6RouterAdvertisementType;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.RouterAdvertisementPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.RouterAdvertisementPacketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.RouterSolicitationPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.router.advertisement.packet.PrefixList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.router.advertisement.packet.PrefixListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Ipv6RouterAdvt {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6RouterAdvt.class);
    private final PacketProcessingService packetService;
    private final IfMgr ifMgr;

    public Ipv6RouterAdvt(PacketProcessingService packetService, IfMgr ifMgr) {
        this.packetService = packetService;
        this.ifMgr = ifMgr;
    }

    public boolean transmitRtrAdvertisement(Ipv6RouterAdvertisementType raType, VirtualPort routerPort,
                                            long elanTag, @Nullable RouterSolicitationPacket rsPdu,
                                            BigInteger dpnId, Uuid port) {
        RouterAdvertisementPacketBuilder raPacket = new RouterAdvertisementPacketBuilder();
        updateRAResponse(raType, rsPdu, raPacket, routerPort);
        // Serialize the response packet
        byte[] txPayload = fillRouterAdvertisementPacket(raPacket.build());
        TransmitPacketInput input = null;
        /* Send solicited router advertisement to requested VM port only.
         * Send periodic unsolicited router advertisement to ELAN broadcast group.
         */
        if (raType == Ipv6RouterAdvertisementType.SOLICITED_ADVERTISEMENT) {
            List<Action> actions = ifMgr.getEgressAction(port.getValue());
            if (actions == null || actions.isEmpty()) {
                LOG.error("Unable to send solicited router advertisement packet out. Since Egress "
                        + "action is empty for interface {}. ", port.getValue());
                return false;
            }
            input = MDSALUtil.getPacketOut(actions, txPayload, dpnId);
            LOG.debug("Transmitting the Router Advt packet out to port {}", port.getValue());
        } else {
            /* Here we handle UNSOLICITED_ADVERTISEMENT, CEASE_ADVERTISEMENT */
            long elanGroupId = Ipv6ServiceUtils.getRemoteBCGroup(elanTag);
            List<ActionInfo> lstActionInfo = new ArrayList<>();
            lstActionInfo.add(new ActionGroup(elanGroupId));
            input = MDSALUtil.getPacketOutDefault(lstActionInfo, txPayload, dpnId);
            LOG.debug("Transmitting the Router Advt packet out to ELAN Group ID {}", elanGroupId);
        }
        JdkFutures.addErrorLogging(packetService.transmitPacket(input), LOG, "transmitPacket");
        return true;
    }

    private static void updateRAResponse(Ipv6RouterAdvertisementType raType, @Nullable RouterSolicitationPacket pdu,
                                         RouterAdvertisementPacketBuilder raPacket,
                                         VirtualPort routerPort) {
        short icmpv6RaFlags = 0;
        String gatewayMac = null;
        IpAddress gatewayIp;
        List<Ipv6Prefix> autoConfigPrefixList = new ArrayList<>();
        List<Ipv6Prefix> statefulConfigPrefixList = new ArrayList<>();

        for (VirtualSubnet subnet : routerPort.getSubnets()) {
            gatewayIp = subnet.getGatewayIp();
            // Skip if its a v4 subnet.
            if (gatewayIp.getIpv4Address() != null) {
                continue;
            }

            if (!subnet.getIpv6RAMode().isEmpty()) {
                if (Ipv6ServiceConstants.IPV6_AUTO_ADDRESS_SUBNETS.contains(subnet.getIpv6RAMode())) {
                    autoConfigPrefixList.add(requireNonNull(subnet.getSubnetCidr().getIpv6Prefix()));
                }

                if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL)) {
                    statefulConfigPrefixList.add(requireNonNull(subnet.getSubnetCidr().getIpv6Prefix()));
                }
            }

            if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS)) {
                icmpv6RaFlags = (short) (icmpv6RaFlags | 1 << 6); // Other Configuration.
            } else if (subnet.getIpv6RAMode().equalsIgnoreCase(Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL)) {
                icmpv6RaFlags = (short) (icmpv6RaFlags | 1 << 7); // Managed Address Conf.
            }
        }

        gatewayMac = routerPort.getMacAddress();

        MacAddress sourceMac = MacAddress.getDefaultInstance(gatewayMac);
        raPacket.setSourceMac(sourceMac);
        if (raType == Ipv6RouterAdvertisementType.SOLICITED_ADVERTISEMENT) {
            if (pdu == null) {
                throw new IllegalArgumentException("Null pdu for solicited advertisement");
            }
            raPacket.setDestinationMac(pdu.getSourceMac());
            raPacket.setDestinationIpv6(pdu.getSourceIpv6());
            raPacket.setFlowLabel(pdu.getFlowLabel());
        } else {
            raPacket.setDestinationMac(new MacAddress(Ipv6Constants.ALL_NODES_MCAST_MAC));
            raPacket.setDestinationIpv6(Ipv6ServiceUtils.ALL_NODES_MCAST_ADDR);
            raPacket.setFlowLabel(Ipv6ServiceConstants.DEF_FLOWLABEL);
        }

        raPacket.setEthertype(NwConstants.ETHTYPE_IPV6);

        raPacket.setVersion(Ipv6Constants.IPV6_VERSION);
        int prefixListLength = autoConfigPrefixList.size() + statefulConfigPrefixList.size();
        int mtuOptionLength = routerPort.getMtu() == 0 ? 0 : Ipv6Constants.ICMPV6_OPTION_MTU_LENGTH;
        raPacket.setIpv6Length(Ipv6Constants.ICMPV6_RA_LENGTH_WO_OPTIONS
                + mtuOptionLength + Ipv6Constants.ICMPV6_OPTION_SOURCE_LLA_LENGTH
                + prefixListLength * Ipv6Constants.ICMPV6_OPTION_PREFIX_LENGTH);
        raPacket.setNextHeader(IPProtocols.IPV6ICMP.shortValue());
        raPacket.setHopLimit(Ipv6Constants.ICMP_V6_MAX_HOP_LIMIT);
        raPacket.setSourceIpv6(Ipv6Util.getIpv6LinkLocalAddressFromMac(sourceMac));

        raPacket.setIcmp6Type(Icmpv6Type.ROUTER_ADVETISEMENT.getValue());
        raPacket.setIcmp6Code((short)0);
        raPacket.setIcmp6Chksum(0);

        raPacket.setCurHopLimit((short) Ipv6Constants.IPV6_DEFAULT_HOP_LIMIT);
        raPacket.setFlags(icmpv6RaFlags);

        if (raType == Ipv6RouterAdvertisementType.CEASE_ADVERTISEMENT) {
            raPacket.setRouterLifetime(0);
        } else {
            raPacket.setRouterLifetime(Ipv6Constants.IPV6_ROUTER_LIFETIME);
        }
        raPacket.setReachableTime((long) Ipv6Constants.IPV6_RA_REACHABLE_TIME);
        raPacket.setRetransTime((long) 0);

        raPacket.setOptionSourceAddr(Ipv6Constants.ICMP_V6_OPTION_SOURCE_LLA);
        raPacket.setSourceAddrLength((short)1);
        raPacket.setSourceLlAddress(MacAddress.getDefaultInstance(gatewayMac));

        if (mtuOptionLength != 0) {
            raPacket.setOptionMtu(Ipv6Constants.ICMP_V6_OPTION_MTU);
            raPacket.setOptionMtuLength((short)1);
            raPacket.setMtu((long) routerPort.getMtu());
        }

        List<PrefixList> prefixList = new ArrayList<>();
        PrefixListBuilder prefix = new PrefixListBuilder();
        prefix.setOptionType(Ipv6Constants.ICMP_V6_OPTION_PREFIX_INFO);
        prefix.setOptionLength((short)4);
        // Note: EUI-64 auto-configuration requires 64 bits.
        prefix.setPrefixLength((short)64);
        prefix.setValidLifetime((long) Ipv6Constants.IPV6_RA_VALID_LIFETIME);
        prefix.setPreferredLifetime((long) Ipv6Constants.IPV6_RA_PREFERRED_LIFETIME);
        prefix.setReserved((long) 0);

        short autoConfPrefixFlags = 0;
        autoConfPrefixFlags = (short) (autoConfPrefixFlags | 1 << 7); // On-link flag
        autoConfPrefixFlags = (short) (autoConfPrefixFlags | 1 << 6); // Autonomous address-configuration flag.
        for (Ipv6Prefix v6Prefix : autoConfigPrefixList) {
            prefix.setFlags(autoConfPrefixFlags);
            prefix.setPrefix(v6Prefix);
            prefixList.add(prefix.build());
        }

        short statefulPrefixFlags = 0;
        statefulPrefixFlags = (short) (statefulPrefixFlags | 1 << 7); // On-link flag
        for (Ipv6Prefix v6Prefix : statefulConfigPrefixList) {
            prefix.setFlags(statefulPrefixFlags);
            prefix.setPrefix(v6Prefix);
            prefixList.add(prefix.build());
        }

        raPacket.setPrefixList(prefixList);
    }

    private byte[] fillRouterAdvertisementPacket(RouterAdvertisementPacket pdu) {
        ByteBuffer buf = ByteBuffer.allocate(Ipv6Constants.ICMPV6_OFFSET + pdu.getIpv6Length());

        buf.put(Ipv6Util.convertEthernetHeaderToByte(pdu), 0, 14);
        buf.put(Ipv6Util.convertIpv6HeaderToByte(pdu), 0, 40);
        buf.put(icmp6RAPayloadtoByte(pdu), 0, pdu.getIpv6Length());
        int checksum = Ipv6Util.calculateIcmpv6Checksum(buf.array(), pdu);
        buf.putShort(Ipv6Constants.ICMPV6_OFFSET + 2, (short)checksum);
        return buf.array();
    }

    private static byte[] icmp6RAPayloadtoByte(RouterAdvertisementPacket pdu) {
        byte[] data = new byte[pdu.getIpv6Length()];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte)pdu.getIcmp6Type().shortValue());
        buf.put((byte)pdu.getIcmp6Code().shortValue());
        buf.putShort((short)pdu.getIcmp6Chksum().intValue());
        buf.put((byte)pdu.getCurHopLimit().shortValue());
        buf.put((byte)pdu.getFlags().shortValue());
        buf.putShort((short)pdu.getRouterLifetime().intValue());
        buf.putInt((int)pdu.getReachableTime().longValue());
        buf.putInt((int)pdu.getRetransTime().longValue());
        buf.put((byte)pdu.getOptionSourceAddr().shortValue());
        buf.put((byte)pdu.getSourceAddrLength().shortValue());
        buf.put(Ipv6Util.bytesFromHexString(pdu.getSourceLlAddress().getValue()));
        if (pdu.getOptionMtu() != null) {
            buf.put((byte)pdu.getOptionMtu().shortValue());
            buf.put((byte)pdu.getOptionMtuLength().shortValue());
            buf.putShort((byte)0); // Reserved field
            buf.putInt((int)pdu.getMtu().longValue());
        }

        for (PrefixList prefix : pdu.nonnullPrefixList()) {
            buf.put((byte)prefix.getOptionType().shortValue());
            buf.put((byte)prefix.getOptionLength().shortValue());
            buf.put((byte)prefix.getPrefixLength().shortValue());
            buf.put((byte)prefix.getFlags().shortValue());
            buf.putInt((int)prefix.getValidLifetime().longValue());
            buf.putInt((int)prefix.getPreferredLifetime().longValue());
            buf.putInt((int)prefix.getReserved().longValue());
            buf.put(IetfInetUtil.INSTANCE.ipv6PrefixToBytes(new Ipv6Prefix(prefix.getPrefix())),0,16);
        }
        return data;
    }
}
