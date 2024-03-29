/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.netvirt.coe.utils.AceNetworkPolicyUtils.buildAcl;
import static org.opendaylight.netvirt.coe.utils.AceNetworkPolicyUtils.getAclNameFromPolicy;
import static org.opendaylight.netvirt.coe.utils.AclUtils.getAclIid;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.RetryingManagedNewTransactionRunner;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NetworkPolicyListener extends AbstractSyncDataTreeChangeListener<NetworkPolicy> {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkPolicyListener.class);
    private final RetryingManagedNewTransactionRunner txRunner;

    @Inject
    public NetworkPolicyListener(@Reference DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class).child(NetworkPolicy.class));
        this.txRunner = new RetryingManagedNewTransactionRunner(dataBroker);
    }

    @Override
    public void add(@NonNull InstanceIdentifier<NetworkPolicy> instanceIdentifier, @NonNull NetworkPolicy policy) {
        LOG.info("add: id: {}\npolicy: {}", instanceIdentifier, policy);
        updateAcl(policy, false);
    }

    @Override
    public void remove(@NonNull InstanceIdentifier<NetworkPolicy> instanceIdentifier, @NonNull NetworkPolicy policy) {
        LOG.info("remove: id: {}\npolicy: {}", instanceIdentifier, policy);
        updateAcl(policy, true);
    }

    @Override
    public void update(@NonNull InstanceIdentifier<NetworkPolicy> instanceIdentifier,
                       @NonNull NetworkPolicy oldPolicy, @NonNull NetworkPolicy policy) {
        LOG.info("update: id: {}\nold policy: {}\nnew policy: {}", instanceIdentifier, oldPolicy, policy);
        updateAcl(policy, false);
    }

    private void updateAcl(@NonNull NetworkPolicy policy, boolean isDeleted) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            String aclName = getAclNameFromPolicy(policy);
            Acl acl = buildAcl(policy, isDeleted);
            InstanceIdentifier<Acl> aclIid = getAclIid(aclName);
            // write the ace, and the api will create the acl parent if needed
            tx.put(aclIid, acl, true);
        }), LOG, "Failed to add acl from policy: {}", policy);
    }
}
