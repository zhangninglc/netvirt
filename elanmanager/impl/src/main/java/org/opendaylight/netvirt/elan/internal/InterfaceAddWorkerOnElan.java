/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceAddWorkerOnElan implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceAddWorkerOnElan.class);

    private final String key;
    private final ElanInterface elanInterface;
    private final ElanInstance elanInstance;
    private final InterfaceInfo interfaceInfo;
    private final ElanInterfaceManager dataChangeListener;

    public InterfaceAddWorkerOnElan(String key, ElanInterface elanInterface, InterfaceInfo interfaceInfo,
                                    ElanInstance elanInstance, ElanInterfaceManager dataChangeListener) {
        this.key = key;
        this.elanInterface = elanInterface;
        this.interfaceInfo = interfaceInfo;
        this.elanInstance = elanInstance;
        this.dataChangeListener = dataChangeListener;
    }

    @Override
    public String toString() {
        return "InterfaceAddWorkerOnElan [key=" + key + ", elanInterface=" + elanInterface + ", elanInstance="
            + elanInstance + ", interfaceInfo=" + interfaceInfo + "]";
    }


    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<ListenableFuture<Void>> call() {
        try {
            return dataChangeListener.addElanInterface(elanInterface, interfaceInfo, elanInstance);
        } catch (RuntimeException e) {
            return ElanUtils.returnFailedListenableFutureIfTransactionCommitFailedExceptionCauseOrElseThrow(e);
        }
    }
}
