/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DhcpConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dhcp.config.Configs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpConfigListener extends AsyncClusteredDataTreeChangeListenerBase<DhcpConfig, DhcpConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpConfigListener.class);

    private final DhcpManager dhcpManager;
    private final DataBroker dataBroker;

    @Inject
    public DhcpConfigListener(final DataBroker db, final DhcpManager dhcpMgr) {
        super(DhcpConfig.class, DhcpConfigListener.class);
        dhcpManager = dhcpMgr;
        this.dataBroker = db;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<DhcpConfig> getWildCardPath() {
        return InstanceIdentifier.create(DhcpConfig.class);
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("DhcpConfig Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig del) {
        LOG.trace("DhcpConfig removed: {}", del);
        updateConfig(null);
    }

    @Override
    protected void update(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig original, DhcpConfig update) {
        LOG.trace("DhcpConfig changed to {}", update);
        updateConfig(update);
    }

    @Override
    protected void add(InstanceIdentifier<DhcpConfig> identifier, DhcpConfig add) {
        LOG.trace("DhcpConfig added {}", add);
        updateConfig(add);
    }

    private void updateConfig(@Nullable DhcpConfig update) {
        //TODO: Update operational with actual values
        if (update == null || update.getConfigs() == null || update.getConfigs().isEmpty()) {
            dhcpManager.setLeaseDuration(DhcpMConstants.DEFAULT_LEASE_TIME);
            dhcpManager.setDefaultDomain(DhcpMConstants.DEFAULT_DOMAIN_NAME);
            return;
        }
        Configs config = update.getConfigs().get(0);
        if (config.getLeaseDuration() != null) {
            dhcpManager.setLeaseDuration(config.getLeaseDuration());
        }
        if (config.getDefaultDomain() != null) {
            dhcpManager.setDefaultDomain(config.getDefaultDomain());
            //TODO: What to do if string is ""
        }
    }

    @Override
    protected DhcpConfigListener getDataTreeChangeListener() {
        return DhcpConfigListener.this;
    }
}
