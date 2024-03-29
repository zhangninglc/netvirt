/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Verifications {
    private static final Logger LOG = LoggerFactory.getLogger(Verifications.class);
    private static final int AWAIT_TIMEOUT = 10;
    private static final int AWAIT_INTERVAL = 1000;
    private final ConditionFactory awaiter;
    private final ManagedNewTransactionRunner txRunner;

    Verifications(final DataBroker dataBroker) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.awaiter = getAwaiter();
    }

    private ConditionFactory getAwaiter() {
        return Awaitility.await("TestableListener")
            .atMost(AWAIT_TIMEOUT, TimeUnit.SECONDS)//TODO constant
            .pollInterval(AWAIT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    <D extends Datastore> void awaitForData(Class<D> datastoreType, InstanceIdentifier<? extends DataObject> iid) {
        awaiter.with()
            .conditionEvaluationListener(condition -> LOG.info("{} ({} ms of {} s)",
                condition.getDescription(), condition.getElapsedTimeInMS(), AWAIT_TIMEOUT))
            .until(() ->
                txRunner.applyWithNewReadOnlyTransactionAndClose(datastoreType, tx -> tx.read(iid)).get().isPresent());
    }

    <D extends Datastore> void awaitForDataDelete(Class<D> datastoreType,
                                                  InstanceIdentifier<? extends DataObject> iid) {
        awaiter.with()
            .conditionEvaluationListener(condition -> LOG.info("{} ({} ms of {} s)",
                condition.getDescription(), condition.getElapsedTimeInMS(), AWAIT_TIMEOUT))
            .until(() ->
                !txRunner.applyWithNewReadOnlyTransactionAndClose(datastoreType, tx -> tx.read(iid)).get().isPresent());
    }
}
