/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.netvirt.natservice.api.NatSwitchCacheListener;
import org.opendaylight.netvirt.natservice.api.SwitchInfo;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatSwitchCacheImpl implements NatSwitchCache {

    private static final Logger LOG = LoggerFactory.getLogger(NatSwitchCacheImpl.class);
    ConcurrentMap<BigInteger,SwitchInfo> switchMap = new ConcurrentHashMap<>();

    private final  List<NatSwitchCacheListener> centralizedSwitchCacheListenerList =
            new ArrayList<NatSwitchCacheListener>();
    private final DataBroker dataBroker;

    @Inject
    public NatSwitchCacheImpl(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void addSwitch(BigInteger dpnId) {
        LOG.info("addSwitch: Retrieving the provider config for {}", dpnId);
        Map<String, String> providerMappingsMap = NatUtil.getOpenvswitchOtherConfigMap(dpnId, dataBroker);
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.setDpnId(dpnId);
        switchInfo.setProviderNets(providerMappingsMap.keySet());
        switchMap.put(dpnId, switchInfo);
        for (NatSwitchCacheListener centralizedSwitchCacheListener : centralizedSwitchCacheListenerList) {
            centralizedSwitchCacheListener.switchAddedToCache(switchInfo);
        }
    }

    @Override
    public void removeSwitch(BigInteger dpnId) {
        LOG.info("removeSwitch: Removing {} dpnId to switchWeightsMap", dpnId);
        SwitchInfo switchInfo = switchMap.get(dpnId);
        for (NatSwitchCacheListener centralizedSwitchCacheListener : centralizedSwitchCacheListenerList) {
            centralizedSwitchCacheListener.switchRemovedFromCache(switchInfo);
        }
    }

    @Override
    public boolean isSwitchConnectedToExternal(BigInteger dpnId, String providerNet) {
        SwitchInfo switchInfo = switchMap.get(dpnId);
        if (switchInfo != null) {
            return switchInfo.getProviderNets().contains(providerNet);
        }
        return false;
    }

    @Override
    public Set<BigInteger> getSwitchesConnectedToExternal(String providerNet) {
        Set<BigInteger> switches = new HashSet<>();
        for (Map.Entry<BigInteger,SwitchInfo> switchesEntrySet : switchMap.entrySet()) {
            Set<String> providerNetSet = switchesEntrySet.getValue().getProviderNets();
            if (providerNetSet != null && providerNetSet.contains(providerNet)) {
                switches.add(switchesEntrySet.getKey());
            }
        }
        return switches;
    }

    public void register(NatSwitchCacheListener centralizedSwitchCacheListener) {
        if (centralizedSwitchCacheListener != null) {
            centralizedSwitchCacheListenerList.add(centralizedSwitchCacheListener);
        }
    }

}
