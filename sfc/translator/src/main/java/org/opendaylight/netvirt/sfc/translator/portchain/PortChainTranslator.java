/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator.portchain;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfcName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfpName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChain;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChainBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.ServiceFunctionChainKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.service.function.chain.SfcServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.service.function.chain.SfcServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfc.rev140701.service.function.chain.grouping.service.function.chain.SfcServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPathBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.ServiceFunctionPathKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.port.chain.attributes.ChainParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.chains.PortChain;

/**
 * Class will convert OpenStack Port Chain API yang models present in neutron northbound project to OpenDaylight SFC
 * yang models.
 */
public final class PortChainTranslator {

    private static final String SYMMETRIC_PARAM = "symmetric";
    private static final String SFP_NAME_PREFIX = "Path-";

    private PortChainTranslator() {
    }

    public static ServiceFunctionChain buildServiceFunctionChain(
            PortChain portChain, List<ServiceFunction> sfList) {
        ServiceFunctionChainBuilder sfcBuilder = new ServiceFunctionChainBuilder();
        sfcBuilder.setName(new SfcName(portChain.getName()));
        sfcBuilder.withKey(new ServiceFunctionChainKey(sfcBuilder.getName()));

        //By default set it to false. If user specify it in chain parameters, it will be overridden
        sfcBuilder.setSymmetric(false);

        //Set service functions
        List<SfcServiceFunction> sfcSfList = new ArrayList<>();
        for (ServiceFunction sf : sfList) {
            SfcServiceFunctionBuilder sfcSfBuilder = new SfcServiceFunctionBuilder();
            sfcSfBuilder.setName(sf.getName().getValue());
            sfcSfBuilder.setType(sf.getType());
            sfcSfBuilder.withKey(new SfcServiceFunctionKey(sfcSfBuilder.getName()));

            //NOTE: no explicit order is set.
            sfcSfList.add(sfcSfBuilder.build());
        }
        List<ChainParameters> cpList = portChain.getChainParameters();
        if (cpList != null && !cpList.isEmpty()) {
            for (ChainParameters cp : cpList) {
                if (SYMMETRIC_PARAM.equals(cp.getChainParameter())) {
                    //Override the symmetric default value.
                    sfcBuilder.setSymmetric(Boolean.valueOf(cp.getChainParameterValue()));
                    break;
                }
            }
        }
        sfcBuilder.setSfcServiceFunction(sfcSfList);
        return sfcBuilder.build();
    }

    public static ServiceFunctionPath buildServiceFunctionPath(ServiceFunctionChain sfc) {
        Preconditions.checkNotNull(sfc, "Service Function Chain must not be null");
        ServiceFunctionPathBuilder sfpBuilder = new ServiceFunctionPathBuilder();

        //Set the name
        sfpBuilder.setName(new SfpName(SFP_NAME_PREFIX + sfc.getName().getValue()));

        sfpBuilder.setSymmetric(sfc.isSymmetric());
        //Set related SFC name
        sfpBuilder.setServiceChainName(sfc.getName());
        return sfpBuilder.build();
    }

    public static ServiceFunctionChainKey getSFCKey(PortChain portChain) {
        return new ServiceFunctionChainKey(new SfcName(portChain.getName()));
    }

    public static ServiceFunctionPathKey getSFPKey(PortChain portChain) {
        return new ServiceFunctionPathKey(new SfpName(SFP_NAME_PREFIX + portChain.getName()));
    }
}
