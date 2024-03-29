/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclInterfaceStateListener extends AsyncDataTreeChangeListenerBase<Interface,
        AclInterfaceStateListener> implements ClusteredDataTreeChangeListener<Interface>, RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceStateListener.class);

    /** Our registration. */
    private final AclServiceManager aclServiceManger;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;
    private final AclDataUtil aclDataUtil;
    private final IInterfaceManager interfaceManager;
    private final AclInterfaceCache aclInterfaceCache;
    private final AclServiceUtils aclServiceUtils;
    protected final JobCoordinator jobCoordinator;

    @Inject
    public AclInterfaceStateListener(AclServiceManager aclServiceManger, AclClusterUtil aclClusterUtil,
            DataBroker dataBroker, AclDataUtil aclDataUtil, IInterfaceManager interfaceManager,
            AclInterfaceCache aclInterfaceCache, AclServiceUtils aclServicUtils, JobCoordinator jobCoordinator,
            ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(Interface.class, AclInterfaceStateListener.class);
        this.aclServiceManger = aclServiceManger;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.interfaceManager = interfaceManager;
        this.aclInterfaceCache = aclInterfaceCache;
        this.aclServiceUtils = aclServicUtils;
        this.jobCoordinator = jobCoordinator;
        serviceRecoveryRegistry.addRecoverableListener(AclServiceUtils.getRecoverServiceRegistryKey(), this);
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener();
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface deleted) {
        if (!L2vlan.class.equals(deleted.getType())) {
            return;
        }
        String interfaceId = deleted.getName();
        AclInterface aclInterface = aclInterfaceCache.remove(interfaceId);
        if (AclServiceUtils.isOfInterest(aclInterface)) {
            List<Uuid> aclList = aclInterface.getSecurityGroups();
            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On remove event, notify ACL service manager to remove ACL from interface: {}", aclInterface);
                aclServiceManger.notify(aclInterface, null, Action.UNBIND);
                aclServiceManger.notify(aclInterface, null, Action.REMOVE);

                if (aclList != null) {
                    aclServiceUtils.deleteAclPortsLookup(aclInterface, aclList, aclInterface.getAllowedAddressPairs());
                }
            }
            if (aclList != null) {
                for (Uuid acl : aclList) {
                    jobCoordinator.enqueueJob(acl.getValue(), () -> {
                        aclDataUtil.removeAclInterfaceMap(acl, aclInterface);
                        return Collections.emptyList();
                    });
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface before, Interface after) {
        /*
         * The update is not of interest as the attributes populated from this listener will not change.
         * The northbound updates are handled in AclInterfaceListener.
         *
         * We're only interested in update in cases where IfType got filled after creation.
         */
        if (before.getType() == null && L2vlan.class.equals(after.getType())) {
            add(key, after);
        } else {
            LOG.trace("Update event for AclInterfaceStateListener is not of interest.");
        }
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface added) {
        if (!L2vlan.class.equals(added.getType())) {
            return;
        }

        if (aclInterfaceCache.get(added.getName()) == null) {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .Interface iface = interfaceManager.getInterfaceInfoFromConfigDataStore(added.getName());
            if (iface == null) {
                LOG.error("No interface with name {} available in interfaceConfig, servicing interfaceState ADD"
                        + "for ACL failed", added.getName());
                return;
            }
            InterfaceAcl aclInPort = iface.augmentation(InterfaceAcl.class);
            if (aclInPort == null) {
                LOG.trace("Interface {} is not an ACL Interface, ignoring ADD interfaceState event",
                        added.getName());
                return;
            }
            aclInterfaceCache.addOrUpdate(added.getName(), (prevAclInterface, builder) -> {
                builder.portSecurityEnabled(aclInPort.isPortSecurityEnabled())
                    .securityGroups(aclInPort.getSecurityGroups())
                    .allowedAddressPairs(aclInPort.getAllowedAddressPairs()).subnetInfo(aclInPort.getSubnetInfo());
            });
        }
        AclInterface aclInterface = aclInterfaceCache.addOrUpdate(added.getName(), (prevAclInterface, builder) -> {
            builder.dpId(AclServiceUtils.getDpIdFromIterfaceState(added)).lPortTag(added.getIfIndex())
                .isMarkedForDelete(false);
            if (AclServiceUtils.isOfInterest(prevAclInterface)) {
                SortedSet<Integer> ingressRemoteAclTags =
                        aclServiceUtils.getRemoteAclTags(prevAclInterface.getSecurityGroups(), DirectionIngress.class);
                SortedSet<Integer> egressRemoteAclTags =
                        aclServiceUtils.getRemoteAclTags(prevAclInterface.getSecurityGroups(), DirectionEgress.class);
                builder.ingressRemoteAclTags(ingressRemoteAclTags).egressRemoteAclTags(egressRemoteAclTags);
            }
        });

        if (AclServiceUtils.isOfInterest(aclInterface)) {
            List<Uuid> aclList = aclInterface.getSecurityGroups();
            if (aclList != null) {
                aclDataUtil.addOrUpdateAclInterfaceMap(aclList, aclInterface);
            }
            if (aclInterface.getElanId() == null) {
                LOG.debug("On Add event, skip ADD since ElanId is not updated");
                return;
            }
            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On add event, notify ACL service manager to add ACL for interface: {}", aclInterface);
                aclServiceManger.notify(aclInterface, null, Action.BIND);
                if (aclList != null) {
                    aclServiceUtils.addAclPortsLookup(aclInterface, aclList, aclInterface.getAllowedAddressPairs());
                }
                aclServiceManger.notify(aclInterface, null, Action.ADD);
            }
        }
    }

    @Override
    protected AclInterfaceStateListener getDataTreeChangeListener() {
        return AclInterfaceStateListener.this;
    }
}
