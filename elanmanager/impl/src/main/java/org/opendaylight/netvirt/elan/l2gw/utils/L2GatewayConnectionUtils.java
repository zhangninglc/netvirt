/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.utils;

import static java.util.Collections.emptyList;
import static org.opendaylight.netvirt.elan.utils.ElanUtils.isVxlanNetworkOrVxlanSegment;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.jobs.AssociateHwvtepToElanJob;
import org.opendaylight.netvirt.elan.l2gw.jobs.DisAssociateHwvtepFromElanJob;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepLogicalSwitchListener;
import org.opendaylight.netvirt.elan.l2gw.listeners.LocalUcastMacListener;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class L2GatewayConnectionUtils implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayConnectionUtils.class);

    private final DataBroker broker;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final JobCoordinator jobCoordinator;
    private final L2GatewayCache l2GatewayCache;
    private final ElanInstanceCache elanInstanceCache;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final HwvtepNodeHACache hwvtepNodeHACache;
    private final HAOpClusteredListener haOpClusteredListener;

    @Inject
    public L2GatewayConnectionUtils(DataBroker dataBroker,
            ElanClusterUtils elanClusterUtils, ElanL2GatewayUtils elanL2GatewayUtils,
            JobCoordinator jobCoordinator, ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
            L2GatewayCache l2GatewayCache, HAOpClusteredListener haOpClusteredListener,
            ElanInstanceCache elanInstanceCache, HwvtepNodeHACache hwvtepNodeHACache) {
        this.broker = dataBroker;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayCache = l2GatewayCache;
        this.haOpClusteredListener = haOpClusteredListener;
        this.elanInstanceCache = elanInstanceCache;
        this.hwvtepNodeHACache = hwvtepNodeHACache;
    }

    @Override
    @PreDestroy
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() {
        closeables.forEach(c -> {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing {}", c, e);
            }
        });
    }

    public static boolean isGatewayAssociatedToL2Device(L2GatewayDevice l2GwDevice) {
        return !l2GwDevice.getL2GatewayIds().isEmpty();
    }

    @Nullable
    public static L2gateway getNeutronL2gateway(DataBroker broker, Uuid l2GatewayId) {
        LOG.debug("getNeutronL2gateway for {}", l2GatewayId.getValue());
        InstanceIdentifier<L2gateway> inst = InstanceIdentifier.create(Neutron.class).child(L2gateways.class)
                .child(L2gateway.class, new L2gatewayKey(l2GatewayId));
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst).orNull();
    }

    @NonNull
    public static List<L2gateway> getL2gatewayList(DataBroker broker) {
        InstanceIdentifier<L2gateways> inst = InstanceIdentifier.create(Neutron.class).child(L2gateways.class);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst).toJavaUtil().map(
                L2gateways::getL2gateway).orElse(emptyList());
    }

    @NonNull
    public static List<L2gatewayConnection> getAllL2gatewayConnections(DataBroker broker) {
        InstanceIdentifier<L2gatewayConnections> inst = InstanceIdentifier.create(Neutron.class)
                .child(L2gatewayConnections.class);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst).toJavaUtil().map(
                L2gatewayConnections::getL2gatewayConnection).orElse(emptyList());
    }

    /**
     * Gets the associated l2 gw connections.
     *
     * @param broker
     *            the broker
     * @param l2GatewayIds
     *            the l2 gateway ids
     * @return the associated l2 gw connections
     */
    @NonNull
    public static List<L2gatewayConnection> getAssociatedL2GwConnections(DataBroker broker, Set<Uuid> l2GatewayIds) {
        List<L2gatewayConnection> allL2GwConns = getAllL2gatewayConnections(broker);
        List<L2gatewayConnection> l2GwConnections = new ArrayList<>();
        for (Uuid l2GatewayId : l2GatewayIds) {
            for (L2gatewayConnection l2GwConn : allL2GwConns) {
                if (Objects.equals(l2GwConn.getL2gatewayId(), l2GatewayId)) {
                    l2GwConnections.add(l2GwConn);
                }
            }
        }
        return l2GwConnections;
    }

    /**
     * Gets the associated l2 gw connections.
     *
     * @param broker
     *            the broker
     * @param elanName
     *            the elan Name
     * @return the associated l2 gw connection with elan
     */
    @NonNull
    public static List<L2gatewayConnection> getL2GwConnectionsByElanName(DataBroker broker, String elanName) {
        List<L2gatewayConnection> allL2GwConns = getAllL2gatewayConnections(broker);
        List<L2gatewayConnection> elanL2GateWayConnections = new ArrayList<>();
        for (L2gatewayConnection l2GwConn : allL2GwConns) {
            if (l2GwConn.getNetworkId().getValue().equalsIgnoreCase(elanName)) {
                elanL2GateWayConnections.add(l2GwConn);
            }
        }
        return elanL2GateWayConnections;
    }

    public void addL2GatewayConnection(L2gatewayConnection input) {
        addL2GatewayConnection(input, null/*deviceName*/, null);
    }

    public void addL2GatewayConnection(final L2gatewayConnection input,
                                       final String l2GwDeviceName) {
        addL2GatewayConnection(input, l2GwDeviceName, null);
    }

    public void addL2GatewayConnection(final L2gatewayConnection input,
                                       @Nullable final String l2GwDeviceName ,
                                       @Nullable L2gateway l2Gateway) {
        LOG.info("Adding L2gateway Connection with ID: {}", input.key().getUuid());

        Uuid networkUuid = input.getNetworkId();

        // Taking cluster reboot scenario , if Elan instance is not available when l2GatewayConnection add events
        // comes we need to wait for elaninstance to resolve. Hence updating the map with the runnable .
        // When elanInstance add comes , it look in to the map and run the associated runnable associated with it.
        ElanInstance elanInstance = elanInstanceCache.get(networkUuid.getValue(),
            () -> addL2GatewayConnection(input, l2GwDeviceName)).orNull();
        if (elanInstance == null) {
            return;
        }

        if (!isVxlanNetworkOrVxlanSegment(elanInstance)) {
            LOG.error("Neutron network with id {} is not VxlanNetwork", networkUuid.getValue());
        } else {
            Uuid l2GatewayId = input.getL2gatewayId();
            if (l2Gateway == null) {
                l2Gateway = getNeutronL2gateway(broker, l2GatewayId);
            }
            if (l2Gateway == null) {
                LOG.error("L2Gateway with id {} is not present", l2GatewayId.getValue());
            } else {
                associateHwvtepsToElan(elanInstance, l2Gateway, input, l2GwDeviceName);
            }
        }
    }

    public void deleteL2GatewayConnection(L2gatewayConnection input) {
        LOG.info("Deleting L2gateway Connection with ID: {}", input.key().getUuid());

        Uuid networkUuid = input.getNetworkId();
        String elanName = networkUuid.getValue();
        disAssociateHwvtepsFromElan(elanName, input);
    }

    private void disAssociateHwvtepsFromElan(String elanName, L2gatewayConnection input) {
        Integer defaultVlan = input.getSegmentId();
        List<L2GatewayDevice> l2Devices = ElanL2GwCacheUtils.getAllElanDevicesFromCache();
        List<Devices> l2gwDevicesToBeDeleted = new ArrayList<>();
        for (L2GatewayDevice elanL2gwDevice : l2Devices) {
            if (elanL2gwDevice.getL2GatewayIds().contains(input.key().getUuid())) {
                l2gwDevicesToBeDeleted.addAll(elanL2gwDevice.getDevicesForL2gwConnectionId(input.key().getUuid()));
            }
        }
        if (l2gwDevicesToBeDeleted.isEmpty()) {
            //delete logical switch
            Uuid l2GatewayId = input.getL2gatewayId();
            L2gateway l2Gateway = L2GatewayConnectionUtils.getNeutronL2gateway(broker, l2GatewayId);
            if (l2Gateway == null) {
                LOG.error("Failed to find the l2gateway for the connection {}", input.getUuid());
                return;
            } else if (l2Gateway.getDevices() != null) {
                l2gwDevicesToBeDeleted.addAll(l2Gateway.getDevices());
            }
        }
        for (Devices l2Device : l2gwDevicesToBeDeleted) {
            String l2DeviceName = l2Device.getDeviceName();
            L2GatewayDevice l2GatewayDevice = l2GatewayCache.get(l2DeviceName);
            String hwvtepNodeId = l2GatewayDevice.getHwvtepNodeId();
            boolean isLastL2GwConnDeleted = false;
            L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
            if (elanL2GwDevice != null && isLastL2GwConnBeingDeleted(elanL2GwDevice)) {
                // Delete L2 Gateway device from 'ElanL2GwDevice' cache
                LOG.debug("Elan L2Gw Conn cache removed for id {}", hwvtepNodeId);
                ElanL2GwCacheUtils.removeL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
                isLastL2GwConnDeleted = true;
            } else {
                Uuid l2GwConnId = input.key().getUuid();
                LOG.debug("Elan L2Gw Conn cache with id {} is being referred by other L2Gw Conns; so only "
                        + "L2 Gw Conn {} reference is removed", hwvtepNodeId, l2GwConnId);
                if (elanL2GwDevice != null) {
                    elanL2GwDevice.removeL2GatewayId(l2GwConnId);
                } else {
                    isLastL2GwConnDeleted = true;
                }
            }

            DisAssociateHwvtepFromElanJob disAssociateHwvtepToElanJob =
                    new DisAssociateHwvtepFromElanJob(elanL2GatewayUtils, elanL2GatewayMulticastUtils,
                            elanL2GwDevice, elanName,
                        l2Device, defaultVlan, hwvtepNodeId, isLastL2GwConnDeleted);
            elanClusterUtils.runOnlyInOwnerNode(disAssociateHwvtepToElanJob.getJobKey(), "remove l2gw connection job",
                    disAssociateHwvtepToElanJob);
        }
    }

    private void associateHwvtepsToElan(ElanInstance elanInstance,
            L2gateway l2Gateway, L2gatewayConnection input, @Nullable String l2GwDeviceName) {
        String elanName = elanInstance.getElanInstanceName();
        Integer defaultVlan = input.getSegmentId();
        Uuid l2GwConnId = input.key().getUuid();
        List<Devices> l2Devices = l2Gateway.getDevices();

        LOG.trace("Associating ELAN {} with L2Gw Conn Id {} having below L2Gw devices {}", elanName, l2GwConnId,
                l2Devices);

        if (l2Devices == null) {
            return;
        }

        for (Devices l2Device : l2Devices) {
            String l2DeviceName = l2Device.getDeviceName();
            // L2gateway can have more than one L2 Gw devices. Configure Logical Switch, VLAN mappings,...
            // only on the switch which has come up just now and exclude all other devices from
            // preprovisioning/re-provisioning
            if (l2GwDeviceName != null && !l2GwDeviceName.equals(l2DeviceName)) {
                LOG.debug("Associating Hwvtep to ELAN is not been processed for {}; as only {} got connected now!",
                        l2DeviceName, l2GwDeviceName);
                continue;
            }
            L2GatewayDevice l2GatewayDevice = l2GatewayCache.get(l2DeviceName);
            if (isL2GwDeviceConnected(l2GatewayDevice)) {
                NodeId hwvtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());

                // Delete pending delete logical switch task if scheduled
                elanL2GatewayUtils.cancelDeleteLogicalSwitch(hwvtepNodeId,
                        ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName));

                // Add L2 Gateway device to 'ElanL2GwDevice' cache
                boolean createLogicalSwitch;
                LogicalSwitches logicalSwitch = HwvtepUtils.getLogicalSwitch(broker, LogicalDatastoreType.CONFIGURATION,
                        hwvtepNodeId, elanName);
                if (logicalSwitch == null) {
                    HwvtepLogicalSwitchListener hwVTEPLogicalSwitchListener = new HwvtepLogicalSwitchListener(
                            elanInstanceCache, elanL2GatewayUtils, elanClusterUtils, elanL2GatewayMulticastUtils,
                            this, l2GatewayDevice, elanName, l2Device, defaultVlan, l2GwConnId, hwvtepNodeHACache);
                    hwVTEPLogicalSwitchListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
                    closeables.add(hwVTEPLogicalSwitchListener);
                    createLogicalSwitch = true;
                } else {
                    addL2DeviceToElanL2GwCache(elanName, l2GatewayDevice, l2GwConnId, l2Device);
                    createLogicalSwitch = false;
                }
                AssociateHwvtepToElanJob associateHwvtepToElanJob = new AssociateHwvtepToElanJob(broker,
                        elanL2GatewayUtils, elanL2GatewayMulticastUtils, elanInstanceCache, l2GatewayDevice,
                        elanInstance, l2Device, defaultVlan, createLogicalSwitch);

                elanClusterUtils.runOnlyInOwnerNode(associateHwvtepToElanJob.getJobKey(),
                        "create logical switch in hwvtep topo", associateHwvtepToElanJob);

            } else {
                LOG.info("L2GwConn create is not handled for device with id {} as it's not connected", l2DeviceName);
            }
        }
    }

    public L2GatewayDevice addL2DeviceToElanL2GwCache(String elanName, L2GatewayDevice l2GatewayDevice, Uuid l2GwConnId,
            Devices l2Device) {
        String l2gwDeviceNodeId = l2GatewayDevice.getHwvtepNodeId();
        L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, l2gwDeviceNodeId);
        if (elanL2GwDevice == null) {
            elanL2GwDevice = new L2GatewayDevice(l2GatewayDevice.getDeviceName());
            elanL2GwDevice.setHwvtepNodeId(l2gwDeviceNodeId);
            elanL2GwDevice.setTunnelIps(l2GatewayDevice.getTunnelIps());
            ElanL2GwCacheUtils.addL2GatewayDeviceToCache(elanName, elanL2GwDevice);
            LOG.debug("Elan L2GwConn cache created for hwvtep id {}", l2gwDeviceNodeId);
        } else {
            LOG.debug("Elan L2GwConn cache already exists for hwvtep id {}; updating L2GwConn id {} to it",
                    l2gwDeviceNodeId, l2GwConnId);
        }
        elanL2GwDevice.addL2GatewayId(l2GwConnId);
        elanL2GwDevice.addL2gwConnectionIdToDevice(l2GwConnId, l2Device);

        //incase of cluster reboot scenario southbound device would have added more macs
        //while odl is down, pull them now
        readAndCopyLocalUcastMacsToCache(elanName, l2GatewayDevice);

        LOG.trace("Elan L2GwConn cache updated with below details: {}", elanL2GwDevice);
        return elanL2GwDevice;
    }

    private static boolean isL2GwDeviceConnected(L2GatewayDevice l2GwDevice) {
        return l2GwDevice != null && l2GwDevice.getHwvtepNodeId() != null;
    }

    protected static boolean isLastL2GwConnBeingDeleted(@NonNull L2GatewayDevice l2GwDevice) {
        return l2GwDevice.getL2GatewayIds().size() == 1;
    }

    private void readAndCopyLocalUcastMacsToCache(final String elanName, final L2GatewayDevice l2GatewayDevice) {
        final InstanceIdentifier<Node> nodeIid = HwvtepSouthboundUtils.createInstanceIdentifier(
                new NodeId(l2GatewayDevice.getHwvtepNodeId()));
        jobCoordinator.enqueueJob(elanName + ":" + l2GatewayDevice.getDeviceName(), () -> {
            final SettableFuture settableFuture = SettableFuture.create();
            Futures.addCallback(broker.newReadOnlyTransaction().read(LogicalDatastoreType.OPERATIONAL,
                    nodeIid), new SettableFutureCallback<Optional<Node>>(settableFuture) {
                        @Override
                        public void onSuccess(@NonNull Optional<Node> resultNode) {
                            LocalUcastMacListener localUcastMacListener =
                                    new LocalUcastMacListener(broker, haOpClusteredListener,
                                            elanL2GatewayUtils, jobCoordinator, elanInstanceCache, hwvtepNodeHACache);
                            settableFuture.set(resultNode);
                            Optional<Node> nodeOptional = resultNode;
                            if (nodeOptional.isPresent()) {
                                Node node = nodeOptional.get();
                                if (node.augmentation(HwvtepGlobalAugmentation.class) != null) {
                                    List<LocalUcastMacs> localUcastMacs =
                                            node.augmentation(HwvtepGlobalAugmentation.class).getLocalUcastMacs();
                                    if (localUcastMacs == null) {
                                        return;
                                    }
                                    localUcastMacs.stream()
                                            .filter((mac) -> macBelongsToLogicalSwitch(mac, elanName))
                                            .forEach((mac) -> {
                                                InstanceIdentifier<LocalUcastMacs> macIid = getMacIid(nodeIid, mac);
                                                localUcastMacListener.added(macIid, mac);
                                            });
                                }
                            }
                        }
                    }, MoreExecutors.directExecutor());
            return Lists.newArrayList(settableFuture);
        } , 5);
    }

    /**
     * Gets the associated l2 gw connections.
     *
     * @param l2GatewayId the l2 gateway id
     *
     * @return the associated l2 gw connections
     */
    public List<L2gatewayConnection> getL2GwConnectionsByL2GatewayId(Uuid l2GatewayId) {
        List<L2gatewayConnection> l2GwConnections = new ArrayList<>();
        List<L2gatewayConnection> allL2GwConns = getAllL2gatewayConnections(broker);
        for (L2gatewayConnection l2GwConn : allL2GwConns) {
            if (Objects.equals(l2GwConn.getL2gatewayId(), l2GatewayId)) {
                l2GwConnections.add(l2GwConn);
            }
        }
        return l2GwConnections;
    }

    private static boolean macBelongsToLogicalSwitch(LocalUcastMacs mac, String logicalSwitchName) {
        InstanceIdentifier<LogicalSwitches> iid = (InstanceIdentifier<LogicalSwitches>)
                mac.getLogicalSwitchRef().getValue();
        return iid.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue().equals(logicalSwitchName);
    }

    static InstanceIdentifier<LocalUcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class).child(LocalUcastMacs.class, mac.key());
    }
}
