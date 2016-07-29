/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/*
* Generated file
*
* Generated from: yang module name: vpnservice yang module local name: vpnservice
* Generated by: org.opendaylight.controller.config.yangjmxgenerator.plugin.JMXGenerator
* Generated at: Fri Feb 13 17:27:11 IST 2015
*
* Do not modify this file unless it is present under src/main directory
*/
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.DynamicMBeanWithInstance;
import org.opendaylight.controller.config.spi.Module;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnserviceImplModuleFactory extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnservice.impl.rev150216.AbstractVpnserviceImplModuleFactory {
    private static final Logger LOG = LoggerFactory.getLogger(VpnserviceImplModuleFactory.class);

    @Override
    public Module createModule(String instanceName,
                               DependencyResolver dependencyResolver,
                               DynamicMBeanWithInstance old, BundleContext bundleContext)
            throws Exception {
        Module module =  super.createModule(instanceName, dependencyResolver, old, bundleContext);
        setModuleBundleContext(bundleContext, module);
        return module;
    }

    @Override
    public Module createModule(String instanceName,
                               DependencyResolver dependencyResolver, BundleContext bundleContext) {
        Module module = super.createModule(instanceName, dependencyResolver, bundleContext);
        setModuleBundleContext(bundleContext, module);
        return module;
    }

    private void setModuleBundleContext(BundleContext bundleContext,
                                        Module module) {
        if (module instanceof VpnserviceImplModule) {
            ((VpnserviceImplModule)module).setBundleContext(bundleContext);
        } else {
            LOG.warn("Module is of type {} expected type {}",
                    module.getClass(), VpnserviceImplModule.class);
        }
    }
}
