/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import com.google.common.base.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class HAEventHandler implements IHAEventHandler {

    private final NodeConnectedHandler nodeConnectedHandler;
    private final ConfigNodeUpdatedHandler configNodeUpdatedHandler = new ConfigNodeUpdatedHandler();
    private final OpNodeUpdatedHandler opNodeUpdatedHandler = new OpNodeUpdatedHandler();

    @Inject
    public HAEventHandler(DataBroker db, HwvtepNodeHACache hwvtepNodeHACache) {
        nodeConnectedHandler = new NodeConnectedHandler(db, hwvtepNodeHACache);
    }

    @Override
    public void handleChildNodeConnected(Node connectedNode,
                                         InstanceIdentifier<Node> connectedNodePath,
                                         InstanceIdentifier<Node> haNodePath,
                                         TypedReadWriteTransaction<Configuration> confTx,
                                         TypedReadWriteTransaction<Operational> operTx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (haNodePath == null) {
            return;
        }
        nodeConnectedHandler.handleNodeConnected(connectedNode, connectedNodePath, haNodePath,
                Optional.absent(), Optional.absent(), confTx, operTx);
    }

    @Override
    public void handleChildNodeReConnected(Node connectedNode,
                                           InstanceIdentifier<Node> connectedNodePath,
                                           InstanceIdentifier<Node> haNodePath,
                                           Optional<Node> haGlobalCfg,
                                           Optional<Node> haPSCfg,
                                           TypedReadWriteTransaction<Configuration> confTx,
                                           TypedReadWriteTransaction<Operational> operTx)
            throws ReadFailedException, ExecutionException, InterruptedException {
        if (haNodePath == null) {
            return;
        }
        nodeConnectedHandler.handleNodeConnected(connectedNode, connectedNodePath, haNodePath,
                haGlobalCfg, haPSCfg, confTx, operTx);
    }

    @Override
    public void copyChildGlobalOpUpdateToHAParent(InstanceIdentifier<Node> haPath,
                                                  DataObjectModification<Node> mod,
                                                  TypedReadWriteTransaction<Operational> tx) {
        if (haPath == null) {
            return;
        }
        opNodeUpdatedHandler.copyChildGlobalOpUpdateToHAParent(haPath, mod, tx);
    }

    @Override
    public void copyChildPsOpUpdateToHAParent(Node updatedSrcPSNode,
                                              InstanceIdentifier<Node> haPath,
                                              DataObjectModification<Node> mod,
                                              TypedReadWriteTransaction<Operational> tx) {
        if (haPath == null) {
            return;
        }
        opNodeUpdatedHandler.copyChildPsOpUpdateToHAParent(updatedSrcPSNode, haPath, mod, tx);
    }

    @Override
    public void copyHAPSUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                      DataObjectModification<Node> mod,
                                      TypedReadWriteTransaction<Configuration> tx) {
        if (haChildNodeId == null) {
            return;
        }
        configNodeUpdatedHandler.copyHAPSUpdateToChild(haChildNodeId, mod, tx);
    }

    @Override
    public void copyHAGlobalUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                          DataObjectModification<Node> mod,
                                          TypedReadWriteTransaction<Configuration> tx) {
        if (haChildNodeId == null) {
            return;
        }
        configNodeUpdatedHandler.copyHAGlobalUpdateToChild(haChildNodeId, mod, tx);
    }

}
