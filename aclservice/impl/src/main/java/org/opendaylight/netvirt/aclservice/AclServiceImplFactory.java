/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclServiceImplFactory extends AbstractLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceImplFactory.class);
    //private static final String SECURITY_GROUP_MODE = "security-group-mode";

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final AclDataUtil aclDataUtil;
    private final AclServiceUtils aclServiceUtils;
    private final JobCoordinator jobCoordinator;
    private final AclInterfaceCache aclInterfaceCache;

    @Inject
    public AclServiceImplFactory(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclserviceConfig config,
            AclDataUtil aclDataUtil, AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator,
            AclInterfaceCache aclInterfaceCache) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServiceUtils;
        this.jobCoordinator = jobCoordinator;
        this.aclInterfaceCache = aclInterfaceCache;

        LOG.info("AclserviceConfig: {}", config);
    }

    @Override
    protected void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    protected void stop() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public IngressAclServiceImpl createIngressAclServiceImpl() {
        LOG.info("creating ingress acl service");
        return new IngressAclServiceImpl(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator,
                aclInterfaceCache);
    }

    public EgressAclServiceImpl createEgressAclServiceImpl() {
        LOG.info("creating egress acl service");
        return new EgressAclServiceImpl(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator,
                aclInterfaceCache);
    }

}
