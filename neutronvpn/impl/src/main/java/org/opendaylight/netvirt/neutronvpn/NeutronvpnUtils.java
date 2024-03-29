/*
 * Copyright (c) 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.Dhcpv6Base;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpPortInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpn.instance.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpn.instance.RouterIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnUtils.class);
    private static final ImmutableBiMap<Class<? extends NetworkTypeBase>, Class<? extends SegmentTypeBase>>
        NETWORK_MAP =
        new ImmutableBiMap.Builder<Class<? extends NetworkTypeBase>, Class<? extends SegmentTypeBase>>()
            .put(NetworkTypeFlat.class, SegmentTypeFlat.class)
            .put(NetworkTypeGre.class, SegmentTypeGre.class)
            .put(NetworkTypeVlan.class, SegmentTypeVlan.class)
            .put(NetworkTypeVxlan.class, SegmentTypeVxlan.class)
            .build();

    private static final Set<Class<? extends NetworkTypeBase>> SUPPORTED_NETWORK_TYPES = new HashSet<>();

    static {
        SUPPORTED_NETWORK_TYPES.add(NetworkTypeFlat.class);
        SUPPORTED_NETWORK_TYPES.add(NetworkTypeVlan.class);
        SUPPORTED_NETWORK_TYPES.add(NetworkTypeVxlan.class);
        SUPPORTED_NETWORK_TYPES.add(NetworkTypeGre.class);
    }

    private final ConcurrentMap<Uuid, Network> networkMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Router> routerMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Port> portMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, Subnet> subnetMap = new ConcurrentHashMap<>();
    private final Map<IpAddress, Set<Uuid>> subnetGwIpMap = new ConcurrentHashMap<>();

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final JobCoordinator jobCoordinator;
    private final IPV6InternetDefaultRouteProgrammer ipV6InternetDefRt;
    private static final int JOB_MAX_RETRIES = 3;

    @Inject
    public NeutronvpnUtils(final DataBroker dataBroker, final IdManagerService idManager,
            final JobCoordinator jobCoordinator, final IPV6InternetDefaultRouteProgrammer ipV6InternetDefRt) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManager;
        this.jobCoordinator = jobCoordinator;
        this.ipV6InternetDefRt = ipV6InternetDefRt;
    }

    @Nullable
    protected Subnetmap getSubnetmap(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> id = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> sn = read(LogicalDatastoreType.CONFIGURATION, id);

        if (sn.isPresent()) {
            return sn.get();
        }
        LOG.error("getSubnetmap failed, subnet {} is not present", subnetId.getValue());
        return null;
    }

    @Nullable
    public VpnMap getVpnMap(Uuid id) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(id)).build();
        Optional<VpnMap> optionalVpnMap = read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            return optionalVpnMap.get();
        }
        LOG.error("getVpnMap failed, VPN {} not present", id.getValue());
        return null;
    }

    @Nullable
    protected Uuid getVpnForNetwork(Uuid network) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            for (VpnMap vpnMap : optionalVpnMaps.get().nonnullVpnMap()) {
                List<Uuid> netIds = vpnMap.getNetworkIds();
                if (netIds != null && netIds.contains(network)) {
                    return vpnMap.getVpnId();
                }
            }
        }
        LOG.debug("getVpnForNetwork: Failed for network {} as no VPN present in VPNMaps DS", network.getValue());
        return null;
    }

    @Nullable
    protected Uuid getVpnForSubnet(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapIdentifier = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetMap = read(LogicalDatastoreType.CONFIGURATION,
                subnetmapIdentifier);
        if (optionalSubnetMap.isPresent()) {
            return optionalSubnetMap.get().getVpnId();
        }
        LOG.error("getVpnForSubnet: Failed as subnetMap DS is absent for subnet {}", subnetId.getValue());
        return null;
    }

    @Nullable
    protected Uuid getNetworkForSubnet(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapIdentifier = buildSubnetMapIdentifier(subnetId);
        Optional<Subnetmap> optionalSubnetMap = read(LogicalDatastoreType.CONFIGURATION,
                subnetmapIdentifier);
        if (optionalSubnetMap.isPresent()) {
            return optionalSubnetMap.get().getNetworkId();
        }
        LOG.error("getNetworkForSubnet: Failed as subnetMap DS is absent for subnet {}", subnetId.getValue());
        return null;
    }

    // @param external vpn - true if external vpn being fetched, false for internal vpn
    @Nullable
    protected Uuid getVpnForRouter(@Nullable Uuid routerId, boolean externalVpn) {
        if (routerId == null) {
            return null;
        }

        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps = read(LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            for (VpnMap vpnMap : optionalVpnMaps.get().nonnullVpnMap()) {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap
                    .RouterIds> routerIdsList = vpnMap.getRouterIds();
                if (routerIdsList == null || routerIdsList.isEmpty()) {
                    continue;
                }
                // Skip router vpnId fetching from internet BGP-VPN
                if (vpnMap.getNetworkIds() != null && !vpnMap.getNetworkIds().isEmpty()) {
                    // We only need to check the first network; if it’s not an external network there’s no
                    // need to check the rest of the VPN’s network list
                    if (getIsExternal(getNeutronNetwork(vpnMap.getNetworkIds().iterator().next()))) {
                        continue;
                    }
                }
                // FIXME: NETVIRT-1503: this check can be replaced by a ReadOnlyTransaction.exists()
                if (routerIdsList.stream().anyMatch(routerIds -> routerId.equals(routerIds.getRouterId()))) {
                    if (externalVpn) {
                        if (!routerId.equals(vpnMap.getVpnId())) {
                            return vpnMap.getVpnId();
                        }
                    } else {
                        if (routerId.equals(vpnMap.getVpnId())) {
                            return vpnMap.getVpnId();
                        }
                    }
                }
            }
        }
        LOG.debug("getVpnForRouter: Failed for router {} as no VPN present in VPNMaps DS", routerId.getValue());
        return null;
    }

    @Nullable
    protected List<Uuid> getRouterIdListforVpn(Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            return NeutronUtils.getVpnMapRouterIdsListUuid(vpnMap.getRouterIds());
        }
        LOG.error("getRouterIdListforVpn: Failed as VPNMaps DS is absent for VPN {}", vpnId.getValue());
        return null;
    }

    @Nullable
    protected List<Uuid> getNetworksForVpn(Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap = read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            return vpnMap.getNetworkIds();
        }
        LOG.error("getNetworksforVpn: Failed as VPNMaps DS is absent for VPN {}", vpnId.getValue());
        return null;
    }

    protected List<Uuid> getSubnetsforVpn(Uuid vpnid) {
        List<Uuid> subnets = new ArrayList<>();
        // read subnetmaps
        InstanceIdentifier<Subnetmaps> subnetmapsid = InstanceIdentifier.builder(Subnetmaps.class).build();
        Optional<Subnetmaps> subnetmaps = read(LogicalDatastoreType.CONFIGURATION, subnetmapsid);
        if (subnetmaps.isPresent() && subnetmaps.get().getSubnetmap() != null) {
            List<Subnetmap> subnetMapList = subnetmaps.get().getSubnetmap();
            for (Subnetmap candidateSubnetMap : subnetMapList) {
                if (candidateSubnetMap.getVpnId() != null && candidateSubnetMap.getVpnId().equals(vpnid)) {
                    subnets.add(candidateSubnetMap.getId());
                }
            }
        }
        return subnets;
    }

    @Nullable
    protected String getNeutronPortNameFromVpnPortFixedIp(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return vpnPortipToPortData.get().getPortName();
        }
        LOG.error("getNeutronPortNameFromVpnPortFixedIp: Failed as vpnPortipToPortData DS is absent for VPN {} and"
                + " fixed IP {}", vpnName, fixedIp);
        return null;
    }

    @Nullable
    protected List<Uuid> getSubnetIdsFromNetworkId(Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = buildNetworkMapIdentifier(networkId);
        Optional<NetworkMap> optionalNetworkMap = read(LogicalDatastoreType.CONFIGURATION, id);
        if (optionalNetworkMap.isPresent()) {
            return optionalNetworkMap.get().getSubnetIdList();
        }
        LOG.error("getSubnetIdsFromNetworkId: Failed as networkmap DS is absent for network {}", networkId.getValue());
        return null;
    }

    protected Router getNeutronRouter(Uuid routerId) {
        Router router = routerMap.get(routerId);
        if (router != null) {
            return router;
        }
        InstanceIdentifier<Router> inst = InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router
                .class, new RouterKey(routerId));
        Optional<Router> rtr = read(LogicalDatastoreType.CONFIGURATION, inst);
        if (rtr.isPresent()) {
            router = rtr.get();
        }
        return router;
    }

    public InstanceIdentifier<Router> getNeutronRouterIid(Uuid routerId) {
        return InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router
                .class, new RouterKey(routerId));

    }

    protected Network getNeutronNetwork(Uuid networkId) {
        Network network = null;
        network = networkMap.get(networkId);
        if (network != null) {
            return network;
        }
        LOG.debug("getNeutronNetwork for {}", networkId.getValue());
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(Networks.class)
            .child(Network.class, new NetworkKey(networkId));
        Optional<Network> net = read(LogicalDatastoreType.CONFIGURATION, inst);
        if (net.isPresent()) {
            network = net.get();
        }
        return network;
    }

    protected Port getNeutronPort(Uuid portId) {
        Port prt = portMap.get(portId);
        if (prt != null) {
            return prt;
        }
        LOG.debug("getNeutronPort for {}", portId.getValue());
        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
                new PortKey(portId));
        Optional<Port> port = read(LogicalDatastoreType.CONFIGURATION, inst);
        if (port.isPresent()) {
            prt = port.get();
        }
        return prt;
    }

    /**
     * Returns port_security_enabled status with the port.
     *
     * @param port the port
     * @return port_security_enabled status
     */
    protected static boolean getPortSecurityEnabled(Port port) {
        String deviceOwner = port.getDeviceOwner();
        if (deviceOwner != null && deviceOwner.startsWith("network:")) {
            // port with device owner of network:xxx is created by
            // neutorn for its internal use. So security group doesn't apply.
            // router interface, dhcp port and floating ip.
            return false;
        }
        PortSecurityExtension portSecurity = port.augmentation(PortSecurityExtension.class);
        if (portSecurity != null) {
            return portSecurity.isPortSecurityEnabled();
        }
        return false;
    }

    /**
     * Gets security group UUIDs delta   .
     *
     * @param port1SecurityGroups the port 1 security groups
     * @param port2SecurityGroups the port 2 security groups
     * @return the security groups delta
     */
    @Nullable
    protected static List<Uuid> getSecurityGroupsDelta(@Nullable List<Uuid> port1SecurityGroups,
            @Nullable List<Uuid> port2SecurityGroups) {
        if (port1SecurityGroups == null) {
            return null;
        }

        if (port2SecurityGroups == null) {
            return port1SecurityGroups;
        }

        List<Uuid> list1 = new ArrayList<>(port1SecurityGroups);
        List<Uuid> list2 = new ArrayList<>(port2SecurityGroups);
        for (Iterator<Uuid> iterator = list1.iterator(); iterator.hasNext();) {
            Uuid securityGroup1 = iterator.next();
            for (Uuid securityGroup2 : list2) {
                if (securityGroup1.getValue().equals(securityGroup2.getValue())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return list1;
    }

    /**
     * Gets the fixed ips delta.
     *
     * @param port1FixedIps the port 1 fixed ips
     * @param port2FixedIps the port 2 fixed ips
     * @return the fixed ips delta
     */
    protected static List<FixedIps> getFixedIpsDelta(List<FixedIps> port1FixedIps, List<FixedIps> port2FixedIps) {
        if (port1FixedIps == null) {
            return null;
        }

        if (port2FixedIps == null) {
            return port1FixedIps;
        }

        List<FixedIps> list1 = new ArrayList<>(port1FixedIps);
        List<FixedIps> list2 = new ArrayList<>(port2FixedIps);
        for (Iterator<FixedIps> iterator = list1.iterator(); iterator.hasNext();) {
            FixedIps fixedIps1 = iterator.next();
            for (FixedIps fixedIps2 : list2) {
                if (fixedIps1.getIpAddress().equals(fixedIps2.getIpAddress())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return list1;
    }

    /**
     * Gets the allowed address pairs delta.
     *
     * @param port1AllowedAddressPairs the port 1 allowed address pairs
     * @param port2AllowedAddressPairs the port 2 allowed address pairs
     * @return the allowed address pairs delta
     */
    @Nullable
    protected static List<AllowedAddressPairs> getAllowedAddressPairsDelta(
        @Nullable List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> port1AllowedAddressPairs,
        @Nullable List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> port2AllowedAddressPairs) {
        if (port1AllowedAddressPairs == null) {
            return null;
        }

        if (port2AllowedAddressPairs == null) {
            return getAllowedAddressPairsForAclService(port1AllowedAddressPairs);
        }

        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> list1 =
                new ArrayList<>(port1AllowedAddressPairs);
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> list2 =
                new ArrayList<>(port2AllowedAddressPairs);
        for (Iterator<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> iterator =
             list1.iterator(); iterator.hasNext();) {
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs allowedAddressPair1 = iterator.next();
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                     .AllowedAddressPairs allowedAddressPair2 : list2) {
                if (allowedAddressPair1.key().equals(allowedAddressPair2.key())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return getAllowedAddressPairsForAclService(list1);
    }

    /**
     * Gets the acl allowed address pairs.
     *
     * @param macAddress the mac address
     * @param ipAddress the ip address
     * @return the acl allowed address pairs
     */
    protected static AllowedAddressPairs getAclAllowedAddressPairs(MacAddress macAddress,
            org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress ipAddress) {
        AllowedAddressPairsBuilder aclAllowedAdressPairBuilder = new AllowedAddressPairsBuilder();
        aclAllowedAdressPairBuilder.setMacAddress(macAddress);
        if (ipAddress != null && ipAddress.stringValue() != null) {
            if (ipAddress.getIpPrefix() != null) {
                aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpPrefix()));
            } else {
                aclAllowedAdressPairBuilder.setIpAddress(new IpPrefixOrAddress(ipAddress.getIpAddress()));
            }
        }
        return aclAllowedAdressPairBuilder.build();
    }

    /**
     * Gets the allowed address pairs for acl service.
     *
     * @param macAddress the mac address
     * @param fixedIps the fixed ips
     * @return the allowed address pairs for acl service
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForAclService(MacAddress macAddress,
            List<FixedIps> fixedIps) {
        List<AllowedAddressPairs> aclAllowedAddressPairs = new ArrayList<>();
        for (FixedIps fixedIp : fixedIps) {
            aclAllowedAddressPairs.add(getAclAllowedAddressPairs(macAddress,
                    org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddressBuilder
                    .getDefaultInstance(fixedIp.getIpAddress().stringValue())));
        }
        return aclAllowedAddressPairs;
    }

    /**
     * Gets the allowed address pairs for acl service.
     *
     * @param portAllowedAddressPairs the port allowed address pairs
     * @return the allowed address pairs for acl service
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForAclService(
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
            .AllowedAddressPairs> portAllowedAddressPairs) {
        List<AllowedAddressPairs> aclAllowedAddressPairs = new ArrayList<>();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs
                 portAllowedAddressPair : portAllowedAddressPairs) {
            aclAllowedAddressPairs.add(getAclAllowedAddressPairs(portAllowedAddressPair.getMacAddress(),
                portAllowedAddressPair.getIpAddress()));
        }
        return aclAllowedAddressPairs;
    }

    /**
     * Gets the IPv6 Link Local Address corresponding to the MAC Address.
     *
     * @param macAddress the mac address
     * @return the allowed address pairs for acl service which includes the MAC + IPv6LLA
     */
    protected static AllowedAddressPairs updateIPv6LinkLocalAddressForAclService(MacAddress macAddress) {
        IpAddress ipv6LinkLocalAddress = getIpv6LinkLocalAddressFromMac(macAddress);
        return getAclAllowedAddressPairs(macAddress,
                org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddressBuilder
                .getDefaultInstance(
                        ipv6LinkLocalAddress.stringValue()));
    }

    /**
     * Gets the updated security groups.
     *
     * @param aclInterfaceSecurityGroups the acl interface security groups
     * @param origSecurityGroups the orig security groups
     * @param newSecurityGroups the new security groups
     * @return the updated security groups
     */
    protected static List<Uuid> getUpdatedSecurityGroups(List<Uuid> aclInterfaceSecurityGroups,
            List<Uuid> origSecurityGroups, List<Uuid> newSecurityGroups) {
        List<Uuid> addedGroups = getSecurityGroupsDelta(newSecurityGroups, origSecurityGroups);
        List<Uuid> deletedGroups = getSecurityGroupsDelta(origSecurityGroups, newSecurityGroups);
        List<Uuid> updatedSecurityGroups =
                aclInterfaceSecurityGroups != null ? new ArrayList<>(aclInterfaceSecurityGroups) : new ArrayList<>();
        if (addedGroups != null) {
            updatedSecurityGroups.addAll(addedGroups);
        }
        if (deletedGroups != null) {
            updatedSecurityGroups.removeAll(deletedGroups);
        }
        return updatedSecurityGroups;
    }

    /**
     * Gets the allowed address pairs for fixed ips.
     *
     * @param aclInterfaceAllowedAddressPairs the acl interface allowed address pairs
     * @param portMacAddress the port mac address
     * @param origFixedIps the orig fixed ips
     * @param newFixedIps the new fixed ips
     * @return the allowed address pairs for fixed ips
     */
    protected static List<AllowedAddressPairs> getAllowedAddressPairsForFixedIps(
            List<AllowedAddressPairs> aclInterfaceAllowedAddressPairs, MacAddress portMacAddress,
            List<FixedIps> origFixedIps, List<FixedIps> newFixedIps) {
        List<FixedIps> addedFixedIps = getFixedIpsDelta(newFixedIps, origFixedIps);
        List<FixedIps> deletedFixedIps = getFixedIpsDelta(origFixedIps, newFixedIps);
        List<AllowedAddressPairs> updatedAllowedAddressPairs =
            aclInterfaceAllowedAddressPairs != null
                ? new ArrayList<>(aclInterfaceAllowedAddressPairs) : new ArrayList<>();
        if (deletedFixedIps != null) {
            updatedAllowedAddressPairs.removeAll(getAllowedAddressPairsForAclService(portMacAddress, deletedFixedIps));
        }
        if (addedFixedIps != null) {
            updatedAllowedAddressPairs.addAll(getAllowedAddressPairsForAclService(portMacAddress, addedFixedIps));
        }
        return updatedAllowedAddressPairs;
    }

    /**
     * Gets the updated allowed address pairs.
     *
     * @param aclInterfaceAllowedAddressPairs the acl interface allowed address pairs
     * @param origAllowedAddressPairs the orig allowed address pairs
     * @param newAllowedAddressPairs the new allowed address pairs
     * @return the updated allowed address pairs
     */
    protected static List<AllowedAddressPairs> getUpdatedAllowedAddressPairs(
            List<AllowedAddressPairs> aclInterfaceAllowedAddressPairs,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs> origAllowedAddressPairs,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes
                .AllowedAddressPairs> newAllowedAddressPairs) {
        List<AllowedAddressPairs> addedAllowedAddressPairs =
            getAllowedAddressPairsDelta(newAllowedAddressPairs,origAllowedAddressPairs);
        List<AllowedAddressPairs> deletedAllowedAddressPairs =
            getAllowedAddressPairsDelta(origAllowedAddressPairs, newAllowedAddressPairs);
        List<AllowedAddressPairs> updatedAllowedAddressPairs =
            aclInterfaceAllowedAddressPairs != null
                ? new ArrayList<>(aclInterfaceAllowedAddressPairs) : new ArrayList<>();
        if (addedAllowedAddressPairs != null) {
            updatedAllowedAddressPairs.addAll(addedAllowedAddressPairs);
        }
        if (deletedAllowedAddressPairs != null) {
            updatedAllowedAddressPairs.removeAll(deletedAllowedAddressPairs);
        }
        return updatedAllowedAddressPairs;
    }

    /**
     * Populate interface acl builder.
     *
     * @param interfaceAclBuilder the interface acl builder
     * @param port the port
     */
    protected void populateInterfaceAclBuilder(InterfaceAclBuilder interfaceAclBuilder, Port port) {
        // Handle security group enabled
        List<Uuid> securityGroups = port.getSecurityGroups();
        if (securityGroups != null) {
            interfaceAclBuilder.setSecurityGroups(securityGroups);
        }
        List<AllowedAddressPairs> aclAllowedAddressPairs = NeutronvpnUtils.getAllowedAddressPairsForAclService(
                port.getMacAddress(), port.getFixedIps());
        // Update the allowed address pair with the IPv6 LLA that is auto configured on the port.
        aclAllowedAddressPairs.add(NeutronvpnUtils.updateIPv6LinkLocalAddressForAclService(port.getMacAddress()));
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs>
            portAllowedAddressPairs = port.getAllowedAddressPairs();
        if (portAllowedAddressPairs != null) {
            aclAllowedAddressPairs.addAll(NeutronvpnUtils.getAllowedAddressPairsForAclService(portAllowedAddressPairs));
        }
        interfaceAclBuilder.setAllowedAddressPairs(aclAllowedAddressPairs);

        populateSubnetInfo(interfaceAclBuilder, port);
    }

    protected void populateSubnetInfo(InterfaceAclBuilder interfaceAclBuilder, Port port) {
        List<SubnetInfo> portSubnetInfo = getSubnetInfo(port);
        if (portSubnetInfo != null) {
            interfaceAclBuilder.setSubnetInfo(portSubnetInfo);
        }
    }

    @Nullable
    protected List<SubnetInfo> getSubnetInfo(Port port) {
        List<FixedIps> portFixedIps = port.getFixedIps();
        if (portFixedIps == null) {
            LOG.error("Failed to get Fixed IPs for the port {}", port.getName());
            return null;
        }
        List<SubnetInfo> subnetInfoList = new ArrayList<>();
        for (FixedIps portFixedIp : portFixedIps) {
            Uuid subnetId = portFixedIp.getSubnetId();
            Subnet subnet = getNeutronSubnet(subnetId);
            if (subnet != null) {
                Class<? extends IpVersionBase> ipVersion =
                        NeutronSecurityRuleConstants.IP_VERSION_MAP.get(subnet.getIpVersion());
                Class<? extends Dhcpv6Base> raMode = subnet.getIpv6RaMode() == null ? null
                        : NeutronSecurityRuleConstants.RA_MODE_MAP.get(subnet.getIpv6RaMode());
                SubnetInfo subnetInfo = new SubnetInfoBuilder().withKey(new SubnetInfoKey(subnetId))
                        .setIpVersion(ipVersion).setIpPrefix(new IpPrefixOrAddress(subnet.getCidr()))
                        .setIpv6RaMode(raMode).setGatewayIp(subnet.getGatewayIp()).build();
                subnetInfoList.add(subnetInfo);
            }
        }
        return subnetInfoList;
    }

    protected Subnet getNeutronSubnet(Uuid subnetId) {
        Subnet subnet = subnetMap.get(subnetId);
        if (subnet != null) {
            return subnet;
        }
        InstanceIdentifier<Subnet> inst = InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet
                .class, new SubnetKey(subnetId));
        Optional<Subnet> sn = read(LogicalDatastoreType.CONFIGURATION, inst);

        if (sn.isPresent()) {
            subnet = sn.get();
            addToSubnetCache(subnet);
        }
        return subnet;
    }

    protected List<Subnetmap> getNeutronRouterSubnetMapList(Uuid routerId) {
        List<Subnetmap> subnetMapList = new ArrayList<>();
        Optional<Subnetmaps> subnetMaps = read(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.builder(Subnetmaps.class).build());
        if (subnetMaps.isPresent() && subnetMaps.get().getSubnetmap() != null) {
            for (Subnetmap subnetmap : subnetMaps.get().getSubnetmap()) {
                if (routerId.equals(subnetmap.getRouterId())) {
                    subnetMapList.add(subnetmap);
                }
            }
        }
        LOG.debug("getNeutronRouterSubnetMapList returns {}", subnetMapList);
        return subnetMapList;
    }

    @NonNull
    protected List<Uuid> getNeutronRouterSubnetIds(Uuid routerId) {
        LOG.debug("getNeutronRouterSubnetIds for {}", routerId.getValue());
        List<Uuid> subnetIdList = new ArrayList<>();
        Optional<Subnetmaps> subnetMaps = read(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(Subnetmaps.class).build());
        if (subnetMaps.isPresent() && subnetMaps.get().getSubnetmap() != null) {
            for (Subnetmap subnetmap : subnetMaps.get().getSubnetmap()) {
                if (routerId.equals(subnetmap.getRouterId())) {
                    subnetIdList.add(subnetmap.getId());
                }
            }
        }
        LOG.debug("getNeutronRouterSubnetIds returns {}", subnetIdList);
        return subnetIdList;
    }

    // TODO Clean up the exception handling and the console output
    @SuppressWarnings({"checkstyle:IllegalCatch", "checkstyle:RegexpSinglelineJava"})
    @Nullable
    protected Short getIPPrefixFromPort(Port port) {
        try {
            Uuid subnetUUID = port.getFixedIps().get(0).getSubnetId();
            SubnetKey subnetkey = new SubnetKey(subnetUUID);
            InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class).child(Subnets
                    .class).child(Subnet.class, subnetkey);
            Optional<Subnet> subnet = read(LogicalDatastoreType.CONFIGURATION, subnetidentifier);
            if (subnet.isPresent()) {
                String cidr = subnet.get().getCidr().stringValue();
                // Extract the prefix length from cidr
                String[] parts = cidr.split("/");
                if (parts.length == 2) {
                    return Short.valueOf(parts[1]);
                } else {
                    LOG.trace("Could not retrieve prefix from subnet CIDR");
                }
            } else {
                LOG.trace("Unable to read on subnet datastore");
            }
        } catch (Exception e) {
            LOG.error("Failed to retrieve IP prefix from port for port {}", port.getUuid().getValue(), e);
        }
        LOG.error("Failed for port {}", port.getUuid().getValue());
        return null;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void createVpnPortFixedIpToPort(String vpnName, String fixedIp, String portName, String macAddress,
            boolean isSubnetIp, TypedWriteTransaction<Datastore.Configuration> writeConfigTxn) {
        InstanceIdentifier<VpnPortipToPort> id = NeutronvpnUtils.buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        VpnPortipToPortBuilder builder = new VpnPortipToPortBuilder()
            .withKey(new VpnPortipToPortKey(fixedIp, vpnName))
            .setVpnName(vpnName).setPortFixedip(fixedIp)
            .setPortName(portName).setMacAddress(macAddress).setSubnetIp(isSubnetIp);
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.put(id, builder.build());
            } else {
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
            }
            LOG.trace("Neutron port with fixedIp: {}, vpn {}, interface {}, mac {}, isSubnetIp {} added to "
                + "VpnPortipToPort DS", fixedIp, vpnName, portName, macAddress, isSubnetIp);
        } catch (Exception e) {
            LOG.error("Failure while creating VPNPortFixedIpToPort map for vpn {} - fixedIP {}", vpnName, fixedIp,
                    e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void removeVpnPortFixedIpToPort(String vpnName, String fixedIp,
                                              TypedWriteTransaction<Datastore.Configuration> writeConfigTxn) {
        InstanceIdentifier<VpnPortipToPort> id = NeutronvpnUtils.buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.delete(id);
            } else {
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            }
            LOG.trace("Neutron router port with fixedIp: {}, vpn {} removed from VpnPortipToPort DS", fixedIp,
                    vpnName);
        } catch (Exception e) {
            LOG.error("Failure while removing VPNPortFixedIpToPort map for vpn {} - fixedIP {}", vpnName, fixedIp,
                    e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void removeLearntVpnVipToPort(String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id = NeutronvpnUtils.buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        // FIXME: can we use 'id' as the lock name?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName + fixedIp);
        lock.lock();
        try {
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            LOG.trace("Neutron router port with fixedIp: {}, vpn {} removed from LearntVpnPortipToPort DS", fixedIp,
                    vpnName);
        } catch (Exception e) {
            LOG.error("Failure while removing LearntVpnPortFixedIpToPort map for vpn {} - fixedIP {}",
                vpnName, fixedIp, e);
        } finally {
            lock.unlock();
        }
    }

    public void addToNetworkCache(Network network) {
        networkMap.put(network.getUuid(), network);
    }

    public void removeFromNetworkCache(Network network) {
        networkMap.remove(network.getUuid());
    }

    public void addToRouterCache(Router router) {
        routerMap.put(router.getUuid(), router);
    }

    public void removeFromRouterCache(Router router) {
        routerMap.remove(router.getUuid());
    }

    public Collection<Router> getAllRouters() {
        return routerMap.values();
    }

    public void addToPortCache(Port port) {
        portMap.put(port.getUuid(), port);
    }

    public void removeFromPortCache(Port port) {
        portMap.remove(port.getUuid());
    }

    public void addToSubnetCache(Subnet subnet) {
        subnetMap.put(subnet.getUuid(), subnet);
        IpAddress gatewayIp = subnet.getGatewayIp();
        if (gatewayIp != null) {
            subnetGwIpMap.computeIfAbsent(gatewayIp, k -> Sets.newConcurrentHashSet()).add(subnet.getUuid());
        }
    }

    public void removeFromSubnetCache(Subnet subnet) {
        subnetMap.remove(subnet.getUuid());
        IpAddress gatewayIp = subnet.getGatewayIp();
        if (gatewayIp != null) {
            Set<Uuid> gwIps = subnetGwIpMap.get(gatewayIp);
            if (gwIps != null) {
                gwIps.remove(subnet.getUuid());
            }
        }
    }

    public static String getSegmentationIdFromNeutronNetwork(Network network) {
        String segmentationId = null;
        NetworkProviderExtension providerExtension = network.augmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            Class<? extends NetworkTypeBase> networkType = providerExtension.getNetworkType();
            segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(network, networkType);
        }

        return segmentationId;
    }

    public static Class<? extends SegmentTypeBase> getSegmentTypeFromNeutronNetwork(Network network) {
        NetworkProviderExtension providerExtension = network.augmentation(NetworkProviderExtension.class);
        return providerExtension != null ? NETWORK_MAP.get(providerExtension.getNetworkType()) : null;
    }

    public static String getPhysicalNetworkName(Network network) {
        NetworkProviderExtension providerExtension = network.augmentation(NetworkProviderExtension.class);
        return providerExtension != null ? providerExtension.getPhysicalNetwork() : null;
    }

    public Collection<Uuid> getSubnetIdsForGatewayIp(IpAddress ipAddress) {
        return subnetGwIpMap.getOrDefault(ipAddress, Collections.emptySet());
    }

    static InstanceIdentifier<VpnPortipToPort> buildVpnPortipToPortIdentifier(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id =
            InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class, new VpnPortipToPortKey(fixedIp, vpnName)).build();
        return id;
    }

    static InstanceIdentifier<LearntVpnVipToPort> buildLearntVpnVipToPortIdentifier(String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id =
            InstanceIdentifier.builder(LearntVpnVipToPortData.class)
                .child(LearntVpnVipToPort.class, new LearntVpnVipToPortKey(fixedIp, vpnName)).build();
        return id;
    }

    static Boolean getIsExternal(Network network) {
        return network.augmentation(NetworkL3Extension.class) != null
                && network.augmentation(NetworkL3Extension.class).isExternal();
    }

    static InstanceIdentifier<NetworkMap> buildNetworkMapIdentifier(Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap.class, new
                NetworkMapKey(networkId)).build();
        return id;
    }

    static InstanceIdentifier<VpnInterface> buildVpnInterfaceIdentifier(String ifName) {
        InstanceIdentifier<VpnInterface> id = InstanceIdentifier.builder(VpnInterfaces.class).child(VpnInterface
                .class, new VpnInterfaceKey(ifName)).build();
        return id;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetMapIdentifier(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new
                SubnetmapKey(subnetId)).build();
        return id;
    }

    static InstanceIdentifier<Interface> buildVlanInterfaceIdentifier(String interfaceName) {
        InstanceIdentifier<Interface> id = InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new
                InterfaceKey(interfaceName)).build();
        return id;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext
            .routers.Routers> buildExtRoutersIdentifier(Uuid routerId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers
                .Routers> id = InstanceIdentifier.builder(ExtRouters.class).child(org.opendaylight.yang.gen.v1.urn
                .opendaylight.netvirt.natservice.rev160111.ext.routers.Routers.class, new RoutersKey(routerId
                .getValue())).build();
        return id;
    }

    static InstanceIdentifier<FloatingIpIdToPortMapping> buildfloatingIpIdToPortMappingIdentifier(Uuid floatingIpId) {
        return InstanceIdentifier.builder(FloatingIpPortInfo.class).child(FloatingIpIdToPortMapping.class, new
                FloatingIpIdToPortMappingKey(floatingIpId)).build();
    }

    // TODO Remove this method entirely
    @SuppressWarnings("checkstyle:IllegalCatch")
    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker, datastoreType, path);
        } catch (ReadFailedException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    static ProviderTypes getProviderNetworkType(Network network) {
        if (network == null) {
            LOG.error("Error in getting provider network type since network is null");
            return null;
        }
        NetworkProviderExtension npe = network.augmentation(NetworkProviderExtension.class);
        if (npe != null) {
            Class<? extends NetworkTypeBase> networkTypeBase = npe.getNetworkType();
            if (networkTypeBase != null) {
                if (networkTypeBase.isAssignableFrom(NetworkTypeFlat.class)) {
                    return ProviderTypes.FLAT;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeVlan.class)) {
                    return ProviderTypes.VLAN;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeVxlan.class)) {
                    return ProviderTypes.VXLAN;
                } else if (networkTypeBase.isAssignableFrom(NetworkTypeGre.class)) {
                    return ProviderTypes.GRE;
                }
            }
        }
        LOG.error("Error in getting provider network type since network provider extension is null for network "
                + "{}", network.getUuid().getValue());
        return null;
    }

    static boolean isNetworkTypeSupported(Network network) {
        NetworkProviderExtension npe = network.augmentation(NetworkProviderExtension.class);
        return npe != null && npe.getNetworkType() != null && SUPPORTED_NETWORK_TYPES.contains(npe.getNetworkType());
    }

    static boolean isNetworkOfType(Network network, Class<? extends NetworkTypeBase> type) {
        NetworkProviderExtension npe = network.augmentation(NetworkProviderExtension.class);
        if (npe != null && npe.getNetworkType() != null) {
            return type.isAssignableFrom(npe.getNetworkType());
        }
        return false;
    }

    static boolean isFlatOrVlanNetwork(Network network) {
        return network != null
                && (isNetworkOfType(network, NetworkTypeVlan.class) || isNetworkOfType(network, NetworkTypeFlat.class));
    }

    static boolean isVlanOrVxlanNetwork(Class<? extends NetworkTypeBase> type) {
        return type.isAssignableFrom(NetworkTypeVxlan.class) || type.isAssignableFrom(NetworkTypeVlan.class);
    }

    /**
     * Get inter-VPN link state.
     *
     * @param vpnLinkName VPN link name
     * @return Optional of InterVpnLinkState
     */
    public Optional<InterVpnLinkState> getInterVpnLinkState(String vpnLinkName) {
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = InstanceIdentifier.builder(InterVpnLinkStates.class)
                .child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName)).build();
        return read(LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid);
    }

    /**
     * Returns an InterVpnLink by searching by one of its endpoint's IP.
     *
     * @param endpointIp IP to search for
     * @return a InterVpnLink
     */
    public Optional<InterVpnLink> getInterVpnLinkByEndpointIp(String endpointIp) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();
        Optional<InterVpnLinks> interVpnLinksOpData = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                interVpnLinksIid);
        if (interVpnLinksOpData.isPresent()) {
            for (InterVpnLink interVpnLink : interVpnLinksOpData.get().nonnullInterVpnLink()) {
                if (interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp)
                        || interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp)) {
                    return Optional.of(interVpnLink);
                }
            }
        }
        return Optional.absent();
    }

    protected void releaseRDId(String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManager.releaseId(idInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get Unique Id returned with errors for poolname {} and ID Key {}: {}",
                        poolName, idKey, rpcResult.getErrors());
            } else {
                LOG.info("ID {} for RD released successfully", idKey);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when trying to release ID for poolname {} and ID Key {}", poolName, idKey, e);
        }
    }

    protected static IpAddress getIpv6LinkLocalAddressFromMac(MacAddress mac) {
        byte[] octets = bytesFromHexString(mac.getValue());

        /* As per the RFC2373, steps involved to generate a LLA include
           1. Convert the 48 bit MAC address to 64 bit value by inserting 0xFFFE
              between OUI and NIC Specific part.
           2. Invert the Universal/Local flag in the OUI portion of the address.
           3. Use the prefix "FE80::/10" along with the above 64 bit Interface
              identifier to generate the IPv6 LLA. */

        StringBuilder interfaceID = new StringBuilder();
        short u8byte = (short) (octets[0] & 0xff);
        u8byte ^= 1 << 1;
        interfaceID.append(Integer.toHexString(0xFF & u8byte));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[1]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[2]));
        interfaceID.append("ff:fe");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[3]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[4]));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[5]), 2, "0"));

        Ipv6Address ipv6LLA = new Ipv6Address("fe80:0:0:0:" + interfaceID.toString());
        IpAddress ipAddress = new IpAddress(ipv6LLA);
        return ipAddress;
    }

    protected static byte[] bytesFromHexString(String values) {
        String target = "";
        if (values != null) {
            target = values;
        }
        String[] octets = target.split(":");

        byte[] ret = new byte[octets.length];
        for (int i = 0; i < octets.length; i++) {
            ret[i] = Integer.valueOf(octets[i], 16).byteValue();
        }
        return ret;
    }

    public List<String> getExistingRDs() {
        List<String> existingRDs = new ArrayList<>();
        InstanceIdentifier<VpnInstances> path = InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> vpnInstancesOptional = read(LogicalDatastoreType.CONFIGURATION, path);
        if (vpnInstancesOptional.isPresent() && vpnInstancesOptional.get().getVpnInstance() != null) {
            for (VpnInstance vpnInstance : vpnInstancesOptional.get().getVpnInstance()) {
                if (vpnInstance.getIpv4Family() == null) {
                    continue;
                }
                List<String> rds = vpnInstance.getIpv4Family().getRouteDistinguisher();
                if (rds != null) {
                    existingRDs.addAll(rds);
                }
            }
        }
        return existingRDs;
    }

    protected boolean doesVpnExist(Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap.class,
                new VpnMapKey(vpnId)).build();
        return read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier).isPresent();
    }

    protected Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
        .subnets.Subnets> getOptionalExternalSubnets(Uuid subnetId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
            .rev160111.external.subnets.Subnets> subnetsIdentifier =
                InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                        .rev160111.external.subnets.Subnets.class, new SubnetsKey(subnetId)).build();
        return read(LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
    }

    public static List<StaticMacEntries> buildStaticMacEntry(Port port) {
        PhysAddress physAddress = new PhysAddress(port.getMacAddress().getValue());
        List<FixedIps> fixedIps = port.getFixedIps();
        IpAddress ipAddress = null;
        if (isNotEmpty(fixedIps)) {
            ipAddress = port.getFixedIps().get(0).getIpAddress();
        }
        StaticMacEntriesBuilder staticMacEntriesBuilder = new StaticMacEntriesBuilder();
        List<StaticMacEntries> staticMacEntries = new ArrayList<>();
        if (ipAddress != null) {
            staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress).setIpPrefix(ipAddress).build());
        } else {
            staticMacEntries.add(staticMacEntriesBuilder.setMacAddress(physAddress).build());
        }
        return staticMacEntries;
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Method to get an ipVersionChosen as IPV4 and/or IPV6 or undefined from the subnetmaps of the router.
     * @param routerUuid the Uuid for which find out the IP version associated
     * @return an IpVersionChoice used by the router from its attached subnetmaps. IpVersionChoice.UNDEFINED if any
     */
    public IpVersionChoice getIpVersionChoicesFromRouterUuid(Uuid routerUuid) {
        IpVersionChoice rep = IpVersionChoice.UNDEFINED;
        if (routerUuid == null) {
            return rep;
        }
        List<Subnetmap> subnetmapList = getNeutronRouterSubnetMaps(routerUuid);
        if (subnetmapList.isEmpty()) {
            return rep;
        }
        for (Subnetmap sn : subnetmapList) {
            if (sn.getSubnetIp() != null) {
                IpVersionChoice ipVers = getIpVersionFromString(sn.getSubnetIp());
                if (rep.choice != ipVers.choice) {
                    rep = rep.addVersion(ipVers);
                }
                if (rep.choice == IpVersionChoice.IPV4AND6.choice) {
                    return rep;
                }
            }
        }
        return rep;
    }

    /**This method return the list of Subnetmap associated to the router or a empty list if any.
     * @param routerId the Uuid of router for which subnetmap is find out
     * @return a list of Subnetmap associated to the router. it could be empty if any
     */
    protected List<Subnetmap> getNeutronRouterSubnetMaps(Uuid routerId) {
        List<Subnetmap> subnetIdList = new ArrayList<>();
        Optional<Subnetmaps> subnetMaps = read(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(Subnetmaps.class).build());
        if (subnetMaps.isPresent() && subnetMaps.get().getSubnetmap() != null) {
            for (Subnetmap subnetmap : subnetMaps.get().getSubnetmap()) {
                if (routerId.equals(subnetmap.getRouterId())) {
                    subnetIdList.add(subnetmap);
                }
            }
        }
        return subnetIdList;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstanceKey(vpnName)).build();
    }

    /**
     * Retrieves the VPN Route Distinguisher searching by its Vpn instance name.
     * @param vpnName Name of the VPN
     *
     * @return the route-distinguisher of the VPN
     */
    @Nullable
    public String getVpnRd(String vpnName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
            .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstance::getVrfId).orElse(null);
    }

    /**Get IpVersionChoice from String IP like x.x.x.x or an representation IPv6.
     * @param ipAddress String of an representation IP address V4 or V6
     * @return the IpVersionChoice of the version or IpVersionChoice.UNDEFINED otherwise
     */
    public static IpVersionChoice getIpVersionFromString(String ipAddress) {
        IpVersionChoice ipchoice = IpVersionChoice.UNDEFINED;
        if (ipAddress.contains("/")) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf("/"));
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (address instanceof Inet4Address) {
                return IpVersionChoice.IPV4;
            } else if (address instanceof Inet6Address) {
                return IpVersionChoice.IPV6;
            }
        } catch (UnknownHostException | SecurityException e) {
            LOG.error("getIpVersionFromString: could not  find version for {}", ipAddress);
        }
        return ipchoice;
    }

    /**Get IpVersionChoice from Uuid Subnet.
     * @param sm Subnetmap structure
     * @return the IpVersionChoice of the version or IpVersionChoice.UNDEFINED otherwise
     */
    public static IpVersionChoice getIpVersionFromSubnet(Subnetmap sm) {
        if (sm != null && sm.getSubnetIp() != null) {
            return getIpVersionFromString(sm.getSubnetIp());
        }
        return IpVersionChoice.UNDEFINED;
    }

    @Nullable
    public VpnInstanceOpDataEntry getVpnInstanceOpDataEntryFromVpnId(String vpnName) {
        String primaryRd = getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.error("getVpnInstanceOpDataEntryFromVpnId: Vpn Instance {} "
                     + "Primary RD not found", vpnName);
            return null;
        }
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnOpDataIdentifier(primaryRd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = read(LogicalDatastoreType.OPERATIONAL, id);
        if (!vpnInstanceOpDataEntryOptional.isPresent()) {
            LOG.error("getVpnInstanceOpDataEntryFromVpnId: VpnInstance {} not found", primaryRd);
            return null;
        }
        return vpnInstanceOpDataEntryOptional.get();
    }

    protected InstanceIdentifier<VpnInstanceOpDataEntry> getVpnOpDataIdentifier(String primaryRd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(primaryRd)).build();
    }

    public boolean shouldVpnHandleIpVersionChoiceChange(IpVersionChoice ipVersion, Uuid routerId, boolean add) {
        int subnetCount = -1;
        if (ipVersion.isIpVersionChosen(IpVersionChoice.IPV4)) {
            subnetCount = getSubnetCountFromRouter(routerId, ipVersion);
        } else if (ipVersion.isIpVersionChosen(IpVersionChoice.IPV6)) {
            subnetCount = getSubnetCountFromRouter(routerId, ipVersion);
        } else {
            //Possible value of ipversion choice is either V4 or V6 only. Not accepted V4andV6 and Undefined
            return false;
        }
        /* ADD: Update vpnInstanceOpDataEntry with address family only on first IPv4/IPv6 subnet
         * for the VPN Instance.
         *
         * REMOVE: Update vpnInstanceOpDataEntry with address family only on last IPv4/IPv6 subnet
         * for the VPN Instance.
         */
        if (add && subnetCount == 1) {
            return true;
        } else if (!add && subnetCount == 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean shouldVpnHandleIpVersionChangeToRemove(Subnetmap sm, Uuid vpnId) {
        if (sm == null) {
            return false;
        }
        InstanceIdentifier<Subnetmaps> subnetMapsId = InstanceIdentifier.builder(Subnetmaps.class).build();
        Optional<Subnetmaps> allSubnetMaps = read(LogicalDatastoreType.CONFIGURATION, subnetMapsId);
        // calculate and store in list IpVersion for each subnetMap, belonging to current VpnInstance
        List<IpVersionChoice> snIpVersions = new ArrayList<>();
        for (Subnetmap snMap : allSubnetMaps.get().nonnullSubnetmap()) {
            if (snMap.getId().equals(sm.getId())) {
                continue;
            }
            if (snMap.getVpnId() != null && snMap.getVpnId().equals(vpnId)) {
                snIpVersions.add(getIpVersionFromString(snMap.getSubnetIp()));
            }
            if (snMap.getInternetVpnId() != null && snMap.getInternetVpnId().equals(vpnId)) {
                snIpVersions.add(getIpVersionFromString(snMap.getSubnetIp()));
            }
        }
        IpVersionChoice ipVersion = getIpVersionFromString(sm.getSubnetIp());
        if (!snIpVersions.contains(ipVersion)) {
            return true;
        }
        return false;
    }

    public int getSubnetCountFromRouter(Uuid routerId, IpVersionChoice ipVer) {
        List<Subnetmap> subnetMapList = getNeutronRouterSubnetMapList(routerId);
        int subnetCount = 0;
        for (Subnetmap subMap : subnetMapList) {
            IpVersionChoice ipVersion = getIpVersionFromString(subMap.getSubnetIp());
            if (ipVersion.isIpVersionChosen(ipVer)) {
                subnetCount++;
            }
            if (subnetCount > 1) {
                break;
            }
        }
        return subnetCount;
    }

    public void updateVpnInstanceWithIpFamily(String vpnName, IpVersionChoice ipVersion, boolean add) {
        jobCoordinator.enqueueJob("VPN-" + vpnName, () -> {
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpDataEntryFromVpnId(vpnName);
            if (vpnInstanceOpDataEntry == null) {
                return Collections.emptyList();
            }
            if (vpnInstanceOpDataEntry.getType() == VpnInstanceOpDataEntry.Type.L2) {
                LOG.debug("updateVpnInstanceWithIpFamily: Update VpnInstance {} with ipFamily {}."
                        + "VpnInstanceOpDataEntry is L2 instance. Do nothing.", vpnName, ipVersion);
                return Collections.emptyList();
            }
            if (ipVersion == IpVersionChoice.UNDEFINED) {
                LOG.debug("updateVpnInstanceWithIpFamily: Update VpnInstance {} with Undefined address family"
                        + "is not allowed. Do nothing", vpnName);
                return Collections.emptyList();
            }
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder(vpnInstanceOpDataEntry);
            boolean ipConfigured = add;
            if (ipVersion.isIpVersionChosen(IpVersionChoice.IPV4AND6)) {
                builder.setIpv4Configured(ipConfigured);
                builder.setIpv6Configured(ipConfigured);
            } else if (ipVersion.isIpVersionChosen(IpVersionChoice.IPV4)) {
                builder.setIpv4Configured(ipConfigured);
            } else if (ipVersion.isIpVersionChosen(IpVersionChoice.IPV6)) {
                builder.setIpv6Configured(ipConfigured);
            }
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    OPERATIONAL, tx -> {
                    InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier
                            .builder(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class,
                                        new VpnInstanceOpDataEntryKey(vpnInstanceOpDataEntry.getVrfId())).build();
                    tx.merge(id, builder.build(), false);
                    LOG.info("updateVpnInstanceWithIpFamily: Successfully {} {} to Vpn {}",
                            add == true ? "added" : "removed", ipVersion, vpnName);
                }));
        });
    }

    /**
     * Get the vpnInstance from its Uuid.
     *
     * @param broker to get informations from ds
     * @param vpnId the Uuid of the VPN
     * @return the VpnInstance or null if unfindable
     */
    @Nullable
    public VpnInstance getVpnInstance(DataBroker broker, Uuid vpnId) {
        if (broker == null || vpnId == null) {
            return null;
        }
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class,
                new VpnInstanceKey(vpnId.getValue())).build();
        Optional<VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        return vpnInstance.isPresent() ? vpnInstance.get() : null;
    }

    /**
     *Get the Uuid of external network of the router (remember you that one router have only one external network).
     * @param routerId the Uuid of the router which you try to reach the external network
     * @return Uuid of externalNetwork or null if is not exist
     */
    protected Uuid getExternalNetworkUuidAttachedFromRouterUuid(@NonNull Uuid routerId) {
        LOG.debug("getExternalNetworkUuidAttachedFromRouterUuid for {}", routerId.getValue());
        Uuid externalNetworkUuid = null;
        Router router = getNeutronRouter(routerId);
        if (router != null && router.getExternalGatewayInfo() != null) {
            externalNetworkUuid = router.getExternalGatewayInfo().getExternalNetworkId();
        }
        return externalNetworkUuid;
    }

    public Uuid getInternetvpnUuidBoundToRouterId(@NonNull Uuid routerId) {
        Uuid netId = getExternalNetworkUuidAttachedFromRouterUuid(routerId);
        if (netId == null) {
            return netId;
        }
        return getVpnForNetwork(netId);
    }

    /**
     * This method get Uuid of internet vpn if existing one bound to the same router of the subnetUuid arg.
     * Explanation: If the subnet (of arg subnetUuid) have a router bound and this router have an
     * externalVpn (vpn on externalProvider network) then <b>its Uuid</b> will be returned.
     * @param subnetUuid Uuid of subnet where you are finding a link to an external network
     * @return Uuid of externalVpn or null if it is not found
     */
    @Nullable
    public Uuid getInternetvpnUuidBoundToSubnetRouter(@NonNull Uuid subnetUuid) {
        Subnetmap subnetmap = getSubnetmap(subnetUuid);
        Uuid routerUuid = subnetmap.getRouterId();
        LOG.debug("getInternetvpnUuidBoundToSubnetRouter for subnetUuid {}", subnetUuid.getValue());
        if (routerUuid == null) {
            return null;
        }
        Uuid externalNetworkUuid = getExternalNetworkUuidAttachedFromRouterUuid(routerUuid);
        return externalNetworkUuid != null ? getVpnForNetwork(externalNetworkUuid) : null;
    }

    /**
     * Get a list of Private Subnetmap Ids from router to export then its prefixes in Internet VPN.
     * @param extNet Provider Network, which has a port attached as external network gateway to router
     * @return a list of Private Subnetmap Ids of the router with external network gateway
     */
    public @NonNull List<Uuid> getPrivateSubnetsToExport(@NonNull Network extNet, Uuid internetVpnId) {
        List<Uuid> subList = new ArrayList<>();
        List<Uuid> rtrList = new ArrayList<>();
        if (internetVpnId != null) {
            rtrList.addAll(getRouterIdListforVpn(internetVpnId));
        } else {
            Uuid extNwVpnId = getVpnForNetwork(extNet.getUuid());
            rtrList.addAll(getRouterIdListforVpn(extNwVpnId));
        }
        if (rtrList.isEmpty()) {
            return subList;
        }
        for (Uuid rtrId: rtrList) {
            Router router = getNeutronRouter(rtrId);
            ExternalGatewayInfo info = router.getExternalGatewayInfo();
            if (info == null) {
                LOG.error("getPrivateSubnetsToExport: can not get info about external gateway for router {}",
                          router.getUuid().getValue());
                continue;
            }
            // check that router really has given provider network as its external gateway port
            if (!extNet.getUuid().equals(info.getExternalNetworkId())) {
                LOG.error("getPrivateSubnetsToExport: router {} is not attached to given provider network {}",
                          router.getUuid().getValue(), extNet.getUuid().getValue());
                continue;
            }
            subList.addAll(getNeutronRouterSubnetIds(rtrId));
        }
        return subList;
    }

    public void updateVpnInstanceWithFallback(Uuid routerId, Uuid vpnName, boolean add) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpDataEntryFromVpnId(vpnName.getValue());
        if (vpnInstanceOpDataEntry == null) {
            LOG.error("updateVpnInstanceWithFallback: vpnInstanceOpDataEntry not found for vpn {}", vpnName);
            return;
        }
        Long internetBgpVpnId = vpnInstanceOpDataEntry.getVpnId();
        List<Uuid> routerIds = new ArrayList<>();
        //Handle router specific V6 internet fallback flow else handle all V6 external routers
        if (routerId != null) {
            routerIds.add(routerId);
        } else {
            //This block will execute for ext-nw to Internet VPN association/disassociation event.
            routerIds = getRouterIdListforVpn(vpnName);
        }
        if (routerIds == null || routerIds.isEmpty()) {
            LOG.error("updateVpnInstanceWithFallback: router not found for vpn {}", vpnName);
            return;
        }
        for (Uuid rtrId: routerIds) {
            if (rtrId == null) {
                continue;
            }
            List<BigInteger> dpnIds = getDpnsForRouter(rtrId.getValue());
            if (dpnIds.isEmpty()) {
                continue;
            }
            VpnInstanceOpDataEntry vpnOpDataEntry = getVpnInstanceOpDataEntryFromVpnId(rtrId.getValue());
            Long routerIdAsLong = vpnOpDataEntry.getVpnId();
            long vpnId;
            Uuid rtrVpnId = getVpnForRouter(rtrId, true);
            if (rtrVpnId == null) {
                //If external BGP-VPN is not associated with router then routerId is same as routerVpnId
                vpnId = routerIdAsLong;
            } else {
                vpnId = getVpnId(rtrVpnId.getValue());
            }
            for (BigInteger dpnId : dpnIds) {
                if (add) {
                    LoggingFutures.addErrorLogging(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION,
                            tx -> ipV6InternetDefRt.installDefaultRoute(tx, dpnId, rtrId.getValue(), internetBgpVpnId,
                                vpnId)), LOG, "Error adding default route");
                } else {
                    LoggingFutures.addErrorLogging(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION,
                            tx -> ipV6InternetDefRt.removeDefaultRoute(tx, dpnId, rtrId.getValue(), internetBgpVpnId,
                                vpnId)), LOG,
                        "Error removing default route");
                }
            }
        }
    }

    public void updateVpnInstanceOpWithType(VpnInstanceOpDataEntry.BgpvpnType choice, @NonNull Uuid vpn) {
        String primaryRd = getVpnRd(vpn.getValue());
        if (primaryRd == null) {
            LOG.debug("updateVpnInstanceOpWithType: Update BgpvpnType {} for {}."
                    + "Primary RD not found", choice, vpn.getValue());
            return;
        }
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
              .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(primaryRd)).build();

        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
            read(LogicalDatastoreType.OPERATIONAL, id);
        if (!vpnInstanceOpDataEntryOptional.isPresent()) {
            LOG.debug("updateVpnInstanceOpWithType: Update BgpvpnType {} for {}."
                    + "VpnInstanceOpDataEntry not found", choice, vpn.getValue());
            return;
        }
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnInstanceOpDataEntryOptional.get();
        if (vpnInstanceOpDataEntry.getBgpvpnType().equals(choice)) {
            LOG.debug("updateVpnInstanceOpWithType: Update BgpvpnType {} for {}."
                    + "VpnInstanceOpDataEntry already set", choice, vpn.getValue());
            return;
        }
        VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder(vpnInstanceOpDataEntry);
        builder.setBgpvpnType(choice);
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
            tx.merge(id, builder.build(), false);
            LOG.debug("updateVpnInstanceOpWithType: sent merge to operDS BgpvpnType {} for {}", choice, vpn.getValue());
        }), LOG, "Error updating VPN instance op {} with type {}", vpn, choice);
    }

    public static RouterIds getvpnInstanceRouterIds(Uuid routerId) {
        return new RouterIdsBuilder().setRouterId(routerId).build();
    }

    public static List<RouterIds> getVpnInstanceRouterIdsList(List<Uuid> routerIds) {
        List<RouterIds> listRouterIds = new ArrayList<>();
        for (Uuid routerId : routerIds) {
            final RouterIds routerIdInstance = getvpnInstanceRouterIds(routerId);
            listRouterIds.add(routerIdInstance);
        }
        return listRouterIds;
    }

    @NonNull
    public List<BigInteger> getDpnsForRouter(String routerUuid) {
        InstanceIdentifier id = InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerUuid)).build();
        Optional<RouterDpnList> routerDpnListData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        List<BigInteger> dpns = new ArrayList<>();
        if (routerDpnListData.isPresent()) {
            for (DpnVpninterfacesList dpnVpnInterface : routerDpnListData.get().nonnullDpnVpninterfacesList()) {
                dpns.add(dpnVpnInterface.getDpnId());
            }
        }
        return dpns;
    }

    @Nullable
    List<Subnetmap> getSubnetmapListFromNetworkId(Uuid networkId) {
        List<Uuid> subnetIdList = getSubnetIdsFromNetworkId(networkId);
        if (subnetIdList != null) {
            List<Subnetmap> subnetmapList = new ArrayList<>();
            for (Uuid subnetId : subnetIdList) {
                Subnetmap subnetmap = getSubnetmap(subnetId);
                if (subnetmap != null) {
                    subnetmapList.add(subnetmap);
                } else {
                    LOG.error("getSubnetmapListFromNetworkId: subnetmap is null for subnet {} belonging to network {}",
                            subnetId.getValue(), networkId.getValue());
                }
            }
            return subnetmapList;
        }
        LOG.error("getSubnetmapListFromNetworkId: Failed as subnetIdList is null for network {}",
                networkId.getValue());
        return null;
    }

    @Nullable
    public long getVpnId(String vpnName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstance::getVpnId).orElse(null);
    }

    protected boolean isV6SubnetPartOfRouter(Uuid routerId) {
        List<Subnetmap> subnetList = getNeutronRouterSubnetMapList(routerId);
        for (Subnetmap sm : subnetList) {
            if (sm == null) {
                continue;
            }
            IpVersionChoice ipVers = getIpVersionFromString(sm.getSubnetIp());
            //skip further subnet processing once found first V6 subnet for the router
            if (ipVers.isIpVersionChosen(IpVersionChoice.IPV6)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends DataObject> void asyncReadAndExecute(final LogicalDatastoreType datastoreType,
                                                           final InstanceIdentifier<T> iid, final String jobKey,
                                                           final Function<Optional<T>, Void> function) {
        jobCoordinator.enqueueJob(jobKey, () -> {
            SettableFuture<Optional<T>> settableFuture = SettableFuture.create();
            List futures = Collections.singletonList(settableFuture);
            try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
                Futures.addCallback(tx.read(datastoreType, iid),
                        new SettableFutureCallback<Optional<T>>(settableFuture) {
                            @Override
                            public void onSuccess(Optional<T> data) {
                                function.apply(data);
                                super.onSuccess(data);
                            }
                        }, MoreExecutors.directExecutor());

                return futures;
            }
        }, JOB_MAX_RETRIES);
    }

    private class SettableFutureCallback<T> implements FutureCallback<T> {

        private final SettableFuture<T> settableFuture;

        SettableFutureCallback(SettableFuture<T> settableFuture) {
            this.settableFuture = settableFuture;
        }

        @Override
        public void onSuccess(T objT) {
            settableFuture.set(objT);
        }

        @Override
        public void onFailure(Throwable throwable) {
            settableFuture.setException(throwable);
        }
    }
}

