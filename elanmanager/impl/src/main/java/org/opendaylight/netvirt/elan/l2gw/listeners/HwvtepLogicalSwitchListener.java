/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import org.opendaylight.genius.datastoreutils.hwvtep.HwvtepClusteredDataTreeChangeListener;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.jobs.LogicalSwitchAddedJob;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The listener class for listening to {@code LogicalSwitches}
 * add/delete/update.
 *
 * @see LogicalSwitches
 */
public class HwvtepLogicalSwitchListener extends
        HwvtepClusteredDataTreeChangeListener<LogicalSwitches, HwvtepLogicalSwitchListener> {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepLogicalSwitchListener.class);

    /** The node id. */
    private final NodeId nodeId;

    /** The logical switch name. */
    private final String logicalSwitchName;

    /** The physical device. */
    private final Devices physicalDevice;

    /** The l2 gateway device. */
    private final L2GatewayDevice l2GatewayDevice;

    // The default vlan id
    private final Integer defaultVlanId;

    // Id of L2 Gateway connection responsible for this logical switch creation
    private final Uuid l2GwConnId;

    private final ElanInstanceCache elanInstanceCache;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final L2GatewayConnectionUtils l2GatewayConnectionUtils;

    /**
     * Instantiates a new hardware vtep logical switch listener.
     */
    public HwvtepLogicalSwitchListener(ElanInstanceCache elanInstanceCache, ElanL2GatewayUtils elanL2GatewayUtils,
            ElanClusterUtils elanClusterUtils, ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
            L2GatewayConnectionUtils l2GatewayConnectionUtils, L2GatewayDevice l2GatewayDevice,
            String logicalSwitchName, Devices physicalDevice, Integer defaultVlanId, Uuid l2GwConnId,
            HwvtepNodeHACache hwvtepNodeHACache) {
        super(LogicalSwitches.class, HwvtepLogicalSwitchListener.class, hwvtepNodeHACache);
        this.elanInstanceCache = elanInstanceCache;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.l2GatewayConnectionUtils = l2GatewayConnectionUtils;
        this.nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        this.logicalSwitchName = logicalSwitchName;
        this.physicalDevice = physicalDevice;
        this.l2GatewayDevice = l2GatewayDevice;
        this.defaultVlanId = defaultVlanId;
        this.l2GwConnId = l2GwConnId;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase#
     * getWildCardPath()
     */
    @Override
    public InstanceIdentifier<LogicalSwitches> getWildCardPath() {
        return HwvtepSouthboundUtils.createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
    }

    @Override
    protected HwvtepLogicalSwitchListener getDataTreeChangeListener() {
        return this;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase#
     * remove(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void removed(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches deletedLogicalSwitch) {
        LOG.trace("Received Remove DataChange Notification for identifier: {}, LogicalSwitches: {}", identifier,
                deletedLogicalSwitch);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase#
     * update(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void updated(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches logicalSwitchOld,
            LogicalSwitches logicalSwitchNew) {
        LOG.trace("Received Update DataChange Notification for identifier: {}, LogicalSwitches old: {}, new: {}."
                + "No Action Performed.", identifier, logicalSwitchOld, logicalSwitchNew);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void added(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches logicalSwitchNew) {
        LOG.debug("Received Add DataChange Notification for identifier: {}, LogicalSwitches: {}", identifier,
                logicalSwitchNew);
        try {
            L2GatewayDevice elanDevice = l2GatewayConnectionUtils.addL2DeviceToElanL2GwCache(
                    logicalSwitchNew.getHwvtepNodeName().getValue(), l2GatewayDevice, l2GwConnId, physicalDevice);

            LogicalSwitchAddedJob logicalSwitchAddedWorker = new LogicalSwitchAddedJob(
                    elanL2GatewayUtils, elanL2GatewayMulticastUtils, logicalSwitchName, physicalDevice, elanDevice,
                    defaultVlanId);
            elanClusterUtils.runOnlyInOwnerNode(logicalSwitchAddedWorker.getJobKey(),
                    "create vlan mappings and mcast configurations", logicalSwitchAddedWorker);
        } catch (RuntimeException e) {
            LOG.error("Failed to handle HwVTEPLogicalSwitch - add for: {}", identifier, e);
        } finally {
            try {
                // This listener is specific to handle a specific logical
                // switch, hence closing it.
                LOG.trace("Closing LogicalSwitches listener for node: {}, logicalSwitch: {}", nodeId.getValue(),
                        logicalSwitchName);
                // TODO use https://git.opendaylight.org/gerrit/#/c/44145/ when merged, and remove @SuppressWarnings
                close();
            } catch (Exception e) {
                LOG.warn("Failed to close HwVTEPLogicalSwitchListener", e);
            }
        }
    }
}
